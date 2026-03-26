package com.ivy.zakat.detail

import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.zakat.model.AccountBalance
import kotlinx.collections.immutable.ImmutableList
import javax.annotation.concurrent.Immutable

@Suppress("DataClassTypedIDs")
@Immutable
data class ZakatDetailScreenState(
    val isEditMode: Boolean,
    val baseCurrency: String,
    val name: String,
    val nisabStandard: NisabStandard?,
    val priceSource: PriceSource,
    val manualGoldPricePerGram: String,
    val manualSilverPricePerGram: String,
    val autoGoldPricePerGram: Double?,
    val autoSilverPricePerGram: Double?,
    val physicalGoldGrams: String,
    val physicalSilverGrams: String,
    val deductions: String,
    val accounts: ImmutableList<AccountBalance>,
    val defaultDeductionAccountId: String?,
    val hijriOffset: String,
)
