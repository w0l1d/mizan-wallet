package com.ivy.accounts

import com.ivy.legacy.data.model.TimePeriod

sealed interface AccountsEvent {
    data class OnReorder(val reorderedList: List<com.ivy.legacy.data.model.AccountData>) :
        AccountsEvent
    data class OnReorderModalVisible(val reorderVisible: Boolean) : AccountsEvent
    data class SetPeriod(val period: TimePeriod) : AccountsEvent
    data object SelectNextMonth : AccountsEvent
    data object SelectPreviousMonth : AccountsEvent
}
