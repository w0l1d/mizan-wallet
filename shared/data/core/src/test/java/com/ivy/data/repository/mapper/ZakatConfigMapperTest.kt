package com.ivy.data.repository.mapper

import arrow.core.Either
import com.ivy.data.db.entity.ZakatConfigEntity
import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.UUID

/**
 * Verifies [ZakatConfigMapper] bidirectional mapping between [ZakatConfigEntity]
 * (Room/serialized form) and [ZakatConfig] (domain model).
 */
class ZakatConfigMapperTest {

    private val mapper = ZakatConfigMapper()

    @Test
    fun `toDomain maps all fields correctly from entity`() {
        val accountId = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val entity = baseEntity(
            id = configId,
            name = "My Zakat",
            nisabStandard = "GOLD",
            accountIdsSerialized = accountId.toString(),
            defaultDeductionAccountId = accountId.toString(),
            hijriOffset = 1,
            trackingState = "HAWL_COMPLETE",
            priceSource = "AUTOMATIC",
            manualGoldPricePerGram = 260.0,
            manualSilverPricePerGram = 4.5,
            physicalGoldGrams = 50.0,
            physicalSilverGrams = 200.0,
            deductions = 5_000.0,
            nisabReachedDate = 1_700_000_000_000L,
            hawlStartDate = 1_700_000_000_000L,
            hawlEndDate = 1_700_030_000_000L,
            lastCheckDate = 1_700_060_000_000L,
            lastCheckWealth = 55_000.0,
            goldPricePerGram = 260.0,
            silverPricePerGram = 4.5,
            totalWealth = 68_000.0,
            nisabAmount = 22_100.0,
            netZakatable = 63_000.0,
            zakatDue = 1_575.0,
            currency = "USD",
            orderId = 2.0,
        )

        val result = with(mapper) { entity.toDomain() }

        val config = result.getOrNull().shouldNotBeNull()
        config.id shouldBe ZakatConfigId(configId)
        config.name.value shouldBe "My Zakat"
        config.nisabStandard shouldBe NisabStandard.GOLD
        config.trackingState shouldBe ZakatTrackingState.HAWL_COMPLETE
        config.priceSource shouldBe PriceSource.AUTOMATIC
        config.manualGoldPricePerGram shouldBe 260.0.plusOrMinus(0.001)
        config.manualSilverPricePerGram shouldBe 4.5.plusOrMinus(0.001)
        config.physicalGoldGrams shouldBe 50.0.plusOrMinus(0.001)
        config.physicalSilverGrams shouldBe 200.0.plusOrMinus(0.001)
        config.deductions shouldBe 5_000.0.plusOrMinus(0.001)
        config.hijriOffset shouldBe 1
        config.nisabReachedDate shouldBe 1_700_000_000_000L
        config.hawlStartDate shouldBe 1_700_000_000_000L
        config.hawlEndDate shouldBe 1_700_030_000_000L
        config.lastCheckDate shouldBe 1_700_060_000_000L
        config.lastCheckWealth shouldBe 55_000.0
        config.goldPricePerGram shouldBe 260.0
        config.silverPricePerGram shouldBe 4.5.plusOrMinus(0.001)
        config.totalWealth shouldBe 68_000.0
        config.nisabAmount shouldBe 22_100.0
        config.netZakatable shouldBe 63_000.0
        config.zakatDue shouldBe 1_575.0
        config.currency shouldBe AssetCode.USD
        config.orderNum shouldBe 2.0
        config.accountIds shouldHaveSize 1
        config.accountIds.first().value shouldBe accountId
        config.defaultDeductionAccountId.shouldNotBeNull()
        config.defaultDeductionAccountId!!.value shouldBe accountId
    }

    @Test
    fun `toDomain maps SILVER nisab standard`() {
        val entity = baseEntity(nisabStandard = "SILVER")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.nisabStandard shouldBe NisabStandard.SILVER
    }

    @Test
    fun `toDomain returns error for unknown nisab standard`() {
        val entity = baseEntity(nisabStandard = "PLATINUM")
        val result = with(mapper) { entity.toDomain() }
        result shouldBe Either.Left("Unknown nisab standard: PLATINUM")
    }

    @Test
    fun `toDomain maps CONFIGURED tracking state`() {
        val entity = baseEntity(trackingState = "CONFIGURED")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.trackingState shouldBe ZakatTrackingState.CONFIGURED
    }

    @Test
    fun `toDomain maps NISAB_REACHED tracking state`() {
        val entity = baseEntity(trackingState = "NISAB_REACHED")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.trackingState shouldBe ZakatTrackingState.NISAB_REACHED
    }

    @Test
    fun `toDomain maps ZAKAT_PAID tracking state`() {
        val entity = baseEntity(trackingState = "ZAKAT_PAID")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.trackingState shouldBe ZakatTrackingState.ZAKAT_PAID
    }

    @Test
    fun `toDomain defaults unknown tracking state to CONFIGURED`() {
        val entity = baseEntity(trackingState = "INVALID_STATE")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.trackingState shouldBe ZakatTrackingState.CONFIGURED
    }

    @Test
    fun `toDomain maps AUTOMATIC price source`() {
        val entity = baseEntity(priceSource = "AUTOMATIC")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.priceSource shouldBe PriceSource.AUTOMATIC
    }

    @Test
    fun `toDomain maps MANUAL price source`() {
        val entity = baseEntity(priceSource = "MANUAL")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.priceSource shouldBe PriceSource.MANUAL
    }

    @Test
    fun `toDomain defaults unknown price source to MANUAL`() {
        val entity = baseEntity(priceSource = "HYBRID")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.priceSource shouldBe PriceSource.MANUAL
    }

    @Test
    fun `toDomain parses multiple account IDs`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val entity = baseEntity(accountIdsSerialized = "$id1,$id2")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.accountIds shouldHaveSize 2
        config.accountIds.map { it.value } shouldBe listOf(id1, id2)
    }

    @Test
    fun `toDomain skips invalid UUIDs in account IDs and keeps valid ones`() {
        val validId = UUID.randomUUID()
        val entity = baseEntity(
            accountIdsSerialized = "$validId,not-a-uuid,also-invalid"
        )
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.accountIds shouldHaveSize 1
        config.accountIds.first().value shouldBe validId
    }

    @Test
    fun `toDomain returns empty list for null account IDs`() {
        val entity = baseEntity(accountIdsSerialized = null)
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.accountIds.shouldBeEmpty()
    }

    @Test
    fun `toDomain returns empty list for blank account IDs`() {
        val entity = baseEntity(accountIdsSerialized = "   ")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.accountIds.shouldBeEmpty()
    }

    @Test
    fun `toDomain returns error for deleted entity`() {
        val entity = baseEntity(isDeleted = true, name = "Deleted Config")
        val result = with(mapper) { entity.toDomain() }
        result shouldBe Either.Left("ZakatConfig is deleted")
    }

    @Test
    fun `toDomain uses null defaultDeductionAccountId when blank`() {
        val entity = baseEntity(defaultDeductionAccountId = "   ")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.defaultDeductionAccountId.shouldBeNull()
    }

    @Test
    fun `toDomain uses null defaultDeductionAccountId when null`() {
        val entity = baseEntity(defaultDeductionAccountId = null)
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.defaultDeductionAccountId.shouldBeNull()
    }

    @Test
    fun `toDomain skips invalid defaultDeductionAccountId UUID`() {
        val entity = baseEntity(defaultDeductionAccountId = "not-a-valid-uuid")
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.defaultDeductionAccountId.shouldBeNull()
    }

    @Test
    fun `toDomain defaults null manualGoldPricePerGram to zero`() {
        val entity = baseEntity(manualGoldPricePerGram = null)
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.manualGoldPricePerGram shouldBe 0.0
    }

    @Test
    fun `toDomain defaults null manualSilverPricePerGram to zero`() {
        val entity = baseEntity(manualSilverPricePerGram = null)
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        config.manualSilverPricePerGram shouldBe 0.0
    }

    @Test
    fun `toEntity serializes account IDs as comma-separated UUIDs`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val config = baseConfig(
            accountIds = listOf(AccountId(id1), AccountId(id2)),
        )
        val entity = with(mapper) { config.toEntity() }
        entity.accountIdsSerialized.shouldNotBeNull()
        entity.accountIdsSerialized shouldBe "$id1,$id2"
    }

    @Test
    fun `toEntity produces null accountIdsSerialized for empty list`() {
        val config = baseConfig(accountIds = emptyList())
        val entity = with(mapper) { config.toEntity() }
        entity.accountIdsSerialized.shouldBeNull()
    }

    @Test
    fun `toDomain then toEntity round-trips preserving key fields`() {
        val accountId = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val entity = baseEntity(
            id = configId,
            name = "Round Trip",
            nisabStandard = "SILVER",
            accountIdsSerialized = accountId.toString(),
            defaultDeductionAccountId = accountId.toString(),
            hijriOffset = -1,
            trackingState = "NISAB_REACHED",
            priceSource = "MANUAL",
            manualGoldPricePerGram = 255.0,
            manualSilverPricePerGram = 3.2,
            physicalGoldGrams = 10.0,
            physicalSilverGrams = 50.0,
            deductions = 1_000.0,
            nisabReachedDate = 1_710_000_000_000L,
            hawlStartDate = 1_710_000_000_000L,
            hawlEndDate = 1_710_030_000_000L,
            lastCheckDate = 1_710_060_000_000L,
            lastCheckWealth = 30_000.0,
            goldPricePerGram = 255.0,
            silverPricePerGram = 3.2,
            totalWealth = 32_550.0,
            nisabAmount = 1_904.0,
            netZakatable = 31_550.0,
            zakatDue = 788.75,
            currency = "SAR",
            orderId = 1.0,
        )
        val config = with(mapper) { entity.toDomain() }.getOrNull().shouldNotBeNull()
        val roundTripped = with(mapper) { config.toEntity() }
        roundTripped.name shouldBe entity.name
        roundTripped.nisabStandard shouldBe entity.nisabStandard
        roundTripped.trackingState shouldBe entity.trackingState
        roundTripped.priceSource shouldBe entity.priceSource
        roundTripped.goldPricePerGram shouldBe entity.goldPricePerGram
        roundTripped.silverPricePerGram shouldBe entity.silverPricePerGram
        roundTripped.totalWealth shouldBe entity.totalWealth
        roundTripped.zakatDue shouldBe entity.zakatDue
        roundTripped.nisabAmount shouldBe entity.nisabAmount
        roundTripped.currency shouldBe entity.currency
        roundTripped.orderId shouldBe entity.orderId
        roundTripped.hijriOffset shouldBe entity.hijriOffset
        roundTripped.physicalGoldGrams shouldBe entity.physicalGoldGrams
        roundTripped.physicalSilverGrams shouldBe entity.physicalSilverGrams
        roundTripped.deductions shouldBe entity.deductions
        roundTripped.accountIdsSerialized shouldBe entity.accountIdsSerialized
        roundTripped.defaultDeductionAccountId shouldBe entity.defaultDeductionAccountId
    }

    // --- helpers ---------------------------------------------------------------------------

    @Suppress("LongParameterList")
    private fun baseEntity(
        id: UUID = UUID.randomUUID(),
        name: String = "Test",
        nisabStandard: String = "GOLD",
        accountIdsSerialized: String? = UUID.randomUUID().toString(),
        defaultDeductionAccountId: String? = UUID.randomUUID().toString(),
        hijriOffset: Int = 0,
        trackingState: String = "CONFIGURED",
        priceSource: String = "MANUAL",
        manualGoldPricePerGram: Double? = 250.0,
        manualSilverPricePerGram: Double? = 3.0,
        physicalGoldGrams: Double = 0.0,
        physicalSilverGrams: Double = 0.0,
        deductions: Double = 0.0,
        nisabReachedDate: Long? = null,
        hawlStartDate: Long = 0L,
        hawlEndDate: Long = 0L,
        lastCheckDate: Long? = null,
        lastCheckWealth: Double? = null,
        goldPricePerGram: Double = 0.0,
        silverPricePerGram: Double = 0.0,
        totalWealth: Double = 0.0,
        nisabAmount: Double = 0.0,
        netZakatable: Double = 0.0,
        zakatDue: Double = 0.0,
        currency: String = "USD",
        orderId: Double = 0.0,
        isDeleted: Boolean = false,
    ): ZakatConfigEntity = ZakatConfigEntity(
        id = id,
        name = name,
        nisabStandard = nisabStandard,
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
        accountIdsSerialized = accountIdsSerialized,
        currency = currency,
        orderId = orderId,
        trackingState = trackingState,
        priceSource = priceSource,
        manualGoldPricePerGram = manualGoldPricePerGram,
        manualSilverPricePerGram = manualSilverPricePerGram,
        physicalGoldGrams = physicalGoldGrams,
        physicalSilverGrams = physicalSilverGrams,
        defaultDeductionAccountId = defaultDeductionAccountId,
        hijriOffset = hijriOffset,
        nisabReachedDate = nisabReachedDate,
        lastCheckDate = lastCheckDate,
        lastCheckWealth = lastCheckWealth,
        isSynced = true,
        isDeleted = isDeleted,
    )

    @Suppress("LongParameterList")
    private fun baseConfig(
        accountIds: List<AccountId> = listOf(AccountId(UUID.randomUUID())),
    ): ZakatConfig = ZakatConfig(
        id = ZakatConfigId(UUID.randomUUID()),
        name = NotBlankTrimmedString.unsafe("Test"),
        nisabStandard = NisabStandard.GOLD,
        priceSource = PriceSource.MANUAL,
        manualGoldPricePerGram = 250.0,
        manualSilverPricePerGram = 3.0,
        physicalGoldGrams = 0.0,
        physicalSilverGrams = 0.0,
        deductions = 0.0,
        accountIds = accountIds,
        defaultDeductionAccountId = null,
        hijriOffset = 0,
        trackingState = ZakatTrackingState.CONFIGURED,
        nisabReachedDate = null,
        hawlStartDate = 0L,
        hawlEndDate = 0L,
        lastCheckDate = null,
        lastCheckWealth = null,
        goldPricePerGram = 0.0,
        silverPricePerGram = 0.0,
        totalWealth = 0.0,
        nisabAmount = 0.0,
        netZakatable = 0.0,
        zakatDue = 0.0,
        currency = AssetCode.USD,
        orderNum = 0.0,
    )
}
