package com.ivy.zakat

sealed interface ZakatScreenEvent {
    data object OnSetup : ZakatScreenEvent
    data object OnOpenSettings : ZakatScreenEvent
    data object OnRefresh : ZakatScreenEvent
    data object OnPayZakat : ZakatScreenEvent
}
