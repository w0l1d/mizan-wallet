package com.ivy.zakat.usecase

import arrow.core.toOption
import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import com.ivy.zakat.model.AccountBalance
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

private const val GoldNisabGrams = 85.0
private const val SilverNisabGrams = 595.0
private const val ZakatRate = 0.025

/**
 * Computes zakat on monetary wealth using the unified-annual-date method (الموعد السنوي الموحد).
 *
 * Total wealth = account balances (exchanged to base currency) + physical gold + physical silver.
 * Zakatable base = (total wealth − deductions).coerceAtLeast(0).
 * Due when zakatable ≥ nisab AND 1 Hijri year has passed since nisab was first reached.
 * Amount = zakatable × 2.5%.
 *
 * See `docs/zakat-knowledge/` for the scholarly basis; `code-mapping.md` links each rule to the
 * line in this file that implements it.
 *
 * Note on [com.ivy.data.model.ZakatConfig.deductions]: this field models الديون الحالة
 * (debts currently due). Long-term debts are out of scope per the majority contemporary opinion.
 */
class CheckZakatNisabUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
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

        val nisabReachedDate = if (config.trackingState == ZakatTrackingState.CONFIGURED && isAboveNisab) {
            findNisabReachedDate(
                config = config,
                nisabThreshold = nisab,
                currentWealth = netWealth,
                goldPrice = goldPrice,
                silverPrice = silverPrice,
                baseCurrency = baseCurrency,
            ) ?: now
        } else {
            now
        }

        val newState = computeNewState(config, isAboveNisab, nisabReachedDate)

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
        baseCurrency: String,
        endDate: Instant? = null,
    ): List<AccountBalance> {
        val allAccounts = accountRepository.findAll()
        val accounts = if (accountIds.isEmpty()) {
            allAccounts
        } else {
            allAccounts.filter { it.id in accountIds }
        }

        return accounts.map { account ->
            val range = endDate?.let { ClosedTimeRange.to(it) }
            val output = calcAccBalanceAct(
                CalcAccBalanceAct.Input(account = account, range = range)
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

    /**
     * Binary-searches transaction history to find the earliest date when total wealth
     * crossed above the nisab threshold. Returns null when no transaction history
     * exists or the crossing date cannot be determined (caller falls back to `now`).
     *
     * Uses current exchange rates and metal prices as an approximation for historical
     * values, since historical rate data is not available.
     */
    private suspend fun findNisabReachedDate(
        config: ZakatConfig,
        nisabThreshold: Double,
        currentWealth: Double,
        goldPrice: Double,
        silverPrice: Double,
        baseCurrency: String,
    ): Long? {
        val earliestDate = if (currentWealth >= nisabThreshold) {
            findEarliestTransactionDate(config.accountIds)
        } else {
            null
        }

        if (earliestDate == null) return null

        val balanceAtEarliest = computeTotalWealthAt(
            config.accountIds, earliestDate, baseCurrency,
        ) + config.physicalGoldGrams * goldPrice + config.physicalSilverGrams * silverPrice
        val netAtEarliest = (balanceAtEarliest - config.deductions).coerceAtLeast(0.0)

        return if (netAtEarliest >= nisabThreshold) {
            earliestDate.toEpochMilli()
        } else {
            binarySearchNisabDate(
                config = config,
                nisabThreshold = nisabThreshold,
                earliestDate = earliestDate,
                goldPrice = goldPrice,
                silverPrice = silverPrice,
                baseCurrency = baseCurrency,
            )
        }
    }

    private suspend fun binarySearchNisabDate(
        config: ZakatConfig,
        nisabThreshold: Double,
        earliestDate: Instant,
        goldPrice: Double,
        silverPrice: Double,
        baseCurrency: String,
    ): Long? {
        val accountIds = config.accountIds
        val now = System.currentTimeMillis()
        var low = earliestDate.toEpochMilli()
        var high = now
        var result: Long? = null

        while (low <= high) {
            val mid = low + (high - low) / 2
            val midInstant = Instant.ofEpochMilli(mid)
            val balance = computeTotalWealthAt(
                accountIds, midInstant, baseCurrency,
            ) + config.physicalGoldGrams * goldPrice + config.physicalSilverGrams * silverPrice
            val netWealth = (balance - config.deductions).coerceAtLeast(0.0)

            if (netWealth >= nisabThreshold) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        return result
    }

    private suspend fun computeTotalWealthAt(
        accountIds: List<AccountId>,
        date: Instant,
        baseCurrency: String,
    ): Double {
        val balances = calculateAccountBalances(accountIds, baseCurrency, endDate = date)
        return balances.sumOf { it.balance }
    }

    private suspend fun findEarliestTransactionDate(
        accountIds: List<AccountId>,
    ): Instant? {
        val allAccounts = accountRepository.findAll()
        val targetIds = if (accountIds.isEmpty()) {
            allAccounts.map { it.id }.toSet()
        } else {
            accountIds.toSet()
        }

        var earliest: Instant? = null
        for (id in targetIds) {
            val transactions = transactionRepository.findAllByAccountAndBetween(
                accountId = id,
                startDate = Instant.EPOCH,
                endDate = Instant.now(),
            )
            for (txn in transactions) {
                val t = txn.time
                if (earliest == null || t < earliest) {
                    earliest = t
                }
            }
        }
        return earliest
    }

    private fun computeNewState(
        config: ZakatConfig,
        isAboveNisab: Boolean,
        nisabReachedDate: Long,
    ): StateTransition {
        return when (config.trackingState) {
            ZakatTrackingState.CONFIGURED -> {
                if (isAboveNisab) {
                    StateTransition(ZakatTrackingState.NISAB_REACHED, nisabReachedDate)
                } else {
                    StateTransition(ZakatTrackingState.CONFIGURED, null)
                }
            }
            ZakatTrackingState.NISAB_REACHED -> {
                if (!isAboveNisab) {
                    // Wealth dropped below Nisab — reset
                    StateTransition(ZakatTrackingState.CONFIGURED, null)
                } else {
                    val reachedDate = config.nisabReachedDate ?: nisabReachedDate
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
                // Start a new cycle from current time
                val now = System.currentTimeMillis()
                if (isAboveNisab) {
                    StateTransition(ZakatTrackingState.NISAB_REACHED, now)
                } else {
                    StateTransition(ZakatTrackingState.CONFIGURED, null)
                }
            }
        }
    }
}
