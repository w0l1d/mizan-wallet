package com.ivy.zakat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.data.repository.ZakatPaymentRepository
import com.ivy.navigation.Navigation
import com.ivy.navigation.ZakatDetailScreen
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.zakat.model.AccountBalance
import com.ivy.zakat.usecase.CheckZakatNisabUseCase
import com.ivy.zakat.usecase.GenerateZakatExpenseUseCase
import com.ivy.zakat.usecase.HijriCalendarUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class ZakatViewModel @Inject constructor(
    private val zakatConfigRepository: ZakatConfigRepository,
    private val zakatPaymentRepository: ZakatPaymentRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val checkZakatNisabUseCase: CheckZakatNisabUseCase,
    private val generateZakatExpenseUseCase: GenerateZakatExpenseUseCase,
    private val nav: Navigation,
) : ComposeViewModel<ZakatScreenState, ZakatScreenEvent>() {

    private var currentConfig: ZakatConfig? = null
    private val hasConfig = mutableStateOf(false)
    private val baseCurrency = mutableStateOf("")
    private val trackingState = mutableStateOf(ZakatTrackingState.CONFIGURED)
    private val totalWealth = mutableDoubleStateOf(0.0)
    private val nisabAmount = mutableDoubleStateOf(0.0)
    private val isAboveNisab = mutableStateOf(false)
    private val nisabReachedDateFormatted = mutableStateOf<String?>(null)
    private val nisabStandardLabel = mutableStateOf("")
    private val metalPriceLabel = mutableStateOf("")
    private val hawlStartFormatted = mutableStateOf<String?>(null)
    private val hawlEndFormatted = mutableStateOf<String?>(null)
    private val hawlDaysRemaining = mutableIntStateOf(0)
    private val hawlDaysTotal = mutableIntStateOf(1)
    private val hawlProgress = mutableFloatStateOf(0f)
    private val netZakatable = mutableDoubleStateOf(0.0)
    private val zakatDue = mutableDoubleStateOf(0.0)
    private val deductions = mutableDoubleStateOf(0.0)
    private val physicalGoldValue = mutableDoubleStateOf(0.0)
    private val physicalSilverValue = mutableDoubleStateOf(0.0)
    private val accountBalances =
        mutableStateOf<ImmutableList<AccountBalance>>(persistentListOf())
    private val todayHijriFormatted = mutableStateOf("")
    private val hijriOffset = mutableIntStateOf(0)
    private val totalPaid = mutableDoubleStateOf(0.0)
    private val remaining = mutableDoubleStateOf(0.0)
    private val isLoading = mutableStateOf(true)

    @Composable
    override fun uiState(): ZakatScreenState {
        LaunchedEffect(Unit) {
            start()
        }

        return ZakatScreenState(
            hasConfig = hasConfig.value,
            baseCurrency = baseCurrency.value,
            trackingState = trackingState.value,
            totalWealth = totalWealth.doubleValue,
            nisabAmount = nisabAmount.doubleValue,
            isAboveNisab = isAboveNisab.value,
            nisabReachedDateFormatted = nisabReachedDateFormatted.value,
            nisabStandardLabel = nisabStandardLabel.value,
            metalPriceLabel = metalPriceLabel.value,
            hawlStartFormatted = hawlStartFormatted.value,
            hawlEndFormatted = hawlEndFormatted.value,
            hawlDaysRemaining = hawlDaysRemaining.intValue,
            hawlDaysTotal = hawlDaysTotal.intValue,
            hawlProgress = hawlProgress.floatValue,
            netZakatable = netZakatable.doubleValue,
            zakatDue = zakatDue.doubleValue,
            deductions = deductions.doubleValue,
            physicalGoldValue = physicalGoldValue.doubleValue,
            physicalSilverValue = physicalSilverValue.doubleValue,
            accountBalances = accountBalances.value,
            todayHijriFormatted = todayHijriFormatted.value,
            hijriOffset = hijriOffset.intValue,
            totalPaid = totalPaid.doubleValue,
            remaining = remaining.doubleValue,
            isLoading = isLoading.value,
        )
    }

    override fun onEvent(event: ZakatScreenEvent) {
        when (event) {
            ZakatScreenEvent.OnSetup -> {
                nav.navigateTo(ZakatDetailScreen(zakatConfigId = null))
            }
            ZakatScreenEvent.OnOpenSettings -> {
                val id = currentConfig?.id?.value
                nav.navigateTo(ZakatDetailScreen(zakatConfigId = id))
            }
            ZakatScreenEvent.OnRefresh -> {
                viewModelScope.launch { refreshCheck() }
            }
            ZakatScreenEvent.OnPayZakat -> {
                viewModelScope.launch { payZakat() }
            }
        }
    }

    private suspend fun start() {
        baseCurrency.value = baseCurrencyAct(Unit)
        updateHijriDate(0)

        val configs = zakatConfigRepository.findAll()
        val config = configs.firstOrNull()

        if (config == null) {
            hasConfig.value = false
            isLoading.value = false
            return
        }

        hasConfig.value = true
        currentConfig = config
        hijriOffset.intValue = config.hijriOffset
        updateHijriDate(config.hijriOffset)

        refreshCheck()
    }

    private suspend fun refreshCheck() {
        val config = currentConfig ?: return
        isLoading.value = true

        val result = checkZakatNisabUseCase.check(config)
        val c = result.config
        currentConfig = c

        trackingState.value = c.trackingState
        totalWealth.doubleValue = c.totalWealth
        nisabAmount.doubleValue = c.nisabAmount
        isAboveNisab.value = c.netZakatable >= c.nisabAmount && c.nisabAmount > 0
        netZakatable.doubleValue = c.netZakatable
        zakatDue.doubleValue = c.zakatDue
        deductions.doubleValue = c.deductions
        physicalGoldValue.doubleValue = c.physicalGoldGrams * c.goldPricePerGram
        physicalSilverValue.doubleValue = c.physicalSilverGrams * c.silverPricePerGram
        accountBalances.value = result.accountBalances.toImmutableList()

        // Nisab standard label
        nisabStandardLabel.value = when (c.nisabStandard) {
            NisabStandard.GOLD -> "Gold (85g)"
            NisabStandard.SILVER -> "Silver (595g)"
        }

        // Metal price label
        val price = when (c.nisabStandard) {
            NisabStandard.GOLD -> c.goldPricePerGram
            NisabStandard.SILVER -> c.silverPricePerGram
        }
        metalPriceLabel.value = "${"%.2f".format(price)} ${baseCurrency.value}/g"

        // Nisab reached date
        val reachedDate = c.nisabReachedDate
        if (reachedDate != null) {
            val hijri = HijriCalendarUtils.epochMillisToHijri(reachedDate, c.hijriOffset)
            nisabReachedDateFormatted.value = HijriCalendarUtils.formatHijri(hijri)
        } else {
            nisabReachedDateFormatted.value = null
        }

        // Hawl progress
        if (reachedDate != null) {
            val startHijri = HijriCalendarUtils.epochMillisToHijri(reachedDate, c.hijriOffset)
            hawlStartFormatted.value = HijriCalendarUtils.formatHijri(startHijri) +
                " (${HijriCalendarUtils.formatEpochMillisAsGregorian(reachedDate)})"

            val endHijri = HijriCalendarUtils.epochMillisToHijri(c.hawlEndDate, c.hijriOffset)
            hawlEndFormatted.value = HijriCalendarUtils.formatHijri(endHijri) +
                " (${HijriCalendarUtils.formatEpochMillisAsGregorian(c.hawlEndDate)})"

            val daysRemaining = HijriCalendarUtils.daysRemainingInHawl(
                reachedDate,
                c.hijriOffset
            )
            val total = HijriCalendarUtils.totalHawlDays(reachedDate, c.hijriOffset)
            val elapsed = HijriCalendarUtils.daysElapsedInHawl(reachedDate)

            hawlDaysRemaining.intValue = daysRemaining
            hawlDaysTotal.intValue = total
            hawlProgress.floatValue = if (total > 0) {
                (elapsed.toFloat() / total).coerceIn(0f, 1f)
            } else {
                0f
            }
        } else {
            hawlStartFormatted.value = null
            hawlEndFormatted.value = null
            hawlDaysRemaining.intValue = 0
            hawlDaysTotal.intValue = 1
            hawlProgress.floatValue = 0f
        }

        // Payments
        val payments = zakatPaymentRepository.findByConfigId(c.id)
        totalPaid.doubleValue = payments.sumOf { it.amount }
        remaining.doubleValue = (c.zakatDue - totalPaid.doubleValue).coerceAtLeast(0.0)

        isLoading.value = false
    }

    private suspend fun payZakat() {
        val config = currentConfig ?: return
        val balances = accountBalances.value
        generateZakatExpenseUseCase.generate(config, balances)
        refreshCheck()
    }

    private fun updateHijriDate(offset: Int) {
        try {
            val hijri = HijriCalendarUtils.todayHijri(offset)
            todayHijriFormatted.value = HijriCalendarUtils.formatHijri(hijri)
        } catch (_: Exception) {
            todayHijriFormatted.value = "Hijri calendar unavailable"
        }
    }
}
