package com.ivy.wallet.domain.action.wallet

import arrow.core.nonEmptyListOf
import arrow.core.toOption
import com.ivy.frp.action.FPAction
import com.ivy.frp.action.thenMap
import com.ivy.frp.then
import com.ivy.data.model.Transfer
import com.ivy.legacy.datamodel.Account
import com.ivy.wallet.domain.action.account.AccTrnsAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.pure.account.filterExcluded
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import com.ivy.wallet.domain.pure.data.IncomeExpensePair
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import com.ivy.legacy.domain.pure.transaction.AccountValueFunctions
import com.ivy.wallet.domain.pure.transaction.foldTransactions
import com.ivy.wallet.domain.pure.util.orZero
import java.math.BigDecimal
import timber.log.Timber
import javax.inject.Inject

class CalcIncomeExpenseAct @Inject constructor(
    private val accTrnsAct: AccTrnsAct,
    private val exchangeAct: ExchangeAct
) : FPAction<CalcIncomeExpenseAct.Input, IncomeExpensePair>() {

    override suspend fun Input.compose(): suspend () -> IncomeExpensePair = suspend {
        filterExcluded(accounts)
    } thenMap { acc ->
        Pair(
            acc,
            accTrnsAct(
                AccTrnsAct.Input(
                    accountId = acc.id,
                    range = range
                )
            )
        )
    } thenMap { (acc, trns) ->
        Timber.i("acc: $acc, trns = ${trns.size}")
        Pair(
            acc,
            foldTransactions(
                transactions = trns,
                valueFunctions = nonEmptyListOf(
                    AccountValueFunctions::income,
                    AccountValueFunctions::expense
                ),
                arg = acc.id
            )
        )
    } thenMap { (acc, stats) ->
        Timber.i("acc_stats: $acc - $stats")
        stats.map {
            exchangeAct(
                ExchangeAct.Input(
                    data = ExchangeData(
                        baseCurrency = baseCurrency,
                        fromCurrency = (acc.currency ?: baseCurrency).toOption()
                    ),
                    amount = it
                ),
            ).orZero()
        }
    } then { statsList ->
        var totalIncome = statsList.sumOf { it[0] }
        var totalExpense = statsList.sumOf { it[1] }

        // Add transfers to/from excluded accounts if corresponding flags are enabled
        val excludedAccountIds = accounts.filter { !it.includeInBalance }.map { it.id }.toSet()
        val hasExcludedAccounts = excludedAccountIds.isNotEmpty()

        if (hasExcludedAccounts &&
            (transfersToExcludedAsExpense || transfersFromExcludedAsIncome)
        ) {
            val includedAccounts = accounts.filter { it.includeInBalance }

            for (acc in includedAccounts) {
                val allTrns = accTrnsAct(
                    AccTrnsAct.Input(accountId = acc.id, range = range)
                )
                val transfers = allTrns.filterIsInstance<Transfer>()

                for (transfer in transfers) {
                    val toExcluded = transfer.toAccount.value in excludedAccountIds
                    val fromExcluded = transfer.fromAccount.value in excludedAccountIds
                    val isSelfTransfer = transfer.fromAccount.value == transfer.toAccount.value

                    if (isSelfTransfer) continue

                    if (transfersToExcludedAsExpense && toExcluded && !fromExcluded) {
                        totalExpense += exchangeAct(
                            ExchangeAct.Input(
                                data = ExchangeData(
                                    baseCurrency = baseCurrency,
                                    fromCurrency = (acc.currency ?: baseCurrency).toOption()
                                ),
                                amount = transfer.fromValue.amount.value.toBigDecimal()
                            )
                        ).orZero()
                    }

                    if (transfersFromExcludedAsIncome && fromExcluded && !toExcluded) {
                        totalIncome += exchangeAct(
                            ExchangeAct.Input(
                                data = ExchangeData(
                                    baseCurrency = baseCurrency,
                                    fromCurrency = (acc.currency ?: baseCurrency).toOption()
                                ),
                                amount = transfer.toValue.amount.value.toBigDecimal()
                            )
                        ).orZero()
                    }
                }
            }
        }

        IncomeExpensePair(
            income = totalIncome,
            expense = totalExpense
        )
    }

    data class Input(
        val baseCurrency: String,
        val accounts: List<Account>,
        val range: ClosedTimeRange,
        val transfersToExcludedAsExpense: Boolean = false,
        val transfersFromExcludedAsIncome: Boolean = false,
    )
}
