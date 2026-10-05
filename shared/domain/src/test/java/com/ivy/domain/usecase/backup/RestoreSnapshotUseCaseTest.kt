package com.ivy.domain.usecase.backup

import arrow.core.Either
import com.ivy.base.TestDispatchersProvider
import com.ivy.base.model.TransactionType
import com.ivy.data.backup.IvyWalletCompleteData
import com.ivy.data.backup.local.SnapshotArchive
import com.ivy.data.backup.local.SnapshotManifest
import com.ivy.data.backup.local.SnapshotManifestCodec
import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.backup.local.WalletDataWriter
import com.ivy.data.backup.local.fake.FakeBackupStorage
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakeBudgetDao
import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.dao.fake.FakeLoanDao
import com.ivy.data.db.dao.fake.FakeLoanRecordDao
import com.ivy.data.db.dao.fake.FakePlannedPaymentDao
import com.ivy.data.db.dao.fake.FakeSettingsDao
import com.ivy.data.db.dao.fake.FakeTagAssociationDao
import com.ivy.data.db.dao.fake.FakeTagDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.db.transaction.fake.FakeDatabaseTransaction
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RestoreSnapshotUseCaseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storage = FakeBackupStorage(describe = ::describeByManifest)
    private val transactionDao = FakeTransactionDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val dbTransaction = FakeDatabaseTransaction()
    private val time = FixedTimeProvider(Instant.parse("2026-06-01T12:00:00Z"))

    private val writer = WalletDataWriter(
        accountDao = accountDao,
        budgetDao = FakeBudgetDao(),
        categoryDao = categoryDao,
        loanDao = FakeLoanDao(),
        loanRecordDao = FakeLoanRecordDao(),
        plannedPaymentRuleDao = FakePlannedPaymentDao(),
        settingsDao = FakeSettingsDao(),
        transactionDao = transactionDao,
        tagDao = FakeTagDao(),
        tagAssociationDao = FakeTagAssociationDao(),
    )

    private val captureSnapshot = mockk<CaptureSnapshotUseCase>()

    private val useCase = restoreWith(writer)

    private val account = AccountEntity(name = "Cash", color = 1, id = UUID.randomUUID())

    @Test
    fun `the snapshot wins for every record it carries`() = runBlocking<Unit> {
        safetyCaptureSucceeds()
        // Both records were edited after the snapshot was taken.
        accountDao.save(account.copy(name = "Renamed"))
        val edited = transaction(at = Instant.parse("2026-05-01T10:00:00Z"), amount = 999.0)
        transactionDao.save(edited)

        val snapshot = put(
            data(accounts = listOf(account), transactions = listOf(edited.copy(amount = 10.0))),
        )

        useCase(snapshot).shouldBeRight()

        accountDao.findAll().single().name shouldBe "Cash"
        transactionDao.findAll().single().amount shouldBe 10.0
    }

    @Test
    fun `records added after the snapshot are still there afterwards`() = runBlocking<Unit> {
        safetyCaptureSucceeds()
        accountDao.save(account)
        val inSnapshot = transaction(at = Instant.parse("2026-01-01T10:00:00Z"), amount = 1.0)
        val addedSince = transaction(at = Instant.parse("2026-05-30T10:00:00Z"), amount = 2.0)
        transactionDao.save(inSnapshot)
        transactionDao.save(addedSince)
        categoryDao.save(CategoryEntity(name = "Added later", color = 0, id = UUID.randomUUID()))

        val snapshot = put(data(accounts = listOf(account), transactions = listOf(inSnapshot)))

        useCase(snapshot).shouldBeRight()

        // A merge by record identity, not a wipe: what the snapshot never knew about survives.
        transactionDao.findAll().map { it.id }.toSet() shouldBe setOf(inSnapshot.id, addedSince.id)
        categoryDao.findAll().single().name shouldBe "Added later"
    }

    @Test
    fun `the safety snapshot is what the user gets back if they change their mind`() =
        runBlocking<Unit> {
            val safety = safetyCaptureSucceeds()
            val snapshot = put(data(accounts = listOf(account)))

            useCase(snapshot).shouldBeRight() shouldBe safety
        }

    @Test
    fun `a snapshot whose contents do not match its digest is refused`() = runBlocking<Unit> {
        safetyCaptureSucceeds()
        val snapshot = putWithWrongDigest(data(accounts = listOf(account)))

        val error = useCase(snapshot).shouldBeLeft().shouldBeUnreadable()

        error.reason shouldContain "digest"
        // Refused before the wallet was touched and before a safety snapshot was taken: a
        // safety snapshot taken for a restore that never happens is clutter the user must explain.
        dbTransaction.started shouldBe 0
        storage.names.none { it.endsWith("--safety.zip") } shouldBe true
        accountDao.findAll() shouldBe emptyList()
    }

    @Test
    fun `a snapshot from a newer version of the app is refused with the reason stated`() =
        runBlocking<Unit> {
            safetyCaptureSucceeds()
            val snapshot = put(data(accounts = listOf(account)), schemaVersion = 99)

            val error = useCase(snapshot).shouldBeLeft().shouldBeUnreadable()

            error.reason shouldContain "99"
            dbTransaction.started shouldBe 0
            storage.names.none { it.endsWith("--safety.zip") } shouldBe true
            accountDao.findAll() shouldBe emptyList()
        }

    @Test
    fun `a restore that cannot take a safety snapshot does not happen at all`() = runBlocking<Unit> {
        coEvery { captureSnapshot(SnapshotOrigin.Safety) } returns Either.Left(BackupError.OutOfSpace)
        val snapshot = put(data(accounts = listOf(account)))

        useCase(snapshot).shouldBeLeft() shouldBe BackupError.OutOfSpace

        dbTransaction.started shouldBe 0
        accountDao.findAll() shouldBe emptyList()
    }

    @Test
    fun `a write that fails partway leaves the wallet unchanged`() = runBlocking<Unit> {
        safetyCaptureSucceeds()
        val brokenWriter = mockk<WalletDataWriter>()
        coEvery { brokenWriter.write(any()) } throws IllegalStateException("disk gave up")
        val snapshot = put(data(accounts = listOf(account)))

        val error = restoreWith(brokenWriter).invoke(snapshot).shouldBeLeft()

        (error is BackupError.RestoreFailed) shouldBe true
        // RestoreFailed carries a guarantee in its name, so the rollback is part of the claim.
        dbTransaction.rolledBack shouldBe 1
        accountDao.findAll() shouldBe emptyList()
    }

    private fun restoreWith(walletWriter: WalletDataWriter) = RestoreSnapshotUseCase(
        storage = storage,
        capture = captureSnapshot,
        transaction = dbTransaction,
        writer = walletWriter,
        environment = TestBackupEnvironment(dataSchemaVersion = 7),
        json = json,
        dispatchers = TestDispatchersProvider,
    )

    private fun BackupError.shouldBeUnreadable(): BackupError.SnapshotUnreadable =
        this as? BackupError.SnapshotUnreadable ?: error("expected SnapshotUnreadable but got $this")

    private fun safetyCaptureSucceeds(): SnapshotRef {
        val name = SnapshotNaming.build(time.now, device = "pixel", origin = SnapshotOrigin.Safety)
        val ref = SnapshotRef(
            name = name,
            uri = "fake://$name",
            capturedAt = time.now,
            origin = SnapshotOrigin.Safety,
            summary = SnapshotSummary.Empty,
            sizeBytes = 1L,
        )
        coEvery { captureSnapshot(SnapshotOrigin.Safety) } returns Either.Right(ref)
        return ref
    }

    private fun data(
        accounts: List<AccountEntity> = emptyList(),
        transactions: List<TransactionEntity> = emptyList(),
    ) = IvyWalletCompleteData(accounts = accounts, transactions = transactions)

    private fun put(data: IvyWalletCompleteData, schemaVersion: Int = 7): SnapshotRef {
        val out = ByteArrayOutputStream()
        SnapshotArchive.write(out, manifest(schemaVersion), encode(data))
        return store(out.toByteArray())
    }

    /** An archive whose recorded digest is not the digest of the bytes it carries. */
    private fun putWithWrongDigest(data: IvyWalletCompleteData): SnapshotRef {
        val manifest = manifest(schemaVersion = 7).copy(dataSha256 = "00".repeat(32))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SnapshotManifest.ENTRY_NAME))
            zip.write(SnapshotManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(manifest.dataEntry))
            zip.write(encode(data))
            zip.closeEntry()
        }
        return store(out.toByteArray())
    }

    private fun store(archive: ByteArray): SnapshotRef {
        val name = SnapshotNaming.build(
            capturedAt = time.now.minusSeconds(3600),
            device = "pixel",
            origin = SnapshotOrigin.Manual,
        )
        storage.put(name, archive)
        return runBlocking { storage.list() }.shouldBeRight().snapshots.first { it.name == name }
    }

    private fun manifest(schemaVersion: Int) = SnapshotManifest(
        capturedAt = time.now.minusSeconds(3600),
        origin = SnapshotOrigin.Manual,
        appVersion = "1.2.3",
        dataSchemaVersion = schemaVersion,
        dataEntry = "wallet.json",
        dataSha256 = "",
        summary = SnapshotSummary.Empty,
    )

    // UTF-16 is what the existing export writes; a snapshot stays a file the manual import accepts.
    private fun encode(data: IvyWalletCompleteData): ByteArray =
        json.encodeToString(IvyWalletCompleteData.serializer(), data).toByteArray(Charsets.UTF_16)

    private fun transaction(at: Instant, amount: Double) = TransactionEntity(
        accountId = account.id,
        type = TransactionType.EXPENSE,
        amount = amount,
        dateTime = at,
        id = UUID.randomUUID(),
    )
}
