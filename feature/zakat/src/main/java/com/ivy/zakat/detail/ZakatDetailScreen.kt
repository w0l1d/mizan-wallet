package com.ivy.zakat.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ivy.data.model.NisabStandard
import com.ivy.data.model.PriceSource
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.navigation.ZakatDetailScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GradientRed

import com.ivy.wallet.ui.theme.components.BackBottomBar
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.zakat.R
import com.ivy.zakat.model.AccountBalance

@Composable
fun BoxWithConstraintsScope.ZakatDetailScreen(screen: ZakatDetailScreen) {
    val viewModel: ZakatDetailViewModel = screenScopedViewModel()
    viewModel.setConfigId(screen.zakatConfigId)
    val uiState = viewModel.uiState()

    UI(
        state = uiState,
        onEvent = viewModel::onEvent,
    )
}

@Suppress("LongMethod")
@Composable
private fun BoxWithConstraintsScope.UI(
    state: ZakatDetailScreenState,
    onEvent: (ZakatDetailScreenEvent) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(start = 24.dp),
            text = if (state.isEditMode) {
                stringResource(R.string.zakat_settings)
            } else {
                stringResource(R.string.setup_zakat)
            },
            style = UI.typo.h2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(24.dp))

        // Name
        SectionCard(title = stringResource(R.string.name)) {
            InputField(
                value = state.name,
                label = stringResource(R.string.name),
                onValueChange = { onEvent(ZakatDetailScreenEvent.OnNameChanged(it)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // Nisab Standard
        SectionCard(title = stringResource(R.string.nisab_standard)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NisabButton(
                    label = stringResource(R.string.gold_nisab),
                    selected = state.nisabStandard == NisabStandard.GOLD,
                    onClick = {
                        onEvent(
                            ZakatDetailScreenEvent.OnNisabStandardChanged(NisabStandard.GOLD)
                        )
                    }
                )
                Spacer(Modifier.width(12.dp))
                NisabButton(
                    label = stringResource(R.string.silver_nisab),
                    selected = state.nisabStandard == NisabStandard.SILVER,
                    onClick = {
                        onEvent(
                            ZakatDetailScreenEvent.OnNisabStandardChanged(NisabStandard.SILVER)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Price Source
        SectionCard(title = stringResource(R.string.price_source)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.priceSource == PriceSource.AUTOMATIC,
                    onClick = {
                        onEvent(
                            ZakatDetailScreenEvent.OnPriceSourceChanged(PriceSource.AUTOMATIC)
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.automatic),
                    style = UI.typo.b2.style(color = UI.colors.pureInverse),
                    modifier = Modifier.clickable {
                        onEvent(
                            ZakatDetailScreenEvent.OnPriceSourceChanged(PriceSource.AUTOMATIC)
                        )
                    }
                )
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = state.priceSource == PriceSource.MANUAL,
                    onClick = {
                        onEvent(
                            ZakatDetailScreenEvent.OnPriceSourceChanged(PriceSource.MANUAL)
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.manual),
                    style = UI.typo.b2.style(color = UI.colors.pureInverse),
                    modifier = Modifier.clickable {
                        onEvent(
                            ZakatDetailScreenEvent.OnPriceSourceChanged(PriceSource.MANUAL)
                        )
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (state.priceSource == PriceSource.AUTOMATIC) {
                val goldLabel = state.autoGoldPricePerGram?.let {
                    "${"%.2f".format(it)} ${state.baseCurrency}/g"
                } ?: stringResource(R.string.unavailable)
                val silverLabel = state.autoSilverPricePerGram?.let {
                    "${"%.2f".format(it)} ${state.baseCurrency}/g"
                } ?: stringResource(R.string.unavailable)

                Text(
                    text = "${stringResource(R.string.gold_price)}: $goldLabel",
                    style = UI.typo.c.style(color = Gray)
                )
                Text(
                    text = "${stringResource(R.string.silver_price)}: $silverLabel",
                    style = UI.typo.c.style(color = Gray)
                )

                Spacer(Modifier.height(8.dp))
                IvyButton(
                    text = stringResource(R.string.refresh_prices),
                    iconStart = com.ivy.ui.R.drawable.ic_refresh
                ) {
                    onEvent(ZakatDetailScreenEvent.OnRefreshPrices)
                }
            } else {
                InputField(
                    value = state.manualGoldPricePerGram,
                    label = "${stringResource(R.string.gold_price)} (${state.baseCurrency}/g)",
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = {
                        onEvent(ZakatDetailScreenEvent.OnManualGoldPriceChanged(it))
                    }
                )
                Spacer(Modifier.height(8.dp))
                InputField(
                    value = state.manualSilverPricePerGram,
                    label = "${stringResource(R.string.silver_price)} (${state.baseCurrency}/g)",
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = {
                        onEvent(ZakatDetailScreenEvent.OnManualSilverPriceChanged(it))
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Physical Holdings
        SectionCard(title = stringResource(R.string.physical_holdings)) {
            InputField(
                value = state.physicalGoldGrams,
                label = stringResource(R.string.gold_grams),
                keyboardType = KeyboardType.Decimal,
                onValueChange = {
                    onEvent(ZakatDetailScreenEvent.OnPhysicalGoldGramsChanged(it))
                }
            )
            Spacer(Modifier.height(8.dp))
            InputField(
                value = state.physicalSilverGrams,
                label = stringResource(R.string.silver_grams),
                keyboardType = KeyboardType.Decimal,
                onValueChange = {
                    onEvent(ZakatDetailScreenEvent.OnPhysicalSilverGramsChanged(it))
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        // Deductions
        SectionCard(title = stringResource(R.string.deductions)) {
            InputField(
                value = state.deductions,
                label = stringResource(R.string.debts_amount),
                keyboardType = KeyboardType.Decimal,
                onValueChange = {
                    onEvent(ZakatDetailScreenEvent.OnDeductionsChanged(it))
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        // Account Selection
        SectionCard(title = stringResource(R.string.select_accounts)) {
            Text(
                text = stringResource(R.string.select_accounts_hint),
                style = UI.typo.c.style(color = Gray)
            )
            Spacer(Modifier.height(8.dp))
            state.accounts.forEach { acc ->
                AccountRow(
                    account = acc,
                    isDeductionAccount = state.defaultDeductionAccountId ==
                        acc.accountId.value.toString(),
                    onToggle = {
                        onEvent(ZakatDetailScreenEvent.OnAccountToggled(acc.accountId))
                    },
                    onSetAsDeduction = {
                        onEvent(
                            ZakatDetailScreenEvent.OnDefaultDeductionAccountChanged(
                                acc.accountId
                            )
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Hijri Calendar Offset
        SectionCard(title = stringResource(R.string.hijri_offset)) {
            Text(
                text = stringResource(R.string.hijri_offset_hint),
                style = UI.typo.c.style(color = Gray)
            )
            Spacer(Modifier.height(8.dp))
            InputField(
                value = state.hijriOffset,
                label = stringResource(R.string.offset_days),
                keyboardType = KeyboardType.Number,
                onValueChange = {
                    onEvent(ZakatDetailScreenEvent.OnHijriOffsetChanged(it))
                }
            )
        }

        // Delete button (edit mode only)
        if (state.isEditMode) {
            Spacer(Modifier.height(24.dp))
            IvyButton(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.delete),
                iconStart = com.ivy.ui.R.drawable.ic_delete,
                backgroundGradient = GradientRed,
            ) {
                onEvent(ZakatDetailScreenEvent.OnDelete)
            }
        }

        Spacer(Modifier.height(150.dp))
    }

    val nav = navigation()
    BackBottomBar(onBack = { nav.back() }) {
        IvyButton(
            text = stringResource(R.string.save),
            iconStart = com.ivy.ui.R.drawable.ic_save
        ) {
            onEvent(ZakatDetailScreenEvent.OnSave)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(UI.colors.medium)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InputField(
    value: String,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}

@Composable
private fun NisabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) Green.copy(alpha = 0.2f) else UI.colors.pure
    val textColor = if (selected) Green else UI.colors.pureInverse

    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        text = label,
        style = UI.typo.b2.style(
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun AccountRow(
    account: AccountBalance,
    isDeductionAccount: Boolean,
    onToggle: () -> Unit,
    onSetAsDeduction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = account.selected,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = account.currency,
                style = UI.typo.c.style(color = Gray)
            )
        }
        if (account.selected) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDeductionAccount) {
                            Green.copy(alpha = 0.2f)
                        } else {
                            UI.colors.pure
                        }
                    )
                    .clickable { onSetAsDeduction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                text = if (isDeductionAccount) {
                    stringResource(R.string.deduction_account)
                } else {
                    stringResource(R.string.set_as_deduction)
                },
                style = UI.typo.nC.style(
                    color = if (isDeductionAccount) Green else Gray,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
