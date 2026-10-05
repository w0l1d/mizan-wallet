package com.ivy.domain.usecase.backup

import com.ivy.base.TestDispatchersProvider
import com.ivy.base.model.TransactionType
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.backup.local.SnapshotArchive
import com.ivy.data.backup.local.SnapshotManifest
import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.backup.local.fake.FakeBackupStorage
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakeBudgetDao
import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

class CaptureSnapshotUseCaseTest {

    private val walletJson = """{"accounts":[],"transactions":[]}"""

    private val storage = FakeBackupStorage(describe = ::describeByManifest)
    private val transactionDao = FakeTransactionDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val time = FixedTimeProvider(Instant.parse("2026-04-01T08:30:00Z"))

    private val backupData = mockk<BackupDataUseCase>().also {
        coEvery { it.generateJsonBackup() } returns walletJson
    }

    private val computeSummary = ComputeWalletSummaryUseCase(
        transactionDao = transactionDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        budgetDao = FakeBudgetDao(),
        dispatchers = TestDispatchersProvider,
    )

    private val useCase = CaptureSnapshotUseCase(
        storage = storage,
        computeSummary = computeSummary,
        backupData = backupData,
        applyRetention = ApplyRetentionUseCase(storage),
        environment = TestBackupEnvironment(),
        timeProvider = time,
        dispatchers = TestDispatchersProvider,
    )

    @Test
    fun `the snapshot describes the wallet it was taken from`() = runBlocking<Unit> {
        val account = AccountEntity(name = "Cash", color = 0, id = UUID.randomUUID())
        accountDao.save(account)
        categoryDao.save(CategoryEntity(name = "Food", color = 0, id = UUID.randomUUID()))
        transactionDao.save(
            TransactionEntity(
                accountId = account.id,
                type = TransactionType.EXPENSE,
                amount = 1.0,
                dateTime = Instant.parse("2026-03-20T10:00:00Z"),
                id = UUID.randomUUID(),
            ),
        )

        val ref = useCase(SnapshotOrigin.Manual).shouldBeRight()

        ref.origin shouldBe SnapshotOrigin.Manual
        ref.capturedAt shouldBe time.now
        ref.summary.transactionCount shouldBe 1
        ref.summary.accountCount shouldBe 1
        ref.summary.categoryCount shouldBe 1
        ref.summary.newestTransactionAt shouldBe Instant.parse("2026-03-20T10:00:00Z")
    }

    @Test
    fun `the archive carries the wallet export the existing backup produces`() = runBlocking<Unit> {
        val ref = useCase(SnapshotOrigin.Scheduled).shouldBeRight()

        val manifest = storage.read(ref) { input ->
            SnapshotArchive.readManifest(ref.name, input)
        }.shouldBeRight().shouldBeRight()
        val data = storage.read(ref) { input ->
            SnapshotArchive.readDataEntry(ref.name, input, manifest)
        }.shouldBeRight().shouldBeRight()

        String(data, Charsets.UTF_16) shouldBe walletJson
        manifest.dataSha256 shouldBe SnapshotArchive.sha256(walletJson.toByteArray(Charsets.UTF_16))
        manifest.appVersion shouldBe "1.2.3"
        manifest.dataSchemaVersion shouldBe 7
    }

    @Test
    fun `the snapshot is named for when it was taken, by this device, and why`() =
        runBlocking<Unit> {
            val ref = useCase(SnapshotOrigin.Scheduled).shouldBeRight()

            val parsed = SnapshotNaming.parse(ref.name)!!
            parsed.device shouldBe "pixel"
            parsed.origin shouldBe SnapshotOrigin.Scheduled
            parsed.capturedAt shouldBe time.now
        }

    @Test
    fun `two captures in the same second do not collide`() = runBlocking<Unit> {
        val first = useCase(SnapshotOrigin.Manual).shouldBeRight()
        val second = useCase(SnapshotOrigin.Manual).shouldBeRight()

        (first.name == second.name) shouldBe false
        storage.names.size shouldBe 2
    }

    @Test
    fun `retention runs only after the new snapshot is confirmed written`() = runBlocking<Unit> {
        val old = (0 until ApplyRetentionUseCase.KEEP_COUNT + 2).map { index ->
            SnapshotNaming.build(
                capturedAt = time.now.minusSeconds((index + 1) * 86_400L),
                device = "pixel",
                origin = SnapshotOrigin.Scheduled,
            )
        }
        old.forEach { storage.put(it, archiveBytes()) }

        useCase(SnapshotOrigin.Scheduled).shouldBeRight()

        // One new snapshot in, the three oldest out, never the other way round.
        storage.names.size shouldBe ApplyRetentionUseCase.KEEP_COUNT
        old.takeLast(3).forEach { storage.names.contains(it) shouldBe false }
    }

    @Test
    fun `a failed write leaves no object behind and deletes nothing`() = runBlocking<Unit> {
        val old = (0 until ApplyRetentionUseCase.KEEP_COUNT + 2).map { index ->
            SnapshotNaming.build(
                capturedAt = time.now.minusSeconds((index + 1) * 86_400L),
                device = "pixel",
                origin = SnapshotOrigin.Scheduled,
            )
        }
        old.forEach { storage.put(it, archiveBytes()) }
        storage.failNext = BackupError.OutOfSpace

        useCase(SnapshotOrigin.Scheduled).shouldBeLeft() shouldBe BackupError.OutOfSpace

        storage.names.sorted() shouldBe old.sorted()
    }

    @Test
    fun `a destination that was never picked is its own answer, not a write failure`() =
        runBlocking<Unit> {
            storage.failNext = BackupError.NotConfigured

            useCase(SnapshotOrigin.Scheduled).shouldBeLeft() shouldBe BackupError.NotConfigured
        }

    private fun archiveBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        SnapshotArchive.write(
            out = out,
            manifest = SnapshotManifest(
                capturedAt = time.now,
                origin = SnapshotOrigin.Scheduled,
                appVersion = "1.2.3",
                dataSchemaVersion = 7,
                dataEntry = "wallet.json",
                dataSha256 = "",
                summary = SnapshotSummary.Empty,
            ),
            data = walletJson.toByteArray(Charsets.UTF_16),
        )
        return out.toByteArray()
    }
}
