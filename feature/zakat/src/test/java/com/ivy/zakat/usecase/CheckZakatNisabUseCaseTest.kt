package com.ivy.zakat.usecase

import arrow.core.Option
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * Scenarios verify the calculation and state machine in [CheckZakatNisabUseCase] against the
 * worked examples in `docs/zakat-knowledge/worked-examples.md`. The two must stay in lockstep.
 */
class CheckZakatNisabUseCaseTest {

    private val accountRepository = mockk<AccountRepository>()
    private val calcAccBalanceAct = mockk<CalcAccBalanceAct>()
    private val exchangeAct = mockk<ExchangeAct>()
    private val zakatConfigRepository = mockk<ZakatConfigRepository>()
    private val baseCurrencyAct = mockk<BaseCurrencyAct>()
    private val fetchMetalPricesUseCase = mockk<FetchMetalPricesUseCase>()

    private val useCase = CheckZakatNisabUseCase(
        accountRepository = accountRepository,
        calcAccBalanceAct = calcAccBalanceAct,
        exchangeAct = exchangeAct,
        zakatConfigRepository = zakatConfigRepository,
        baseCurrencyAct = baseCurrencyAct,
        fetchMetalPricesUseCase = fetchMetalPricesUseCase,
    )

    @Test
    fun `example 1 — fixed salary with hawl complete yields 2_5 percent zakat`() = runTest {
        // Given: 36,000 SAR savings, gold 250/g, nisab = 21,250. Hawl already complete.
        val account = account(balance = 36_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("36000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
        )

        val result = useCase.check(config)

        result.config.totalWealth shouldBe 36_000.0.plusOrMinus(0.01)
        result.config.netZakatable shouldBe 36_000.0.plusOrMinus(0.01)
        result.config.nisabAmount shouldBe (85.0 * 250.0).plusOrMinus(0.01)
        result.config.trackingState shouldBe ZakatTrackingState.HAWL_COMPLETE
        result.config.zakatDue shouldBe 900.0.plusOrMinus(0.01)
    }

    @Test
    fun `example 3 — mixed cash and physical gold produces combined wealth`() = runTest {
        // 20,000 cash + 100g physical gold × 250 = 45,000. Zakat = 1,125.
        val account = account(balance = 20_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("20000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
            physicalGoldGrams = 100.0,
        )

        val result = useCase.check(config)

        result.config.totalWealth shouldBe 45_000.0.plusOrMinus(0.01)
        result.config.zakatDue shouldBe 1_125.0.plusOrMinus(0.01)
    }

    @Test
    fun `example 4 — deductions are subtracted from zakatable base`() = runTest {
        // 50,000 − 10,000 due debt = 40,000 → zakat 1,000.
        val account = account(balance = 50_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("50000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
            deductions = 10_000.0,
        )

        val result = useCase.check(config)

        result.config.totalWealth shouldBe 50_000.0.plusOrMinus(0.01)
        result.config.netZakatable shouldBe 40_000.0.plusOrMinus(0.01)
        result.config.zakatDue shouldBe 1_000.0.plusOrMinus(0.01)
    }

    @Test
    fun `example 5 — wealth dropping below nisab mid-hawl interrupts and resets`() = runTest {
        // Was NISAB_REACHED (hawl in progress), now wealth is 5,000 vs nisab 21,250.
        val account = account(balance = 5_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("5000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(100),
            accountIds = listOf(account.id),
        )

        val result = useCase.check(config)

        result.config.trackingState shouldBe ZakatTrackingState.CONFIGURED
        result.config.nisabReachedDate.shouldBeNull()
        result.config.zakatDue shouldBe 0.0
    }

    @Test
    fun `below nisab from the start keeps state CONFIGURED and yields no zakat`() = runTest {
        // 15,000 wealth vs nisab 21,250.
        val account = account(balance = 15_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("15000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.CONFIGURED,
            nisabReachedDate = null,
            accountIds = listOf(account.id),
        )

        val result = useCase.check(config)

        result.config.trackingState shouldBe ZakatTrackingState.CONFIGURED
        result.config.nisabReachedDate.shouldBeNull()
        result.config.zakatDue shouldBe 0.0
    }

    @Test
    fun `just crossed nisab but hawl not complete stays in NISAB_REACHED`() = runTest {
        val account = account(balance = 30_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("30000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.CONFIGURED,
            nisabReachedDate = null,
            accountIds = listOf(account.id),
        )

        val result = useCase.check(config)

        result.config.trackingState shouldBe ZakatTrackingState.NISAB_REACHED
        result.config.nisabReachedDate.shouldNotBeNull()
        result.config.zakatDue shouldBe 0.0
    }

    @Test
    fun `ZAKAT_PAID and wealth still above nisab begins a new cycle in NISAB_REACHED`() = runTest {
        val account = account(balance = 40_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("40000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.ZAKAT_PAID,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
        )

        val result = useCase.check(config)

        result.config.trackingState shouldBe ZakatTrackingState.NISAB_REACHED
        result.config.zakatDue shouldBe 0.0
    }

    @Test
    fun `deductions exceeding wealth floor zakatable base at zero`() = runTest {
        val account = account(balance = 5_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("5000")),
            goldPrice = 250.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
            deductions = 10_000.0,
        )

        val result = useCase.check(config)

        result.config.netZakatable shouldBe 0.0
        result.config.zakatDue shouldBe 0.0
    }

    @Test
    fun `example 7 — silver nisab standard lowers the threshold`() = runTest {
        // 10,000 wealth, silver 3/g, nisab = 595 × 3 = 1,785. Hawl complete.
        val account = account(balance = 10_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("10000")),
            goldPrice = 250.0,
            silverPrice = 3.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
            nisabStandard = NisabStandard.SILVER,
        )

        val result = useCase.check(config)

        result.config.nisabAmount shouldBe (595.0 * 3.0).plusOrMinus(0.01)
        result.config.zakatDue shouldBe 250.0.plusOrMinus(0.01)
    }

    @Test
    fun `automatic price source falls back to manual when auto fetch returns null`() = runTest {
        val account = account(balance = 36_000.0)
        coEvery { baseCurrencyAct(Unit) } returns "USD"
        coEvery { accountRepository.findAll() } returns listOf(account)
        coEvery { calcAccBalanceAct(any()) } returns
            CalcAccBalanceAct.Output(account = account, balance = BigDecimal("36000"))
        coEvery { exchangeAct(any()) } returns Option.fromNullable(BigDecimal("36000"))
        coEvery { zakatConfigRepository.save(any()) } just Runs
        coEvery { fetchMetalPricesUseCase.fetch() } returns FetchMetalPricesUseCase.MetalPrices(
            goldPricePerGram = null,
            silverPricePerGram = null,
            baseCurrency = "USD",
        )
        val config = zakatConfig(
            state = ZakatTrackingState.NISAB_REACHED,
            nisabReachedDate = daysAgo(400),
            accountIds = listOf(account.id),
            priceSource = PriceSource.AUTOMATIC,
            manualGoldPricePerGram = 250.0,
            manualSilverPricePerGram = 3.0,
        )

        val result = useCase.check(config)

        result.config.goldPricePerGram shouldBe 250.0.plusOrMinus(0.01)
        result.config.silverPricePerGram shouldBe 3.0.plusOrMinus(0.01)
        result.config.zakatDue shouldBe 900.0.plusOrMinus(0.01)
    }

    @Test
    fun `zero gold price yields zero nisab which the guard treats as not above nisab`() = runTest {
        val account = account(balance = 1_000_000.0)
        arrange(
            accounts = listOf(account),
            balances = mapOf(account.id to BigDecimal("1000000")),
            goldPrice = 0.0,
        )
        val config = zakatConfig(
            state = ZakatTrackingState.CONFIGURED,
            nisabReachedDate = null,
            accountIds = listOf(account.id),
            manualGoldPricePerGram = 0.0,
            manualSilverPricePerGram = 0.0,
        )

        val result = useCase.check(config)

        result.config.nisabAmount shouldBe 0.0
        result.config.trackingState shouldBe ZakatTrackingState.CONFIGURED
        result.config.zakatDue shouldBe 0.0
    }

    // --- helpers ---------------------------------------------------------------------------

    private fun arrange(
        accounts: List<Account>,
        balances: Map<AccountId, BigDecimal>,
        goldPrice: Double,
        silverPrice: Double = 3.0,
        baseCurrency: String = "USD",
    ) {
        coEvery { baseCurrencyAct(Unit) } returns baseCurrency
        coEvery { accountRepository.findAll() } returns accounts
        for (account in accounts) {
            val balance = balances[account.id] ?: BigDecimal.ZERO
            coEvery {
                calcAccBalanceAct(match<CalcAccBalanceAct.Input> { it.account.id == account.id })
            } returns CalcAccBalanceAct.Output(account = account, balance = balance)
        }
        coEvery { exchangeAct(any()) } answers {
            val input = firstArg<ExchangeAct.Input>()
            Option.fromNullable(input.amount)
        }
        coEvery { zakatConfigRepository.save(any()) } just Runs
        coEvery { fetchMetalPricesUseCase.fetch() } returns FetchMetalPricesUseCase.MetalPrices(
            goldPricePerGram = goldPrice,
            silverPricePerGram = silverPrice,
            baseCurrency = baseCurrency,
        )
    }

    private fun account(balance: Double): Account = Account(
        id = AccountId(UUID.randomUUID()),
        name = NotBlankTrimmedString.unsafe("Account-${balance.toInt()}"),
        asset = AssetCode.USD,
        color = ColorInt(0),
        icon = null,
        includeInBalance = true,
        orderNum = 0.0,
    )

    @Suppress("LongParameterList")
    private fun zakatConfig(
        state: ZakatTrackingState,
        nisabReachedDate: Long?,
        accountIds: List<AccountId>,
        nisabStandard: NisabStandard = NisabStandard.GOLD,
        physicalGoldGrams: Double = 0.0,
        physicalSilverGrams: Double = 0.0,
        deductions: Double = 0.0,
        priceSource: PriceSource = PriceSource.MANUAL,
        manualGoldPricePerGram: Double = 250.0,
        manualSilverPricePerGram: Double = 3.0,
    ): ZakatConfig = ZakatConfig(
        id = ZakatConfigId(UUID.randomUUID()),
        name = NotBlankTrimmedString.unsafe("Test Config"),
        nisabStandard = nisabStandard,
        priceSource = priceSource,
        manualGoldPricePerGram = manualGoldPricePerGram,
        manualSilverPricePerGram = manualSilverPricePerGram,
        physicalGoldGrams = physicalGoldGrams,
        physicalSilverGrams = physicalSilverGrams,
        deductions = deductions,
        accountIds = accountIds,
        defaultDeductionAccountId = null,
        hijriOffset = 0,
        trackingState = state,
        nisabReachedDate = nisabReachedDate,
        hawlStartDate = nisabReachedDate ?: 0L,
        hawlEndDate = 0L,
        lastCheckDate = null,
        lastCheckWealth = null,
        goldPricePerGram = 0.0,
        silverPricePerGram = 0.0,
        totalWealth = 0.0,
        nisabAmount = 0.0,
        netZakatable = 0.0,
        zakatDue = 0.0,
        currency = AssetCode.USD,
        orderNum = 0.0,
    )

    private fun daysAgo(days: Int): Long {
        return System.currentTimeMillis() - (days.toLong() * 86_400_000L)
    }
}
