package com.ivy.zakat.usecase

import com.ivy.data.db.dao.read.ExchangeRatesDao
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import javax.inject.Inject

private const val TroyOunceToGrams = 31.1035

class FetchMetalPricesUseCase @Inject constructor(
    private val exchangeRatesDao: ExchangeRatesDao,
    private val baseCurrencyAct: BaseCurrencyAct,
) {
    data class MetalPrices(
        val goldPricePerGram: Double?,
        val silverPricePerGram: Double?,
        val baseCurrency: String,
    )

    suspend fun fetch(): MetalPrices {
        val baseCurrency = baseCurrencyAct(Unit)

        val xauRate = exchangeRatesDao.findByBaseCurrencyAndCurrency(
            baseCurrency.lowercase(),
            "xau"
        )
        val xagRate = exchangeRatesDao.findByBaseCurrencyAndCurrency(
            baseCurrency.lowercase(),
            "xag"
        )

        // Rate means: 1 baseCurrency = rate XAU (troy ounces)
        // Gold price per troy ounce = 1 / rate
        // Gold price per gram = 1 / (rate * 31.1035)
        val goldPricePerGram = xauRate?.rate?.takeIf { it > 0 }
            ?.let { 1.0 / (it * TroyOunceToGrams) }

        val silverPricePerGram = xagRate?.rate?.takeIf { it > 0 }
            ?.let { 1.0 / (it * TroyOunceToGrams) }

        return MetalPrices(
            goldPricePerGram = goldPricePerGram,
            silverPricePerGram = silverPricePerGram,
            baseCurrency = baseCurrency,
        )
    }
}
