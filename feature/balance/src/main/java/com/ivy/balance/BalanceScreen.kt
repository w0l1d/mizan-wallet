package com.ivy.balance

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.base.legacy.Theme
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.data.model.Month
import com.ivy.legacy.data.model.TimePeriod
import com.ivy.legacy.utils.format
import com.ivy.navigation.BalanceScreen
import com.ivy.navigation.navigation
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.BalanceRow
import com.ivy.wallet.ui.theme.components.IvyCircleButton
import com.ivy.wallet.ui.theme.components.IvyDividerLine
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModal
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModalData
import com.ivy.wallet.ui.theme.wallet.PeriodSelector

val FAB_BUTTON_SIZE = 56.dp

@Composable
fun BoxWithConstraintsScope.BalanceScreen(screen: BalanceScreen) {
    val viewModel: BalanceViewModel = viewModel()
    val uiState = viewModel.uiState()

    UI(
        state = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun BoxWithConstraintsScope.UI(
    state: BalanceState,
    onEvent: (BalanceEvent) -> Unit = {}
) {
    var choosePeriodModal: ChoosePeriodModalData? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(20.dp))

        PeriodSelector(
            period = state.period,
            onPreviousMonth = { onEvent(BalanceEvent.OnPreviousMonth) },
            onNextMonth = { onEvent(BalanceEvent.OnNextMonth) },
            onShowChoosePeriodModal = {
                choosePeriodModal = ChoosePeriodModalData(
                    period = state.period
                )
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(32.dp))

            CurrentBalance(
                currency = state.baseCurrencyCode,
                currentBalance = state.currentBalance
            )

            Spacer(Modifier.height(32.dp))

            IvyDividerLine(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(40.dp))

            ProjectedBalance(
                title = stringResource(R.string.balance_after_payments),
                titleColor = Orange,
                currency = state.baseCurrencyCode,
                currentBalance = state.currentBalance,
                plannedPaymentsAmount = state.plannedPaymentsAmount,
                balanceAfterPlannedPayments = state.balanceAfterPlannedPayments
            )

            val hasExcludedAccounts = state.excludedAccountsBalance != 0.0

            if (hasExcludedAccounts) {
                Spacer(Modifier.height(16.dp))

                ExcludedAccountsRow(
                    currency = state.baseCurrencyCode,
                    excludedAccountsBalance = state.excludedAccountsBalance
                )
            }

            if (hasExcludedAccounts && state.showExcludedAccountsBalance) {
                Spacer(Modifier.height(32.dp))

                IvyDividerLine(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(40.dp))

                ProjectedBalance(
                    title = stringResource(R.string.balance_including_excluded_accounts),
                    titleColor = Green,
                    currency = state.baseCurrencyCode,
                    currentBalance = state.currentBalanceWithExcluded,
                    plannedPaymentsAmount = state.plannedPaymentsAmountWithExcluded,
                    balanceAfterPlannedPayments =
                    state.balanceAfterPlannedPaymentsWithExcluded
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        CloseButton()

        Spacer(Modifier.height(48.dp))
    }

    ChoosePeriodModal(
        modal = choosePeriodModal,
        dismiss = {
            choosePeriodModal = null
        }
    ) {
        onEvent(BalanceEvent.OnSetPeriod(it))
    }
}

@Composable
private fun ColumnScope.CurrentBalance(
    currency: String,
    currentBalance: Double
) {
    Text(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        text = stringResource(R.string.current_balance),
        style = UI.typo.b2.style(
            color = Gray,
            fontWeight = FontWeight.ExtraBold
        )
    )

    Spacer(Modifier.height(4.dp))

    BalanceRow(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        currency = currency,
        balance = currentBalance
    )
}

@Composable
private fun ColumnScope.ProjectedBalance(
    title: String,
    titleColor: Color,
    currency: String,
    currentBalance: Double,
    plannedPaymentsAmount: Double,
    balanceAfterPlannedPayments: Double
) {
    Text(
        modifier = Modifier
            .padding(horizontal = 32.dp),
        text = title,
        style = UI.typo.b2.style(
            color = titleColor,
            fontWeight = FontWeight.ExtraBold
        )
    )

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp))

        BalanceRow(
            currency = currency,
            balance = balanceAfterPlannedPayments,

            balanceFontSize = 30.sp,
            currencyFontSize = 18.sp,

            currencyUpfront = false
        )

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = "${currentBalance.format(2)} $currency",
                style = UI.typo.nC.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Normal
                )
            )

            Spacer(Modifier.height(2.dp))

            val plusSign = if (plannedPaymentsAmount >= 0) "+" else ""
            Text(
                text = "${plusSign}${plannedPaymentsAmount.format(2)} $currency",
                style = UI.typo.nC.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }

        Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun ColumnScope.ExcludedAccountsRow(
    currency: String,
    excludedAccountsBalance: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.excluded_accounts),
            style = UI.typo.c.style(
                color = Gray,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "${excludedAccountsBalance.format(2)} $currency",
            style = UI.typo.nC.style(
                color = Gray,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun ColumnScope.CloseButton() {
    val nav = navigation()
    IvyCircleButton(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(FAB_BUTTON_SIZE)
            .rotate(45f)
            .zIndex(200f),
        backgroundPadding = 8.dp,
        icon = R.drawable.ic_add,
        backgroundGradient = Gradient.solid(Gray),
        hasShadow = false,
        tint = White
    ) {
        nav.back()
    }
}

@Preview
@Composable
private fun Preview(theme: Theme = Theme.LIGHT) {
    IvyWalletPreview(theme) {
        UI(
            state = BalanceState(
                period = TimePeriod(month = Month.monthsList().first()),
                baseCurrencyCode = "BGN",
                currentBalance = 9326.55,
                balanceAfterPlannedPayments = 8426.0,
                plannedPaymentsAmount = -900.55,
                excludedAccountsBalance = 5500.0,
                showExcludedAccountsBalance = true,
                currentBalanceWithExcluded = 14326.55,
                plannedPaymentsAmountWithExcluded = -400.55,
                balanceAfterPlannedPaymentsWithExcluded = 13926.0,
            )
        )
    }
}

/** For screenshot testing */
@Composable
fun BalanceScreenUiTest(isDark: Boolean) {
    val theme = when (isDark) {
        true -> Theme.DARK
        false -> Theme.LIGHT
    }
    Preview(theme)
}