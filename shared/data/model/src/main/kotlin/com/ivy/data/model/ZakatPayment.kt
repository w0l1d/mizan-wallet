package com.ivy.data.model

import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class ZakatPaymentId(override val value: UUID) : UniqueId

@Suppress("DataClassTypedIDs")
data class ZakatPayment(
    override val id: ZakatPaymentId,
    val zakatConfigId: ZakatConfigId,
    val amount: Double,
    val dateTime: Long,
    val transactionId: UUID?,
    val note: String?,
    override val orderNum: Double,
) : Identifiable<ZakatPaymentId>, Reorderable
