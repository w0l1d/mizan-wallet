package com.ivy.zakat.usecase

import arrow.core.toOption
import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import com.ivy.zakat.model.AccountBalance
import java.math.BigDecimal
import javax.inject.Inject

private const val GoldNisabGrams = 85.0
private const val SilverNisabGrams = 595.0
private const val ZakatRate = 0.025

class CheckZakatNisabUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val exchangeAct: ExchangeAct,
    private val zakatConfigRepository: ZakatConfigRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val fetchMetalPricesUseCase: FetchMetalPricesUseCase,
) {
    data class NisabCheckResult(
        val config: ZakatConfig,
        val accountBalances: List<AccountBalance>,
    )

    suspend fun check(config: ZakatConfig): NisabCheckResult {
        val baseCurrency = baseCurrencyAct(Unit)

        // 1. Calculate account balances
        val accountBalances = calculateAccountBalances(config.accountIds, baseCurrency)
        val accountWealth = accountBalances.sumOf { it.balance }

        // 2. Get metal prices
        val (goldPrice, silverPrice) = getEffectivePrices(config)

        // 3. Add physical holdings value
        val physicalGoldValue = config.physicalGoldGrams * goldPrice
        val physicalSilverValue = config.physicalSilverGrams * silverPrice

        // 4. Total wealth
        val totalWealth = accountWealth + physicalGoldValue + physicalSilverValue
        val netWealth = (totalWealth - config.deductions).coerceAtLeast(0.0)

        // 5. Calculate Nisab threshold
        val nisab = when (config.nisabStandard) {
            NisabStandard.GOLD -> GoldNisabGrams * goldPrice
            NisabStandard.SILVER -> SilverNisabGrams * silverPrice
        }

        // 6. State transition
        val now = System.currentTimeMillis()
        val isAboveNisab = netWealth >= nisab && nisab > 0

        val newState = computeNewState(config, isAboveNisab, now)

        // 7. Calculate Zakat due
        val zakatDue = if (newState.state == ZakatTrackingState.HAWL_COMPLETE) {
            netWealth * ZakatRate
        } else {
            0.0
        }

        // 8. Build Hawl dates
        val hawlStart = newState.nisabReachedDate ?: config.hawlStartDate
        val hawlEnd = if (newState.nisabReachedDate != null) {
            HijriCalendarUtils.hawlEndDateMillis(
                newState.nisabReachedDate,
                config.hijriOffset
            )
        } else {
            config.hawlEndDate
        }

        // 9. Update config
        val updatedConfig = config.copy(
            trackingState = newState.state,
            nisabReachedDate = newState.nisabReachedDate,
            hawlStartDate = hawlStart,
            hawlEndDate = hawlEnd,
            lastCheckDate = now,
            lastCheckWealth = totalWealth,
            goldPricePerGram = goldPrice,
            silverPricePerGram = silverPrice,
            totalWealth = totalWealth,
            nisabAmount = nisab,
            netZakatable = netWealth,
            zakatDue = zakatDue,
        )

        zakatConfigRepository.save(updatedConfig)

        return NisabCheckResult(
            config = updatedConfig,
            accountBalances = accountBalances,
        )
    }

    private suspend fun calculateAccountBalances(
        accountIds: List<AccountId>,
        baseCurrency: String
    ): List<AccountBalance> {
        val allAccounts = accountRepository.findAll()
        val accounts = if (accountIds.isEmpty()) {
            allAccounts
        } else {
            allAccounts.filter { it.id in accountIds }
        }

        return accounts.map { account ->
            val output = calcAccBalanceAct(
                CalcAccBalanceAct.Input(account = account)
            )

            val exchanged = exchangeAct(
                ExchangeAct.Input(
                    data = ExchangeData(
                        baseCurrency = baseCurrency,
                        fromCurrency = account.asset.code.toOption(),
                    ),
                    amount = output.balance
                )
            ).orNull() ?: BigDecimal.ZERO

            AccountBalance(
                accountId = account.id,
                name = account.name.value,
                balance = exchanged.toDouble(),
                currency = account.asset.code,
                selected = true,
            )
        }
    }

    private suspend fun getEffectivePrices(config: ZakatConfig): Pair<Double, Double> {
        return when (config.priceSource) {
            PriceSource.MANUAL -> Pair(
                config.manualGoldPricePerGram,
                config.manualSilverPricePerGram
            )
            PriceSource.AUTOMATIC -> {
                val prices = fetchMetalPricesUseCase.fetch()
                Pair(
                    prices.goldPricePerGram ?: config.manualGoldPricePerGram,
                    prices.silverPricePerGram ?: config.manualSilverPricePerGram
                )
            }
        }
    }

    private data class StateTransition(
        val state: ZakatTrackingState,
        val nisabReachedDate: Long?,
    )

    private fun computeNewState(
        config: ZakatConfig,
        isAboveNisab: Boolean,
        now: Long,
    ): StateTransition {
        return when (config.trackingState) {
            ZakatTrackingState.CONFIGURED -> {
                if (isAboveNisab) {
                    StateTransition(ZakatTrackingState.NISAB_REACHED, now)
                } else {
                    StateTransition(ZakatTrackingState.CONFIGURED, null)
                }
            }
            ZakatTrackingState.NISAB_REACHED -> {
                if (!isAboveNisab) {
                    // Wealth dropped below Nisab — reset
                    StateTransition(ZakatTrackingState.CONFIGURED, null)
                } else {
                    val reachedDate = config.nisabReachedDate ?: now
                    if (HijriCalendarUtils.isHawlComplete(reachedDate, config.hijriOffset)) {
                        StateTransition(ZakatTrackingState.HAWL_COMPLETE, reachedDate)
                    } else {
                        StateTransition(ZakatTrackingState.NISAB_REACHED, reachedDate)
                    }
                }
            }
            ZakatTrackingState.HAWL_COMPLETE -> {
                // Stay in HAWL_COMPLETE until user pays
                val reachedDate = config.nisabReachedDate
                StateTransition(ZakatTrackingState.HAWL_COMPLETE, reachedDate)
            }
            ZakatTrackingState.ZAKAT_PAID -> {
                // Start a new cycle
                if (isAboveNisab) {
                    StateTransition(ZakatTrackingState.NISAB_REACHED, now)
                } else {
                    StateTransition(ZakatTrackingState.CONFIGURED, null)
                }
            }
        }
    }
}
