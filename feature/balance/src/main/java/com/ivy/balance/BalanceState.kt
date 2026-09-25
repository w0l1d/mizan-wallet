package com.ivy.balance

import androidx.compose.runtime.Immutable
import com.ivy.legacy.data.model.TimePeriod

@Immutable
data class BalanceState(
    val period: TimePeriod,
    val baseCurrencyCode: String,
    val currentBalance: Double,
    val plannedPaymentsAmount: Double,
    val balanceAfterPlannedPayments: Double,
    /**
     * What the accounts excluded from the balance hold, planned payments included.
     * Zero when no account is excluded.
     */
    val excludedAccountsBalance: Double,
    val showExcludedAccountsBalance: Boolean,
    val currentBalanceWithExcluded: Double,
    val plannedPaymentsAmountWithExcluded: Double,
    val balanceAfterPlannedPaymentsWithExcluded: Double,
)
