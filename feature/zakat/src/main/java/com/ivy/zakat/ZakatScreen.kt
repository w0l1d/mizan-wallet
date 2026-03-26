package com.ivy.zakat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.data.model.ZakatTrackingState
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.navigation.ZakatScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GreenDark
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.BackBottomBar
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.zakat.model.AccountBalance
import kotlinx.collections.immutable.ImmutableList

@Composable
@Suppress("UnusedParameter")
fun BoxWithConstraintsScope.ZakatScreen(screen: ZakatScreen) {
    val viewModel: ZakatViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()

    UI(
        state = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun BoxWithConstraintsScope.UI(
    state: ZakatScreenState,
    onEvent: (ZakatScreenEvent) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(32.dp))

        // Title row with settings button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.zakat),
                style = UI.typo.h2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            if (state.hasConfig) {
                IvyButton(
                    text = stringResource(R.string.settings),
                    iconStart = com.ivy.ui.R.drawable.ic_settings
                ) {
                    onEvent(ZakatScreenEvent.OnOpenSettings)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!state.hasConfig) {
            Spacer(Modifier.weight(1f))
            EmptyState()
            Spacer(Modifier.weight(1f))
        } else {
            // Hijri Calendar Card
            HijriCalendarCard(
                todayHijri = state.todayHijriFormatted,
            )

            Spacer(Modifier.height(12.dp))

            // Nisab Status Card
            NisabStatusCard(
                totalWealth = state.totalWealth,
                nisabAmount = state.nisabAmount,
                isAboveNisab = state.isAboveNisab,
                nisabReachedDate = state.nisabReachedDateFormatted,
                nisabStandard = state.nisabStandardLabel,
                metalPrice = state.metalPriceLabel,
                baseCurrency = state.baseCurrency,
                accountBalances = state.accountBalances,
                physicalGoldValue = state.physicalGoldValue,
                physicalSilverValue = state.physicalSilverValue,
                deductions = state.deductions,
            )

            // Hawl Progress Card
            if (state.trackingState != ZakatTrackingState.CONFIGURED) {
                Spacer(Modifier.height(12.dp))
                HawlProgressCard(
                    state = state.trackingState,
                    startDate = state.hawlStartFormatted,
                    endDate = state.hawlEndFormatted,
                    daysRemaining = state.hawlDaysRemaining,
                    progress = state.hawlProgress,
                )
            }

            // Zakat Calculation Card
            if (state.trackingState == ZakatTrackingState.HAWL_COMPLETE) {
                Spacer(Modifier.height(12.dp))
                ZakatDueCard(
                    netZakatable = state.netZakatable,
                    zakatDue = state.zakatDue,
                    baseCurrency = state.baseCurrency,
                    onPayZakat = { onEvent(ZakatScreenEvent.OnPayZakat) },
                )
            }

            // Payment History
            if (state.totalPaid > 0) {
                Spacer(Modifier.height(12.dp))
                PaymentHistoryCard(
                    totalPaid = state.totalPaid,
                    remaining = state.remaining,
                    baseCurrency = state.baseCurrency,
                )
            }
        }

        Spacer(Modifier.height(150.dp))
    }

    val nav = navigation()
    BackBottomBar(onBack = { nav.back() }) {
        if (!state.hasConfig) {
            IvyButton(
                text = stringResource(R.string.setup_zakat),
                iconStart = com.ivy.ui.R.drawable.ic_plus
            ) {
                onEvent(ZakatScreenEvent.OnSetup)
            }
        } else {
            IvyButton(
                text = stringResource(R.string.refresh),
                iconStart = com.ivy.ui.R.drawable.ic_refresh
            ) {
                onEvent(ZakatScreenEvent.OnRefresh)
            }
        }
    }
}

@Composable
private fun HijriCalendarCard(todayHijri: String) {
    SectionCard(title = stringResource(R.string.hijri_calendar)) {
        Text(
            text = todayHijri,
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun NisabStatusCard(
    totalWealth: Double,
    nisabAmount: Double,
    isAboveNisab: Boolean,
    nisabReachedDate: String?,
    nisabStandard: String,
    metalPrice: String,
    baseCurrency: String,
    accountBalances: ImmutableList<AccountBalance>,
    physicalGoldValue: Double,
    physicalSilverValue: Double,
    deductions: Double,
) {
    SectionCard(title = stringResource(R.string.nisab_status)) {
        // Status badge
        val statusColor = if (isAboveNisab) Green else Red
        val statusText = if (isAboveNisab) {
            stringResource(R.string.above_nisab)
        } else {
            stringResource(R.string.below_nisab)
        }

        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            text = statusText,
            style = UI.typo.b2.style(
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        )

        if (nisabReachedDate != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${stringResource(R.string.above_nisab_since)} $nisabReachedDate",
                style = UI.typo.c.style(color = Gray)
            )
        }

        Spacer(Modifier.height(12.dp))

        ResultRow(
            label = stringResource(R.string.total_wealth),
            value = "${totalWealth.format(baseCurrency)} $baseCurrency"
        )
        ResultRow(
            label = stringResource(R.string.nisab_threshold) + " ($nisabStandard)",
            value = "${nisabAmount.format(baseCurrency)} $baseCurrency"
        )
        ResultRow(
            label = stringResource(R.string.metal_price),
            value = metalPrice
        )

        // Account breakdown
        if (accountBalances.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.account_balances),
                style = UI.typo.c.style(
                    color = Gray,
                    fontWeight = FontWeight.Bold
                )
            )
            accountBalances.forEach { acc ->
                ResultRow(
                    label = "  ${acc.name}",
                    value = "${acc.balance.format(baseCurrency)} $baseCurrency"
                )
            }
        }

        if (physicalGoldValue > 0) {
            ResultRow(
                label = "  " + stringResource(R.string.physical_gold),
                value = "${physicalGoldValue.format(baseCurrency)} $baseCurrency"
            )
        }
        if (physicalSilverValue > 0) {
            ResultRow(
                label = "  " + stringResource(R.string.physical_silver),
                value = "${physicalSilverValue.format(baseCurrency)} $baseCurrency"
            )
        }
        if (deductions > 0) {
            ResultRow(
                label = "  " + stringResource(R.string.deductions),
                value = "-${deductions.format(baseCurrency)} $baseCurrency"
            )
        }
    }
}

@Composable
private fun HawlProgressCard(
    state: ZakatTrackingState,
    startDate: String?,
    endDate: String?,
    daysRemaining: Int,
    progress: Float,
) {
    SectionCard(title = stringResource(R.string.hawl_progress)) {
        val statusText = when (state) {
            ZakatTrackingState.NISAB_REACHED -> stringResource(R.string.in_progress)
            ZakatTrackingState.HAWL_COMPLETE -> stringResource(R.string.complete)
            ZakatTrackingState.ZAKAT_PAID -> stringResource(R.string.paid)
            else -> stringResource(R.string.not_started)
        }

        val statusColor = when (state) {
            ZakatTrackingState.NISAB_REACHED -> Orange
            ZakatTrackingState.HAWL_COMPLETE -> Green
            ZakatTrackingState.ZAKAT_PAID -> GreenDark
            else -> Gray
        }

        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            text = statusText,
            style = UI.typo.b2.style(
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = statusColor,
        )

        Spacer(Modifier.height(8.dp))

        if (state == ZakatTrackingState.NISAB_REACHED) {
            Text(
                text = "$daysRemaining ${stringResource(R.string.days_remaining)}",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (startDate != null) {
            ResultRow(
                label = stringResource(R.string.start),
                value = startDate
            )
        }
        if (endDate != null) {
            ResultRow(
                label = stringResource(R.string.end),
                value = endDate
            )
        }
    }
}

@Composable
private fun ZakatDueCard(
    netZakatable: Double,
    zakatDue: Double,
    baseCurrency: String,
    onPayZakat: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.zakat_calculation)) {
        ResultRow(
            label = stringResource(R.string.net_zakatable),
            value = "${netZakatable.format(baseCurrency)} $baseCurrency"
        )
        ResultRow(
            label = stringResource(R.string.zakat_rate),
            value = "2.5%"
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${stringResource(R.string.zakat_due)}: ${
                zakatDue.format(baseCurrency)
            } $baseCurrency",
            style = UI.typo.b1.style(
                color = Green,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(12.dp))

        IvyButton(
            text = stringResource(R.string.pay_zakat),
            iconStart = com.ivy.ui.R.drawable.ic_planned_payments
        ) {
            onPayZakat()
        }
    }
}

@Composable
private fun PaymentHistoryCard(
    totalPaid: Double,
    remaining: Double,
    baseCurrency: String,
) {
    SectionCard(title = stringResource(R.string.payment_history)) {
        ResultRow(
            label = stringResource(R.string.total_paid),
            value = "${totalPaid.format(baseCurrency)} $baseCurrency"
        )
        ResultRow(
            label = stringResource(R.string.remaining),
            value = "${remaining.format(baseCurrency)} $baseCurrency"
        )
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
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = UI.typo.c.style(color = Gray)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        IvyIcon(
            icon = com.ivy.ui.R.drawable.ic_budget_xl,
            tint = Gray
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.no_zakat_configs),
            style = UI.typo.b1.style(
                color = Gray,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.no_zakat_configs_text),
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(96.dp))
    }
}
