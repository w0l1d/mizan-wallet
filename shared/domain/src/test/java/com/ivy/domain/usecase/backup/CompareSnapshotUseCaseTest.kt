package com.ivy.domain.usecase.backup

import com.ivy.base.TestDispatchersProvider
import com.ivy.base.model.TransactionType
import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakeBudgetDao
import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CompareSnapshotUseCaseTest {

    private val transactionDao = FakeTransactionDao()
    private val accountDao = FakeAccountDao()

    private val computeSummary = ComputeWalletSummaryUseCase(
        transactionDao = transactionDao,
        accountDao = accountDao,
        categoryDao = FakeCategoryDao(),
        budgetDao = FakeBudgetDao(),
        dispatchers = TestDispatchersProvider,
    )

    private val useCase = CompareSnapshotUseCase(computeSummary)

    @Test
    fun `the comparison says what restoring would change`() = runBlocking<Unit> {
        val account = AccountEntity(name = "Cash", color = 0, id = UUID.randomUUID())
        accountDao.save(account)
        transactionDao.save(
            TransactionEntity(
                accountId = account.id,
                type = TransactionType.EXPENSE,
                amount = 1.0,
                dateTime = Instant.parse("2026-02-01T10:00:00Z"),
                id = UUID.randomUUID(),
            ),
        )

        val comparison = useCase(
            candidate = ref(
                SnapshotSummary(
                    transactionCount = 10,
                    accountCount = 1,
                    categoryCount = 4,
                    budgetCount = 0,
                    newestTransactionAt = Instant.parse("2026-01-01T10:00:00Z"),
                ),
            ),
        )

        comparison.current.transactionCount shouldBe 1
        comparison.candidate.transactionCount shouldBe 10
        comparison.transactionDelta shouldBe 9
        comparison.accountDelta shouldBe 0
        comparison.categoryDelta shouldBe 4
    }

    @Test
    fun `comparing needs nothing but the two summaries, so a missing archive still compares`() =
        runBlocking<Unit> {
            // The snapshot this ref names is not in any folder. If comparison opened the data
            // document it could not answer at all; it answers, because it never opens one.
            val comparison = useCase(candidate = ref(SnapshotSummary.Empty))

            comparison.candidate shouldBe SnapshotSummary.Empty
            comparison.current shouldBe SnapshotSummary.Empty
            comparison.transactionDelta shouldBe 0
        }

    private fun ref(summary: SnapshotSummary): SnapshotRef {
        val at = Instant.parse("2026-01-01T10:00:00Z")
        val name = SnapshotNaming.build(at, device = "pixel", origin = SnapshotOrigin.Manual)
        return SnapshotRef(
            name = name,
            uri = "nowhere://$name",
            capturedAt = at,
            origin = SnapshotOrigin.Manual,
            summary = summary,
            sizeBytes = 1L,
        )
    }
}
