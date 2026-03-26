package com.ivy.zakat

import com.ivy.data.model.ZakatTrackingState
import com.ivy.zakat.model.AccountBalance
import kotlinx.collections.immutable.ImmutableList
import javax.annotation.concurrent.Immutable

@Immutable
data class ZakatScreenState(
    val hasConfig: Boolean,
    val baseCurrency: String,
    val trackingState: ZakatTrackingState,

    // Nisab status
    val totalWealth: Double,
    val nisabAmount: Double,
    val isAboveNisab: Boolean,
    val nisabReachedDateFormatted: String?,
    val nisabStandardLabel: String,
    val metalPriceLabel: String,

    // Hawl progress
    val hawlStartFormatted: String?,
    val hawlEndFormatted: String?,
    val hawlDaysRemaining: Int,
    val hawlDaysTotal: Int,
    val hawlProgress: Float,

    // Zakat calculation
    val netZakatable: Double,
    val zakatDue: Double,
    val deductions: Double,
    val physicalGoldValue: Double,
    val physicalSilverValue: Double,

    // Account balances
    val accountBalances: ImmutableList<AccountBalance>,

    // Hijri calendar
    val todayHijriFormatted: String,
    val hijriOffset: Int,

    // Payment history
    val totalPaid: Double,
    val remaining: Double,

    // Loading state
    val isLoading: Boolean,
)
