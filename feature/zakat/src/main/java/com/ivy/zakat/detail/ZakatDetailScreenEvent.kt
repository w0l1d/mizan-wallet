package com.ivy.zakat.detail

import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource

@Suppress("DataClassTypedIDs")
sealed interface ZakatDetailScreenEvent {
    data class OnNameChanged(val name: String) : ZakatDetailScreenEvent
    data class OnNisabStandardChanged(val standard: NisabStandard) : ZakatDetailScreenEvent
    data class OnPriceSourceChanged(val source: PriceSource) : ZakatDetailScreenEvent
    data class OnManualGoldPriceChanged(val price: String) : ZakatDetailScreenEvent
    data class OnManualSilverPriceChanged(val price: String) : ZakatDetailScreenEvent
    data class OnPhysicalGoldGramsChanged(val grams: String) : ZakatDetailScreenEvent
    data class OnPhysicalSilverGramsChanged(val grams: String) : ZakatDetailScreenEvent
    data class OnDeductionsChanged(val deductions: String) : ZakatDetailScreenEvent
    data class OnAccountToggled(val accountId: AccountId) : ZakatDetailScreenEvent
    data class OnDefaultDeductionAccountChanged(val accountId: AccountId) :
        ZakatDetailScreenEvent
    data class OnHijriOffsetChanged(val offset: String) : ZakatDetailScreenEvent
    data object OnSave : ZakatDetailScreenEvent
    data object OnDelete : ZakatDetailScreenEvent
    data object OnRefreshPrices : ZakatDetailScreenEvent
}
