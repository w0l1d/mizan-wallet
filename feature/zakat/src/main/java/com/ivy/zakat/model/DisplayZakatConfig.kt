package com.ivy.zakat.model

import androidx.compose.runtime.Immutable
import com.ivy.data.model.ZakatConfig

@Immutable
data class DisplayZakatConfig(
    val config: ZakatConfig,
    val totalPaid: Double,
    val remaining: Double,
    val progressPercent: Float,
    val isOverdue: Boolean,
)
