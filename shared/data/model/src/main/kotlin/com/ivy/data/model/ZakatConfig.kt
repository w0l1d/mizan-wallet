package com.ivy.data.model

import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class ZakatConfigId(override val value: UUID) : UniqueId

enum class NisabStandard { GOLD, SILVER }

enum class ZakatTrackingState {
    CONFIGURED,
    NISAB_REACHED,
    HAWL_COMPLETE,
    ZAKAT_PAID
}

enum class PriceSource { AUTOMATIC, MANUAL }

data class ZakatConfig(
    override val id: ZakatConfigId,
    val name: NotBlankTrimmedString,
    val nisabStandard: NisabStandard,
    val priceSource: PriceSource,
    val manualGoldPricePerGram: Double,
    val manualSilverPricePerGram: Double,
    val physicalGoldGrams: Double,
    val physicalSilverGrams: Double,
    val deductions: Double,
    val accountIds: List<AccountId>,
    @Suppress("DataClassTypedIDs") val defaultDeductionAccountId: AccountId?,
    val hijriOffset: Int,

    // Tracking state
    val trackingState: ZakatTrackingState,
    val nisabReachedDate: Long?,
    val hawlStartDate: Long,
    val hawlEndDate: Long,
    val lastCheckDate: Long?,
    val lastCheckWealth: Double?,

    // Computed snapshots (persisted for offline display)
    val goldPricePerGram: Double,
    val silverPricePerGram: Double,
    val totalWealth: Double,
    val nisabAmount: Double,
    val netZakatable: Double,
    val zakatDue: Double,

    val currency: AssetCode,
    override val orderNum: Double,
) : Identifiable<ZakatConfigId>, Reorderable
