package com.ivy.zakat.usecase

import com.ivy.data.model.Expense
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatPayment
import com.ivy.data.model.ZakatPaymentId
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.data.repository.ZakatPaymentRepository
import com.ivy.zakat.model.AccountBalance
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class GenerateZakatExpenseUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val zakatConfigRepository: ZakatConfigRepository,
    private val zakatPaymentRepository: ZakatPaymentRepository,
) {
    @Suppress("ReturnCount")
    suspend fun generate(
        config: ZakatConfig,
        accountBalances: List<AccountBalance>,
    ): Boolean {
        val zakatDue = config.zakatDue
        if (zakatDue <= 0) return false

        val positiveAmount = PositiveDouble.from(zakatDue).getOrNull() ?: return false

        val deductionAccountId = config.defaultDeductionAccountId
            ?: config.accountIds.firstOrNull()
            ?: return false

        val description = buildDescription(config, accountBalances)
        val descriptionStr = NotBlankTrimmedString.from(description).getOrNull()

        val transactionId = TransactionId(UUID.randomUUID())
        val expense = Expense(
            id = transactionId,
            title = NotBlankTrimmedString.from("Zakat Payment").getOrNull(),
            description = descriptionStr,
            category = null,
            time = Instant.now(),
            settled = true,
            metadata = TransactionMetadata(
                recurringRuleId = null,
                paidForDateTime = null,
                loanId = null,
                loanRecordId = null,
            ),
            tags = emptyList(),
            value = PositiveValue(
                amount = positiveAmount,
                asset = config.currency,
            ),
            account = deductionAccountId,
        )

        transactionRepository.save(expense)

        val payment = ZakatPayment(
            id = ZakatPaymentId(UUID.randomUUID()),
            zakatConfigId = config.id,
            amount = zakatDue,
            dateTime = System.currentTimeMillis(),
            transactionId = transactionId.value,
            note = "Zakat expense — ${config.currency.code} ${"%.2f".format(zakatDue)}",
            orderNum = 0.0,
        )
        zakatPaymentRepository.save(payment)

        zakatConfigRepository.save(
            config.copy(trackingState = ZakatTrackingState.ZAKAT_PAID)
        )

        return true
    }

    private fun buildDescription(
        config: ZakatConfig,
        accountBalances: List<AccountBalance>,
    ): String = buildString {
        appendLine("Zakat Calculation Details")
        appendLine("========================")

        if (config.nisabReachedDate != null) {
            appendLine(
                "Hawl Period: ${
                    HijriCalendarUtils.formatEpochMillisAsGregorian(config.nisabReachedDate!!)
                } - ${
                    HijriCalendarUtils.formatEpochMillisAsGregorian(config.hawlEndDate)
                }"
            )
            val hijriStart = HijriCalendarUtils.epochMillisToHijri(
                config.nisabReachedDate!!,
                config.hijriOffset
            )
            val hijriEnd = HijriCalendarUtils.epochMillisToHijri(
                config.hawlEndDate,
                config.hijriOffset
            )
            appendLine(
                "Hijri: ${HijriCalendarUtils.formatHijri(hijriStart)} - " +
                    HijriCalendarUtils.formatHijri(hijriEnd)
            )
        }

        appendLine()
        appendLine(
            "Nisab Standard: ${
                if (config.nisabStandard == NisabStandard.GOLD) "Gold (85g)" else "Silver (595g)"
            }"
        )

        val metalName = if (config.nisabStandard == NisabStandard.GOLD) "Gold" else "Silver"
        val metalPrice = if (config.nisabStandard == NisabStandard.GOLD) {
            config.goldPricePerGram
        } else {
            config.silverPricePerGram
        }
        appendLine("$metalName Price/gram: ${"%.2f".format(metalPrice)} ${config.currency.code}")
        appendLine("Nisab Threshold: ${"%.2f".format(config.nisabAmount)} ${config.currency.code}")

        appendLine()
        appendLine("Account Balances:")
        accountBalances.forEach { acc ->
            appendLine("  ${acc.name}: ${"%.2f".format(acc.balance)} ${config.currency.code}")
        }

        if (config.physicalGoldGrams > 0) {
            appendLine(
                "Physical Gold: ${"%.2f".format(config.physicalGoldGrams)}g " +
                    "(${"%.2f".format(config.physicalGoldGrams * config.goldPricePerGram)} " +
                    "${config.currency.code})"
            )
        }
        if (config.physicalSilverGrams > 0) {
            appendLine(
                "Physical Silver: ${"%.2f".format(config.physicalSilverGrams)}g " +
                    "(${"%.2f".format(config.physicalSilverGrams * config.silverPricePerGram)} " +
                    "${config.currency.code})"
            )
        }

        appendLine()
        appendLine("Total Wealth: ${"%.2f".format(config.totalWealth)} ${config.currency.code}")
        if (config.deductions > 0) {
            appendLine(
                "Deductions: -${"%.2f".format(config.deductions)} ${config.currency.code}"
            )
        }
        appendLine(
            "Net Zakatable: ${"%.2f".format(config.netZakatable)} ${config.currency.code}"
        )
        appendLine("Zakat Rate: 2.5%")
        appendLine("Zakat Due: ${"%.2f".format(config.zakatDue)} ${config.currency.code}")
    }
}
