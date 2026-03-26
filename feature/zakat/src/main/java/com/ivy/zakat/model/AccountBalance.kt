package com.ivy.zakat.model

import androidx.compose.runtime.Immutable
import com.ivy.data.model.AccountId

@Suppress("DataClassTypedIDs")
@Immutable
data class AccountBalance(
    val accountId: AccountId,
    val name: String,
    val balance: Double,
    val currency: String,
    val selected: Boolean,
)
