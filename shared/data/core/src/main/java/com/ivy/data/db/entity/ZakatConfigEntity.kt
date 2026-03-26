package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "zakat_configs")
data class ZakatConfigEntity(
    @SerialName("name")
    val name: String,
    @SerialName("nisabStandard")
    val nisabStandard: String,
    @SerialName("goldPricePerGram")
    val goldPricePerGram: Double,
    @SerialName("silverPricePerGram")
    val silverPricePerGram: Double,
    @SerialName("hawlStartDate")
    val hawlStartDate: Long,
    @SerialName("hawlEndDate")
    val hawlEndDate: Long,
    @SerialName("totalWealth")
    val totalWealth: Double,
    @SerialName("goldValueGrams")
    val goldValueGrams: Double,
    @SerialName("silverValueGrams")
    val silverValueGrams: Double,
    @SerialName("deductions")
    val deductions: Double,
    @SerialName("netZakatable")
    val netZakatable: Double,
    @SerialName("zakatDue")
    val zakatDue: Double,
    @SerialName("nisabAmount")
    val nisabAmount: Double,
    @SerialName("accountIdsSerialized")
    val accountIdsSerialized: String?,
    @SerialName("currency")
    val currency: String,

    @SerialName("orderId")
    val orderId: Double,

    // New fields for redesign
    @ColumnInfo(defaultValue = "CONFIGURED")
    @SerialName("trackingState")
    val trackingState: String = "CONFIGURED",

    @ColumnInfo(defaultValue = "MANUAL")
    @SerialName("priceSource")
    val priceSource: String = "MANUAL",

    @SerialName("manualGoldPricePerGram")
    val manualGoldPricePerGram: Double? = null,

    @SerialName("manualSilverPricePerGram")
    val manualSilverPricePerGram: Double? = null,

    @ColumnInfo(defaultValue = "0.0")
    @SerialName("physicalGoldGrams")
    val physicalGoldGrams: Double = 0.0,

    @ColumnInfo(defaultValue = "0.0")
    @SerialName("physicalSilverGrams")
    val physicalSilverGrams: Double = 0.0,

    @SerialName("defaultDeductionAccountId")
    val defaultDeductionAccountId: String? = null,

    @ColumnInfo(defaultValue = "0")
    @SerialName("hijriOffset")
    val hijriOffset: Int = 0,

    @SerialName("nisabReachedDate")
    val nisabReachedDate: Long? = null,

    @SerialName("lastCheckDate")
    val lastCheckDate: Long? = null,

    @SerialName("lastCheckWealth")
    val lastCheckWealth: Double? = null,

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
