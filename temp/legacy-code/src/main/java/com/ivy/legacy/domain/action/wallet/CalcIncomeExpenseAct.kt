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
import java.util.UUID
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
        stats.map { exchangeToBase(acc, it) }
    } then { statsList ->
        val adjustment = excludedTransferAdjustment()
        IncomeExpensePair(
            income = statsList.sumOf { it[0] } + adjustment.income,
            expense = statsList.sumOf { it[1] } + adjustment.expense
        )
    }

    /**
     * Extra income/expense contributed by transfers to/from excluded accounts,
     * when the corresponding flags are enabled. Zero when disabled or when there
     * are no excluded accounts.
     */
    private suspend fun Input.excludedTransferAdjustment(): IncomeExpensePair {
        val excludedAccountIds = accounts.filterNot { it.includeInBalance }.map { it.id }.toSet()
        val enabled = transfersToExcludedAsExpense || transfersFromExcludedAsIncome
        if (excludedAccountIds.isEmpty() || !enabled) return IncomeExpensePair.zero()

        var income = BigDecimal.ZERO
        var expense = BigDecimal.ZERO
        for (account in accounts.filter { it.includeInBalance }) {
            val transfers = accTrnsAct(AccTrnsAct.Input(accountId = account.id, range = range))
                .filterIsInstance<Transfer>()
            for (transfer in transfers) {
                expense += transferExpense(account, transfer, excludedAccountIds)
                income += transferIncome(account, transfer, excludedAccountIds)
            }
        }
        return IncomeExpensePair(income = income, expense = expense)
    }

    /** The amount leaving an included account towards an excluded one, or zero. */
    private suspend fun Input.transferExpense(
        account: Account,
        transfer: Transfer,
        excludedAccountIds: Set<UUID>
    ): BigDecimal {
        if (!transfersToExcludedAsExpense) return BigDecimal.ZERO
        val toExcluded = transfer.toAccount.value in excludedAccountIds
        val fromExcluded = transfer.fromAccount.value in excludedAccountIds
        val isSelfTransfer = transfer.fromAccount.value == transfer.toAccount.value
        return if (toExcluded && !fromExcluded && !isSelfTransfer) {
            exchangeToBase(account, transfer.fromValue.amount.value.toBigDecimal())
        } else {
            BigDecimal.ZERO
        }
    }

    /** The amount arriving in an included account from an excluded one, or zero. */
    private suspend fun Input.transferIncome(
        account: Account,
        transfer: Transfer,
        excludedAccountIds: Set<UUID>
    ): BigDecimal {
        if (!transfersFromExcludedAsIncome) return BigDecimal.ZERO
        val toExcluded = transfer.toAccount.value in excludedAccountIds
        val fromExcluded = transfer.fromAccount.value in excludedAccountIds
        val isSelfTransfer = transfer.fromAccount.value == transfer.toAccount.value
        return if (fromExcluded && !toExcluded && !isSelfTransfer) {
            exchangeToBase(account, transfer.toValue.amount.value.toBigDecimal())
        } else {
            BigDecimal.ZERO
        }
    }

    private suspend fun Input.exchangeToBase(account: Account, amount: BigDecimal): BigDecimal =
        exchangeAct(
            ExchangeAct.Input(
                data = ExchangeData(
                    baseCurrency = baseCurrency,
                    fromCurrency = (account.currency ?: baseCurrency).toOption()
                ),
                amount = amount
            )
        ).orZero()

    @Suppress("DataClassDefaultValues")
    data class Input(
        val baseCurrency: String,
        val accounts: List<Account>,
        val range: ClosedTimeRange,
        val transfersToExcludedAsExpense: Boolean = false,
        val transfersFromExcludedAsIncome: Boolean = false,
    )
}
