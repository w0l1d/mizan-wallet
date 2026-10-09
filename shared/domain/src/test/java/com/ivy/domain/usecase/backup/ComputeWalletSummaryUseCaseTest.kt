package com.ivy.domain.usecase.backup

import com.ivy.base.TestDispatchersProvider
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakeBudgetDao
import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.BudgetEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.db.entity.TransactionEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ComputeWalletSummaryUseCaseTest {

    private val transactionDao = FakeTransactionDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val budgetDao = FakeBudgetDao()

    private val useCase = ComputeWalletSummaryUseCase(
        transactionDao = transactionDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        budgetDao = budgetDao,
        dispatchers = TestDispatchersProvider,
    )

    @Test
    fun `an empty wallet is a legitimate thing to back up`() = runBlocking<Unit> {
        val summary = useCase()

        summary.transactionCount shouldBe 0
        summary.accountCount shouldBe 0
        summary.categoryCount shouldBe 0
        summary.budgetCount shouldBe 0
        // Null, not epoch zero: "no transactions" and "a transaction dated 1970" are different facts.
        summary.newestTransactionAt shouldBe null
    }

    @Test
    fun `counts describe what the wallet actually holds`() = runBlocking<Unit> {
        val account = account()
        transactionDao.save(transaction(account, at = Instant.parse("2026-01-01T10:00:00Z")))
        transactionDao.save(transaction(account, at = Instant.parse("2026-02-01T10:00:00Z")))
        accountDao.save(account)
        categoryDao.save(category())
        categoryDao.save(category())
        categoryDao.save(category())
        budgetDao.save(budget())

        val summary = useCase()

        summary.transactionCount shouldBe 2
        summary.accountCount shouldBe 1
        summary.categoryCount shouldBe 3
        summary.budgetCount shouldBe 1
    }

    @Test
    fun `the newest transaction time is the newest one, not the last one written`() =
        runBlocking<Unit> {
            val account = account()
            transactionDao.save(transaction(account, at = Instant.parse("2026-03-01T10:00:00Z")))
            transactionDao.save(transaction(account, at = Instant.parse("2026-01-01T10:00:00Z")))

            useCase().newestTransactionAt shouldBe Instant.parse("2026-03-01T10:00:00Z")
        }

    @Test
    fun `a wallet of only planned payments has no newest transaction time`() = runBlocking<Unit> {
        val account = account()
        // A due date is something that has not happened yet; it is not what the wallet records.
        transactionDao.save(
            transaction(account, at = null).copy(dueDate = Instant.parse("2027-01-01T10:00:00Z")),
        )

        val summary = useCase()

        summary.transactionCount shouldBe 1
        summary.newestTransactionAt shouldBe null
    }

    @Test
    fun `deleted records are not part of what is backed up`() = runBlocking<Unit> {
        val account = account()
        transactionDao.save(transaction(account, at = Instant.parse("2026-01-01T10:00:00Z")))
        transactionDao.save(
            transaction(account, at = Instant.parse("2026-05-01T10:00:00Z")).copy(isDeleted = true),
        )
        accountDao.save(account)
        accountDao.save(account().copy(isDeleted = true))
        categoryDao.save(category().copy(isDeleted = true))

        val summary = useCase()

        summary.transactionCount shouldBe 1
        summary.accountCount shouldBe 1
        summary.categoryCount shouldBe 0
        summary.newestTransactionAt shouldBe Instant.parse("2026-01-01T10:00:00Z")
    }

    private fun account() = AccountEntity(
        name = "Cash",
        color = 0,
        id = UUID.randomUUID(),
    )

    private fun category() = CategoryEntity(
        name = "Food",
        color = 0,
        id = UUID.randomUUID(),
    )

    private fun budget() = BudgetEntity(
        name = "Groceries",
        amount = 100.0,
        categoryIdsSerialized = null,
        accountIdsSerialized = null,
        orderId = 0.0,
        id = UUID.randomUUID(),
    )

    private fun transaction(account: AccountEntity, at: Instant?) = TransactionEntity(
        accountId = account.id,
        type = TransactionType.EXPENSE,
        amount = 1.0,
        dateTime = at,
        id = UUID.randomUUID(),
    )
}
