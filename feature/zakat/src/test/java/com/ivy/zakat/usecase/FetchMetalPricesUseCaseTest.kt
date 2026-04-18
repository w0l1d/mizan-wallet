package com.ivy.zakat.usecase

import com.ivy.data.db.dao.read.ExchangeRatesDao
import com.ivy.data.db.entity.ExchangeRateEntity
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val TroyOunceToGrams = 31.1035
private const val BaseCurrency = "usd"

class FetchMetalPricesUseCaseTest {

    private val exchangeRatesDao = mockk<ExchangeRatesDao>()
    private val baseCurrencyAct = mockk<BaseCurrencyAct>()

    private val useCase = FetchMetalPricesUseCase(
        exchangeRatesDao = exchangeRatesDao,
        baseCurrencyAct = baseCurrencyAct,
    )

    @Test
    fun `valid XAU and XAG rates yield per-gram prices in base currency`() = runTest {
        // Rate = 1 USD → 0.0005 XAU (troy ounces). So 1 XAU ≈ 2000 USD, per gram ≈ 64.30 USD.
        val xauRate = 0.0005
        val xagRate = 0.04 // 1 USD → 0.04 XAG → 1 XAG ≈ 25 USD → per gram ≈ 0.8038 USD
        coEvery { baseCurrencyAct(Unit) } returns BaseCurrency.uppercase()
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xau") } returns
            ExchangeRateEntity(baseCurrency = BaseCurrency, currency = "xau", rate = xauRate)
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xag") } returns
            ExchangeRateEntity(baseCurrency = BaseCurrency, currency = "xag", rate = xagRate)

        val prices = useCase.fetch()

        prices.goldPricePerGram!! shouldBe (1.0 / (xauRate * TroyOunceToGrams)).plusOrMinus(0.0001)
        prices.silverPricePerGram!! shouldBe (1.0 / (xagRate * TroyOunceToGrams)).plusOrMinus(0.0001)
        prices.baseCurrency shouldBe BaseCurrency.uppercase()
    }

    @Test
    fun `zero rate is treated as unavailable`() = runTest {
        coEvery { baseCurrencyAct(Unit) } returns BaseCurrency.uppercase()
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xau") } returns
            ExchangeRateEntity(baseCurrency = BaseCurrency, currency = "xau", rate = 0.0)
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xag") } returns null

        val prices = useCase.fetch()

        prices.goldPricePerGram.shouldBeNull()
        prices.silverPricePerGram.shouldBeNull()
    }

    @Test
    fun `negative rate is treated as unavailable`() = runTest {
        coEvery { baseCurrencyAct(Unit) } returns BaseCurrency.uppercase()
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xau") } returns
            ExchangeRateEntity(baseCurrency = BaseCurrency, currency = "xau", rate = -0.001)
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xag") } returns null

        val prices = useCase.fetch()

        prices.goldPricePerGram.shouldBeNull()
    }

    @Test
    fun `missing rate row yields null price`() = runTest {
        coEvery { baseCurrencyAct(Unit) } returns BaseCurrency.uppercase()
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xau") } returns null
        coEvery { exchangeRatesDao.findByBaseCurrencyAndCurrency(BaseCurrency, "xag") } returns null

        val prices = useCase.fetch()

        prices.goldPricePerGram.shouldBeNull()
        prices.silverPricePerGram.shouldBeNull()
    }
}
