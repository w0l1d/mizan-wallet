package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.data.db.entity.ZakatConfigEntity
import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import java.util.UUID
import javax.inject.Inject

class ZakatConfigMapper @Inject constructor() {
    fun ZakatConfigEntity.toDomain(): Either<String, ZakatConfig> = either {
        ensure(!isDeleted) { "ZakatConfig is deleted" }

        val nisab = when (nisabStandard.uppercase()) {
            "GOLD" -> NisabStandard.GOLD
            "SILVER" -> NisabStandard.SILVER
            else -> raise("Unknown nisab standard: $nisabStandard")
        }

        val tracking = when (trackingState.uppercase()) {
            "CONFIGURED" -> ZakatTrackingState.CONFIGURED
            "NISAB_REACHED" -> ZakatTrackingState.NISAB_REACHED
            "HAWL_COMPLETE" -> ZakatTrackingState.HAWL_COMPLETE
            "ZAKAT_PAID" -> ZakatTrackingState.ZAKAT_PAID
            else -> ZakatTrackingState.CONFIGURED
        }

        val price = when (priceSource.uppercase()) {
            "AUTOMATIC" -> PriceSource.AUTOMATIC
            "MANUAL" -> PriceSource.MANUAL
            else -> PriceSource.MANUAL
        }

        val parsedAccountIds = accountIdsSerialized
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }
            ?.map { AccountId(it) }
            ?: emptyList()

        val parsedDeductionAccountId = defaultDeductionAccountId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { AccountId(UUID.fromString(it)) }.getOrNull() }

        ZakatConfig(
            id = ZakatConfigId(id),
            name = NotBlankTrimmedString.from(name).bind(),
            nisabStandard = nisab,
            priceSource = price,
            manualGoldPricePerGram = manualGoldPricePerGram ?: 0.0,
            manualSilverPricePerGram = manualSilverPricePerGram ?: 0.0,
            physicalGoldGrams = physicalGoldGrams,
            physicalSilverGrams = physicalSilverGrams,
            deductions = deductions,
            accountIds = parsedAccountIds,
            defaultDeductionAccountId = parsedDeductionAccountId,
            hijriOffset = hijriOffset,
            trackingState = tracking,
            nisabReachedDate = nisabReachedDate,
            hawlStartDate = hawlStartDate,
            hawlEndDate = hawlEndDate,
            lastCheckDate = lastCheckDate,
            lastCheckWealth = lastCheckWealth,
            goldPricePerGram = goldPricePerGram,
            silverPricePerGram = silverPricePerGram,
            totalWealth = totalWealth,
            nisabAmount = nisabAmount,
            netZakatable = netZakatable,
            zakatDue = zakatDue,
            currency = AssetCode.from(currency).bind(),
            orderNum = orderId,
        )
    }

    fun ZakatConfig.toEntity(): ZakatConfigEntity {
        return ZakatConfigEntity(
            name = name.value,
            nisabStandard = nisabStandard.name,
            goldPricePerGram = goldPricePerGram,
            silverPricePerGram = silverPricePerGram,
            hawlStartDate = hawlStartDate,
            hawlEndDate = hawlEndDate,
            totalWealth = totalWealth,
            goldValueGrams = physicalGoldGrams,
            silverValueGrams = physicalSilverGrams,
            deductions = deductions,
            netZakatable = netZakatable,
            zakatDue = zakatDue,
            nisabAmount = nisabAmount,
            accountIdsSerialized = accountIds.joinToString(",") { it.value.toString() }
                .takeIf { it.isNotBlank() },
            currency = currency.code,
            orderId = orderNum,
            trackingState = trackingState.name,
            priceSource = priceSource.name,
            manualGoldPricePerGram = manualGoldPricePerGram,
            manualSilverPricePerGram = manualSilverPricePerGram,
            physicalGoldGrams = physicalGoldGrams,
            physicalSilverGrams = physicalSilverGrams,
            defaultDeductionAccountId = defaultDeductionAccountId?.value?.toString(),
            hijriOffset = hijriOffset,
            nisabReachedDate = nisabReachedDate,
            lastCheckDate = lastCheckDate,
            lastCheckWealth = lastCheckWealth,
            id = id.value,
            isSynced = true,
        )
    }
}
