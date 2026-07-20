package com.ivy.wallet.domain.action.viewmodel.account

import arrow.core.toOption
import com.ivy.frp.action.FPAction
import com.ivy.frp.action.thenMap
import com.ivy.frp.then
import com.ivy.legacy.datamodel.Account
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.action.account.CalcAccIncomeExpenseAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class AccountDataAct @Inject constructor(
    private val exchangeAct: ExchangeAct,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val calcAccIncomeExpenseAct: CalcAccIncomeExpenseAct
) : FPAction<AccountDataAct.Input, ImmutableList<com.ivy.legacy.data.model.AccountData>>() {

    override suspend fun Input.compose(): suspend () -> ImmutableList<com.ivy.legacy.data.model.AccountData> = suspend {
        accounts
    } thenMap { acc ->
        val balance = calcAccBalanceAct(
            CalcAccBalanceAct.Input(
                account = acc,
                range = balanceRange
            )
        ).balance

        val balanceBaseCurrency = if (acc.asset.code != baseCurrency) {
            exchangeAct(
                ExchangeAct.Input(
                    data = ExchangeData(
                        baseCurrency = baseCurrency,
                        fromCurrency = acc.asset.code.toOption()
                    ),
                    amount = balance
                )
            ).orNull()
        } else {
            null
        }

        val incomeExpensePair = calcAccIncomeExpenseAct(
            CalcAccIncomeExpenseAct.Input(
                account = acc,
                range = incomeExpenseRange,
                includeTransfersInCalc = includeTransfersInCalc
            )
        ).incomeExpensePair

        com.ivy.legacy.data.model.AccountData(
            account = acc,
            balance = balance.toDouble(),
            balanceBaseCurrency = balanceBaseCurrency?.toDouble(),
            monthlyIncome = incomeExpensePair.income.toDouble(),
            monthlyExpenses = incomeExpensePair.expense.toDouble(),
        )
    } then {
        it.toImmutableList()
    }

    data class Input(
        val accounts: ImmutableList<com.ivy.data.model.Account>,
        val baseCurrency: String,
        /** Range for each account's balance. `null` = all-time balance up to now. */
        val balanceRange: ClosedTimeRange?,
        /** Range for each account's income/expense totals. */
        val incomeExpenseRange: ClosedTimeRange,
        val includeTransfersInCalc: Boolean = false
    )
}
