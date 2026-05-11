package com.ivy.zakat.usecase

import com.ivy.data.model.AccountId
import com.ivy.data.model.Expense
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.Transaction
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.data.repository.ZakatPaymentRepository
import com.ivy.zakat.model.AccountBalance
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.Called
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * Verifies that [GenerateZakatExpenseUseCase] creates the right expense transaction,
 * payment record, and config update when the user pays zakat. Uses actual data from
 * the worked examples in `docs/zakat-knowledge/worked-examples.md`.
 */
class GenerateZakatExpenseUseCaseTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private val zakatConfigRepository = mockk<ZakatConfigRepository>()
    private val zakatPaymentRepository = mockk<ZakatPaymentRepository>()

    private val useCase = GenerateZakatExpenseUseCase(
        transactionRepository = transactionRepository,
        zakatConfigRepository = zakatConfigRepository,
        zakatPaymentRepository = zakatPaymentRepository,
    )

    @Test
    fun `example 1 — creates expense for 900 SAR zakat from 36K savings`() = runTest {
        // Given: 36,000 SAR savings, gold 250/g → nisab 21,250, zakatDue = 900.
        val account = accountBalance(id = UUID.randomUUID(), name = "Savings", balance = 36_000.0)
        val config = zakatConfig(
            zakatDue = 900.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
        )
        arrange()

        val result = useCase.generate(config, listOf(account))

        result.shouldBeTrue()
        coVerify { transactionRepository.save(match<Transaction> { it is Expense }) }
        coVerify {
            zakatPaymentRepository.save(
                match {
                    it.amount == 900.0
                }
            )
        }
        coVerify {
            zakatConfigRepository.save(
                match {
                    it.trackingState == ZakatTrackingState.ZAKAT_PAID
                }
            )
        }
    }

    @Test
    fun `example 3 — creates expense for 1_125 SAR from mixed cash and gold`() = runTest {
        // 20,000 cash + 100g physical gold × 250 = 45,000 → zakat 1,125.
        val account = accountBalance(id = UUID.randomUUID(), name = "Main", balance = 20_000.0)
        val config = zakatConfig(
            zakatDue = 1_125.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
            physicalGoldGrams = 100.0,
        )
        arrange()

        val result = useCase.generate(config, listOf(account))

        result.shouldBeTrue()
        coVerify {
            zakatPaymentRepository.save(
                match {
                    (it.amount - 1_125.0) < 0.02
                }
            )
        }
        coVerify {
            zakatConfigRepository.save(
                match {
                    it.trackingState == ZakatTrackingState.ZAKAT_PAID
                }
            )
        }
    }

    @Test
    fun `example 4 — creates expense for 1_000 SAR after 10K deductions`() = runTest {
        // 50,000 - 10,000 due debt = 40,000 → zakat 1,000.
        val account = accountBalance(id = UUID.randomUUID(), name = "Check", balance = 50_000.0)
        val config = zakatConfig(
            zakatDue = 1_000.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
            deductions = 10_000.0,
        )
        arrange()

        val result = useCase.generate(config, listOf(account))

        result.shouldBeTrue()
        coVerify {
            zakatPaymentRepository.save(
                match {
                    (it.amount - 1_000.0) < 0.02
                }
            )
        }
    }

    @Test
    fun `example 7 — creates expense for 250 SAR under silver nisab`() = runTest {
        // 10,000 wealth, silver 3/g → nisab 1,785, zakat 250.
        val account = accountBalance(id = UUID.randomUUID(), name = "Silver", balance = 10_000.0)
        val config = zakatConfig(
            zakatDue = 250.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
            nisabStandard = NisabStandard.SILVER,
        )
        arrange()

        val result = useCase.generate(config, listOf(account))

        result.shouldBeTrue()
        coVerify {
            zakatPaymentRepository.save(
                match {
                    (it.amount - 250.0) < 0.02
                }
            )
        }
    }

    @Test
    fun `returns false when zakatDue is zero`() = runTest {
        val account = accountBalance(id = UUID.randomUUID(), name = "Empty", balance = 0.0)
        val config = zakatConfig(
            zakatDue = 0.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
        )

        val result = useCase.generate(config, listOf(account))

        result.shouldBeFalse()
        coVerify { transactionRepository wasNot Called }
    }

    @Test
    fun `returns false when zakatDue is negative`() = runTest {
        val account = accountBalance(id = UUID.randomUUID(), name = "Acc", balance = 100.0)
        val config = zakatConfig(
            zakatDue = -50.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
        )

        val result = useCase.generate(config, listOf(account))

        result.shouldBeFalse()
        coVerify { transactionRepository wasNot Called }
    }

    @Test
    fun `returns false when no deduction account and no accountIds`() = runTest {
        val config = zakatConfig(
            zakatDue = 500.0,
            accountIds = emptyList(),
            deductionAccountId = null,
        )

        val result = useCase.generate(config, emptyList())

        result.shouldBeFalse()
        coVerify { transactionRepository wasNot Called }
    }

    @Test
    fun `falls back to first accountId when deductionAccountId is null`() = runTest {
        val account = accountBalance(id = UUID.randomUUID(), name = "First", balance = 10_000.0)
        val config = zakatConfig(
            zakatDue = 250.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = null,
        )
        arrange()

        val result = useCase.generate(config, listOf(account))

        result.shouldBeTrue()
        coVerify { transactionRepository.save(match<Transaction> { it is Expense }) }
    }

    @Test
    fun `payment record contains correct config ID reference`() = runTest {
        val account = accountBalance(id = UUID.randomUUID(), name = "Savings", balance = 36_000.0)
        val configId = ZakatConfigId(UUID.randomUUID())
        val config = zakatConfig(
            id = configId,
            zakatDue = 900.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
        )
        arrange()

        useCase.generate(config, listOf(account))

        coVerify {
            zakatPaymentRepository.save(
                match {
                    it.zakatConfigId == configId && it.amount == 900.0
                }
            )
        }
    }

    @Test
    fun `config saved with ZAKAT_PAID state and all other fields preserved`() = runTest {
        val account = accountBalance(id = UUID.randomUUID(), name = "Main", balance = 20_000.0)
        val config = zakatConfig(
            zakatDue = 500.0,
            accountIds = listOf(account.accountId),
            deductionAccountId = account.accountId,
            physicalGoldGrams = 30.0,
            deductions = 2_000.0,
            nisabStandard = NisabStandard.GOLD,
        )
        arrange()

        useCase.generate(config, listOf(account))

        coVerify {
            zakatConfigRepository.save(
                match {
                    it.trackingState == ZakatTrackingState.ZAKAT_PAID &&
                        it.totalWealth == config.totalWealth &&
                        it.physicalGoldGrams == 30.0 &&
                        it.deductions == 2_000.0 &&
                        it.nisabStandard == NisabStandard.GOLD
                }
            )
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    private fun arrange() {
        coEvery { transactionRepository.save(any<Transaction>()) } just Runs
        coEvery { zakatPaymentRepository.save(any()) } just Runs
        coEvery { zakatConfigRepository.save(any()) } just Runs
    }

    private fun accountBalance(
        id: UUID,
        name: String,
        balance: Double,
    ): AccountBalance = AccountBalance(
        accountId = AccountId(id),
        name = name,
        balance = balance,
        currency = "USD",
        selected = true,
    )

    @Suppress("LongParameterList")
    private fun zakatConfig(
        id: ZakatConfigId = ZakatConfigId(UUID.randomUUID()),
        zakatDue: Double,
        accountIds: List<AccountId>,
        deductionAccountId: AccountId?,
        nisabStandard: NisabStandard = NisabStandard.GOLD,
        physicalGoldGrams: Double = 0.0,
        deductions: Double = 0.0,
    ): ZakatConfig = ZakatConfig(
        id = id,
        name = NotBlankTrimmedString.unsafe("Test Zakat Config"),
        nisabStandard = nisabStandard,
        priceSource = PriceSource.MANUAL,
        manualGoldPricePerGram = 250.0,
        manualSilverPricePerGram = 3.0,
        physicalGoldGrams = physicalGoldGrams,
        physicalSilverGrams = 0.0,
        deductions = deductions,
        accountIds = accountIds,
        defaultDeductionAccountId = deductionAccountId,
        hijriOffset = 0,
        trackingState = ZakatTrackingState.HAWL_COMPLETE,
        nisabReachedDate = System.currentTimeMillis() - 400L * 86_400_000L,
        hawlStartDate = System.currentTimeMillis() - 400L * 86_400_000L,
        hawlEndDate = System.currentTimeMillis(),
        lastCheckDate = System.currentTimeMillis(),
        lastCheckWealth = 36_000.0,
        goldPricePerGram = 250.0,
        silverPricePerGram = 3.0,
        totalWealth = 36_000.0,
        nisabAmount = 21_250.0,
        netZakatable = 36_000.0,
        zakatDue = zakatDue,
        currency = AssetCode.USD,
        orderNum = 0.0,
    )
}
