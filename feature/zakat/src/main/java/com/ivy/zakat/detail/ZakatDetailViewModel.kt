package com.ivy.zakat.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.AccountId
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatTrackingState
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.ZakatConfigRepository
import com.ivy.data.repository.ZakatPaymentRepository
import com.ivy.navigation.Navigation
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.zakat.model.AccountBalance
import com.ivy.zakat.usecase.FetchMetalPricesUseCase
import com.ivy.zakat.usecase.HijriCalendarUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Stable
@HiltViewModel
class ZakatDetailViewModel @Inject constructor(
    private val zakatConfigRepository: ZakatConfigRepository,
    private val zakatPaymentRepository: ZakatPaymentRepository,
    private val accountRepository: AccountRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val fetchMetalPricesUseCase: FetchMetalPricesUseCase,
    private val nav: Navigation,
) : ComposeViewModel<ZakatDetailScreenState, ZakatDetailScreenEvent>() {

    private var configId: ZakatConfigId? = null
    private val baseCurrency = mutableStateOf("")
    private val name = mutableStateOf("")
    private val nisabStandard = mutableStateOf<NisabStandard?>(null)
    private val priceSource = mutableStateOf(PriceSource.MANUAL)
    private val manualGoldPricePerGram = mutableStateOf("")
    private val manualSilverPricePerGram = mutableStateOf("")
    private val autoGoldPricePerGram = mutableStateOf<Double?>(null)
    private val autoSilverPricePerGram = mutableStateOf<Double?>(null)
    private val physicalGoldGrams = mutableStateOf("")
    private val physicalSilverGrams = mutableStateOf("")
    private val deductions = mutableStateOf("")
    private val accounts = mutableStateOf<ImmutableList<AccountBalance>>(persistentListOf())
    private val selectedAccountIds = mutableStateOf<Set<AccountId>>(emptySet())
    private val defaultDeductionAccountId = mutableStateOf<AccountId?>(null)
    private val hijriOffset = mutableStateOf("")

    fun setConfigId(id: UUID?) {
        configId = id?.let { ZakatConfigId(it) }
    }

    @Composable
    override fun uiState(): ZakatDetailScreenState {
        LaunchedEffect(Unit) {
            start()
        }

        return ZakatDetailScreenState(
            isEditMode = configId != null,
            baseCurrency = baseCurrency.value,
            name = name.value,
            nisabStandard = nisabStandard.value,
            priceSource = priceSource.value,
            manualGoldPricePerGram = manualGoldPricePerGram.value,
            manualSilverPricePerGram = manualSilverPricePerGram.value,
            autoGoldPricePerGram = autoGoldPricePerGram.value,
            autoSilverPricePerGram = autoSilverPricePerGram.value,
            physicalGoldGrams = physicalGoldGrams.value,
            physicalSilverGrams = physicalSilverGrams.value,
            deductions = deductions.value,
            accounts = accounts.value,
            defaultDeductionAccountId = defaultDeductionAccountId.value?.value?.toString(),
            hijriOffset = hijriOffset.value,
        )
    }

    override fun onEvent(event: ZakatDetailScreenEvent) {
        when (event) {
            is ZakatDetailScreenEvent.OnNameChanged -> name.value = event.name
            is ZakatDetailScreenEvent.OnNisabStandardChanged ->
                nisabStandard.value = event.standard
            is ZakatDetailScreenEvent.OnPriceSourceChanged ->
                priceSource.value = event.source
            is ZakatDetailScreenEvent.OnManualGoldPriceChanged ->
                manualGoldPricePerGram.value = event.price
            is ZakatDetailScreenEvent.OnManualSilverPriceChanged ->
                manualSilverPricePerGram.value = event.price
            is ZakatDetailScreenEvent.OnPhysicalGoldGramsChanged ->
                physicalGoldGrams.value = event.grams
            is ZakatDetailScreenEvent.OnPhysicalSilverGramsChanged ->
                physicalSilverGrams.value = event.grams
            is ZakatDetailScreenEvent.OnDeductionsChanged ->
                deductions.value = event.deductions
            is ZakatDetailScreenEvent.OnAccountToggled ->
                toggleAccount(event.accountId)
            is ZakatDetailScreenEvent.OnDefaultDeductionAccountChanged ->
                defaultDeductionAccountId.value = event.accountId
            is ZakatDetailScreenEvent.OnHijriOffsetChanged ->
                hijriOffset.value = event.offset
            ZakatDetailScreenEvent.OnSave -> save()
            ZakatDetailScreenEvent.OnDelete -> delete()
            ZakatDetailScreenEvent.OnRefreshPrices -> {
                viewModelScope.launch { fetchAutoPrices() }
            }
        }
    }

    private fun start() {
        viewModelScope.launch {
            baseCurrency.value = baseCurrencyAct(Unit)
            loadAccounts()
            fetchAutoPrices()

            val id = configId
            if (id != null) {
                val config = zakatConfigRepository.findById(id)
                if (config != null) {
                    loadConfig(config)
                }
            }
        }
    }

    private suspend fun loadAccounts() {
        val allAccounts = accountRepository.findAll()
        accounts.value = allAccounts.map { account ->
            AccountBalance(
                accountId = account.id,
                name = account.name.value,
                balance = 0.0,
                currency = account.asset.code,
                selected = selectedAccountIds.value.isEmpty() ||
                    selectedAccountIds.value.contains(account.id),
            )
        }.toImmutableList()
    }

    private suspend fun fetchAutoPrices() {
        val prices = fetchMetalPricesUseCase.fetch()
        autoGoldPricePerGram.value = prices.goldPricePerGram
        autoSilverPricePerGram.value = prices.silverPricePerGram
    }

    private fun loadConfig(config: ZakatConfig) {
        name.value = config.name.value
        nisabStandard.value = config.nisabStandard
        priceSource.value = config.priceSource

        manualGoldPricePerGram.value = if (config.manualGoldPricePerGram > 0) {
            config.manualGoldPricePerGram.toString()
        } else {
            ""
        }
        manualSilverPricePerGram.value = if (config.manualSilverPricePerGram > 0) {
            config.manualSilverPricePerGram.toString()
        } else {
            ""
        }
        physicalGoldGrams.value = if (config.physicalGoldGrams > 0) {
            config.physicalGoldGrams.toString()
        } else {
            ""
        }
        physicalSilverGrams.value = if (config.physicalSilverGrams > 0) {
            config.physicalSilverGrams.toString()
        } else {
            ""
        }
        deductions.value = if (config.deductions > 0) config.deductions.toString() else ""
        hijriOffset.value = if (config.hijriOffset != 0) config.hijriOffset.toString() else ""

        selectedAccountIds.value = config.accountIds.toSet()
        defaultDeductionAccountId.value = config.defaultDeductionAccountId

        accounts.value = accounts.value.map { acc ->
            acc.copy(
                selected = config.accountIds.isEmpty() ||
                    selectedAccountIds.value.contains(acc.accountId)
            )
        }.toImmutableList()
    }

    private fun toggleAccount(accountId: AccountId) {
        val current = selectedAccountIds.value.toMutableSet()
        if (current.contains(accountId)) {
            current.remove(accountId)
        } else {
            current.add(accountId)
        }
        selectedAccountIds.value = current
        accounts.value = accounts.value.map { acc ->
            acc.copy(selected = current.isEmpty() || current.contains(acc.accountId))
        }.toImmutableList()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun save() {
        viewModelScope.launch {
            val nameStr = name.value.trim()
            if (nameStr.isBlank() || nisabStandard.value == null) return@launch

            val parsedName = NotBlankTrimmedString.from(nameStr).getOrNull() ?: return@launch
            val currency = AssetCode.from(baseCurrency.value).getOrNull() ?: return@launch

            val id = configId ?: ZakatConfigId(UUID.randomUUID())
            val existingConfig = if (configId != null) {
                zakatConfigRepository.findById(id)
            } else {
                null
            }

            val orderNum = existingConfig?.orderNum
                ?: (zakatConfigRepository.findMaxOrderNum() + 1.0)

            val offset = hijriOffset.value.toIntOrNull() ?: 0
            val now = System.currentTimeMillis()
            val hawlEnd = if (existingConfig?.nisabReachedDate != null) {
                HijriCalendarUtils.hawlEndDateMillis(
                    existingConfig.nisabReachedDate!!,
                    offset
                )
            } else {
                HijriCalendarUtils.hawlEndDateMillis(now, offset)
            }

            val config = ZakatConfig(
                id = id,
                name = parsedName,
                nisabStandard = nisabStandard.value!!,
                priceSource = priceSource.value,
                manualGoldPricePerGram = manualGoldPricePerGram.value.toDoubleOrNull() ?: 0.0,
                manualSilverPricePerGram = manualSilverPricePerGram.value.toDoubleOrNull()
                    ?: 0.0,
                physicalGoldGrams = physicalGoldGrams.value.toDoubleOrNull() ?: 0.0,
                physicalSilverGrams = physicalSilverGrams.value.toDoubleOrNull() ?: 0.0,
                deductions = deductions.value.toDoubleOrNull() ?: 0.0,
                accountIds = selectedAccountIds.value.toList(),
                defaultDeductionAccountId = defaultDeductionAccountId.value,
                hijriOffset = offset,
                trackingState = existingConfig?.trackingState
                    ?: ZakatTrackingState.CONFIGURED,
                nisabReachedDate = existingConfig?.nisabReachedDate,
                hawlStartDate = existingConfig?.hawlStartDate ?: now,
                hawlEndDate = hawlEnd,
                lastCheckDate = existingConfig?.lastCheckDate,
                lastCheckWealth = existingConfig?.lastCheckWealth,
                goldPricePerGram = existingConfig?.goldPricePerGram ?: 0.0,
                silverPricePerGram = existingConfig?.silverPricePerGram ?: 0.0,
                totalWealth = existingConfig?.totalWealth ?: 0.0,
                nisabAmount = existingConfig?.nisabAmount ?: 0.0,
                netZakatable = existingConfig?.netZakatable ?: 0.0,
                zakatDue = existingConfig?.zakatDue ?: 0.0,
                currency = currency,
                orderNum = orderNum,
            )

            zakatConfigRepository.save(config)
            configId = id
            nav.back()
        }
    }

    private fun delete() {
        viewModelScope.launch {
            val id = configId ?: return@launch
            zakatPaymentRepository.deleteByConfigId(id)
            zakatConfigRepository.deleteById(id)
            nav.back()
        }
    }
}
