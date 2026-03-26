package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "zakat_payments")
data class ZakatPaymentEntity(
    @SerialName("zakatConfigId")
    @Serializable(with = KSerializerUUID::class)
    val zakatConfigId: UUID,
    @SerialName("amount")
    val amount: Double,
    @SerialName("dateTime")
    val dateTime: Long,
    @SerialName("transactionId")
    @Serializable(with = KSerializerUUID::class)
    val transactionId: UUID? = null,
    @SerialName("note")
    val note: String? = null,

    @SerialName("orderId")
    val orderId: Double,

    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isSynced")
    val isSynced: Boolean = false,
    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isDeleted")
    val isDeleted: Boolean = false,

    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID()
)
