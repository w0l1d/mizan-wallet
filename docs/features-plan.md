# Feature Implementation Plans

**Date:** 2026-07-13
**Status:** Planning — not yet executed
**Branches to create:** `feat/transfer-excluded-expenses`, `feat/transfer-excluded-incomes`, `feat/accounts-date-filter`

---

## Table of Contents

1. [Feature A: Transfers to Excluded Accounts as Expenses](#feature-a-transfers-to-excluded-accounts-as-expenses)
2. [Feature B: Transfers from Excluded Accounts as Incomes](#feature-b-transfers-from-excluded-accounts-as-incomes)
3. [Feature C: Date Filter on Accounts Screen](#feature-c-date-filter-on-accounts-screen)
4. [Implementation Order & Dependencies](#implementation-order--dependencies)
5. [Testing Strategy](#testing-strategy)

---

## Feature A: Transfers to Excluded Accounts as Expenses

### Overview

Add a checkbox (feature toggle) in Advanced Features that, when enabled, counts transfers from included accounts **to** excluded accounts as expenses in the monthly expense total shown at the top of the main/home screen.

**Example:** You have a "Savings" account excluded from balance. When you transfer €500 from your "Checking" (included) to "Savings" (excluded), that €500 should appear as an expense in the monthly total — treating money moved to excluded accounts as "spent" from the perspective of your active accounts.

### Current Behaviour

- `CalcIncomeExpenseAct` calls `filterExcluded(accounts)` which drops all accounts with `includeInBalance == false`
- For each remaining account, it counts only `Income` and `Expense` transaction types — transfers are ignored entirely
- Money transferred to an excluded account simply disappears from the monthly income/expense totals

### Target Behaviour

When the toggle is **enabled**:
- Transfers from an included account to an excluded account are counted as **expenses**
- The amount counted is the `fromValue` of the transfer (the amount leaving the included account)
- Only applies to transfers where the source account is included AND the destination account is excluded

When the toggle is **disabled** (default):
- Current behaviour is preserved — no change

### Implementation Plan

#### Step A1: Add SharedPrefs key

**File:** `shared/base/src/main/java/com/ivy/base/legacy/SharedPrefs.kt`

Add a new constant:
```kotlin
const val TRANSFERS_TO_EXCLUDED_AS_EXPENSE = "transfers_to_excluded_as_expense"
```

**Rationale:** This follows the exact same pattern as the existing `TRANSFERS_AS_INCOME_EXPENSE` setting. It's a behavioural preference that affects core financial calculations, so SharedPrefs is the right persistence layer (not DataStore feature flags).

#### Step A2: Add setting to SettingsState

**File:** `feature/settings/src/main/java/com/ivy/settings/SettingsState.kt`

Add field:
```kotlin
val treatTransfersToExcludedAsExpense: Boolean,
```

#### Step A3: Add setting to SettingsViewModel

**File:** `feature/settings/src/main/java/com/ivy/settings/SettingsViewModel.kt`

Add:
- A `mutableStateOf(false)` field `treatTransfersToExcludedAsExpense`
- `initializeTransfersToExcludedAsExpense()` — reads from SharedPrefs
- `setTransfersToExcludedAsExpense(Boolean)` — writes to SharedPrefs
- `getTreatTransfersToExcludedAsExpense()` Composable getter
- Handle `SettingsEvent.SetTransfersToExcludedAsExpense` in `onEvent()`

#### Step A4: Add event and UI toggle

**File:** `feature/settings/src/main/java/com/ivy/settings/SettingsEvent.kt`

Add:
```kotlin
data class SetTransfersToExcludedAsExpense(val enabled: Boolean) : SettingsEvent()
```

**File:** `feature/settings/src/main/java/com/ivy/settings/SettingsScreen.kt`

Add a new `AppSwitch` row in the App Settings section (near the existing "Transfers as income/expense" toggle) with:
- Icon: `R.drawable.ic_custom_transfer_m` (reuse or create new)
- Label: "Transfers to excluded as expense"
- Description: "Count transfers to excluded accounts as expenses in monthly totals"

#### Step A5: Modify CalcIncomeExpenseAct

**File:** `temp/legacy-code/src/main/java/com/ivy/legacy/domain/action/wallet/CalcIncomeExpenseAct.kt`

This is the **core logic change**. Currently:

```kotlin
override suspend fun Input.compose(): suspend () -> IncomeExpensePair = suspend {
    filterExcluded(accounts)
} thenMap { acc ->
    Pair(acc, accTrnsAct(AccTrnsAct.Input(accountId = acc.id, range = range)))
} thenMap { (acc, trns) ->
    Pair(acc, foldTransactions(transactions = trns,
        valueFunctions = nonEmptyListOf(AccountValueFunctions::income, AccountValueFunctions::expense),
        arg = acc.id))
} thenMap { (acc, stats) ->
    stats.map { exchangeAct(ExchangeAct.Input(...), amount = it).orZero() }
} then { statsList ->
    IncomeExpensePair(income = statsList.sumOf { it[0] }, expense = statsList.sumOf { it[1] })
}
```

**New Input fields:**
```kotlin
data class Input(
    val baseCurrency: String,
    val accounts: List<Account>,
    val range: ClosedTimeRange,
    val transfersToExcludedAsExpense: Boolean = false,  // NEW
)
```

**Modified logic** (pseudocode):

```kotlin
override suspend fun Input.compose(): suspend () -> IncomeExpensePair = suspend {
    val includedAccounts = accounts.filter { it.includeInBalance }
    val excludedAccounts = accounts.filter { !it.includeInBalance }
    val excludedAccountIds = excludedAccounts.map { it.id }.toSet()

    // 1. Calculate normal income/expense for included accounts (existing logic)
    val normalStats = includedAccounts.map { acc -> /* existing pipeline */ }

    // 2. If toggle enabled, find transfers from included → excluded
    val transferExpenses = if (transfersToExcludedAsExpense && excludedAccountIds.isNotEmpty()) {
        includedAccounts.flatMap { acc ->
            val trns = accTrnsAct(AccTrnsAct.Input(accountId = acc.id, range = range))
            trns.filterIsInstance<Transfer>()
                .filter { transfer -> transfer.toAccount in excludedAccountIds }
                .map { transfer -> exchangeAct(ExchangeAct.Input(...), amount = transfer.fromValue).orZero() }
        }.sum()
    } else BigDecimal.ZERO

    IncomeExpensePair(
        income = normalStats.sumOf { it.income },
        expense = normalStats.sumOf { it.expense } + transferExpenses
    )
}
```

**Key design decisions:**
- The `excludedAccountIds` set is built from the full accounts list (before filtering)
- Only transfers where BOTH: source is included AND destination is excluded are counted
- The amount used is `fromValue` (what left the included account), not `toValue` (what arrived)
- Currency exchange is applied before summing

#### Step A6: Pass setting from HomeViewModel

**File:** `feature/home/src/main/java/com/ivy/home/HomeViewModel.kt`

In `loadIncomeExpenseBalance()`:

```kotlin
val transfersToExcludedAsExpense = sharedPrefs.getBoolean(
    SharedPrefs.TRANSFERS_TO_EXCLUDED_AS_EXPENSE, false
)

val incomeExpense = calcIncomeExpenseAct(
    CalcIncomeExpenseAct.Input(
        baseCurrency = settings.baseCurrency,
        accounts = accounts,  // pass ALL accounts, not just included
        range = timeRange,
        transfersToExcludedAsExpense = transfersToExcludedAsExpense,
    )
)
```

#### Step A7: Apply to other consumers

The `CalcIncomeExpenseAct` is also used by:
- `PieChartStatisticViewModel` — needs the same flag for consistency
- `TransactionsViewModel` — optional but recommended

Read the setting and pass it through in each consumer.

### Affected Files Summary

| File | Change |
|------|--------|
| `shared/base/.../SharedPrefs.kt` | Add key constant |
| `feature/settings/.../SettingsState.kt` | Add field |
| `feature/settings/.../SettingsEvent.kt` | Add event |
| `feature/settings/.../SettingsViewModel.kt` | Add state, init, persist, getter |
| `feature/settings/.../SettingsScreen.kt` | Add AppSwitch row |
| `temp/.../CalcIncomeExpenseAct.kt` | Add input param + transfer logic |
| `feature/home/.../HomeViewModel.kt` | Read pref + pass to act |
| `feature/piechart/.../PieChartStatisticViewModel.kt` | Read pref + pass to act |
| `feature/transactions/.../TransactionsViewModel.kt` | Read pref + pass to act |

### Strings to Add

```xml
<string name="transfers_to_excluded_as_expense">Transfers to excluded as expense</string>
<string name="transfers_to_excluded_as_expense_desc">Count money transferred to excluded accounts as expenses in monthly totals</string>
```

---

## Feature B: Transfers from Excluded Accounts as Incomes

### Overview

Add a checkbox (feature toggle) in Advanced Features that, when enabled, counts transfers **from** excluded accounts **to** included accounts as income in the monthly income total shown at the top of the main/home screen.

**Example:** You withdraw €200 from a "Savings" account (excluded) to your "Checking" account (included). With this toggle on, that €200 appears as income — treating money coming back from excluded accounts as "earned" from the perspective of your active accounts.

### Current Behaviour

Same as Feature A — transfers are completely ignored in `CalcIncomeExpenseAct`.

### Target Behaviour

When the toggle is **enabled**:
- Transfers from an excluded account to an included account are counted as **income**
- The amount counted is the `toValue` of the transfer (the amount arriving in the included account)
- Only applies to transfers where the source account is excluded AND the destination account is included

When the toggle is **disabled** (default):
- Current behaviour is preserved — no change

### Implementation Plan

#### Step B1: Add SharedPrefs key

**File:** `shared/base/src/main/java/com/ivy/base/legacy/SharedPrefs.kt`

```kotlin
const val TRANSFERS_FROM_EXCLUDED_AS_INCOME = "transfers_from_excluded_as_income"
```

#### Step B2–B4: Settings plumbing

Same pattern as Feature A (Steps A2–A4), add:
- Field in `SettingsState`
- State, init, persist, getter in `SettingsViewModel`
- Event in `SettingsEvent`
- `AppSwitch` row in `SettingsScreen`

#### Step B5: Modify CalcIncomeExpenseAct (combined with Feature A)

**File:** `temp/legacy-code/src/main/java/com/ivy/legacy/domain/action/wallet/CalcIncomeExpenseAct.kt`

**Updated Input:**
```kotlin
data class Input(
    val baseCurrency: String,
    val accounts: List<Account>,
    val range: ClosedTimeRange,
    val transfersToExcludedAsExpense: Boolean = false,
    val transfersFromExcludedAsIncome: Boolean = false,  // NEW
)
```

**Extended logic** (combined with Feature A):

```kotlin
// In addition to Feature A's transfer expense calculation:
val transferIncomes = if (transfersFromExcludedAsIncome && excludedAccountIds.isNotEmpty()) {
    // For each included account, find transfers FROM excluded accounts TO this account
    includedAccounts.flatMap { acc ->
        val trns = accTrnsAct(AccTrnsAct.Input(accountId = acc.id, range = range))
        trns.filterIsInstance<Transfer>()
            .filter { transfer ->
                // The transfer is TO this included account FROM an excluded account
                transfer.toAccount == acc.id && transfer.fromAccount in excludedAccountIds
            }
            .map { transfer -> exchangeAct(ExchangeAct.Input(...), amount = transfer.toValue).orZero() }
    }.sum()
} else BigDecimal.ZERO

IncomeExpensePair(
    income = normalStats.sumOf { it.income } + transferIncomes,
    expense = normalStats.sumOf { it.expense } + transferExpenses
)
```

**Important distinction from Feature A:**
- Feature A: looks at transfers FROM included accounts, checks if `toAccount` is excluded
- Feature B: looks at transfers TO included accounts, checks if `fromAccount` is excluded
- Both iterate over included accounts' transactions, so we don't need to query excluded accounts' transactions (which might be expensive)

**Combined approach** — single iteration:
For each included account, fetch all its transactions once. Then:
- If Feature A enabled: filter transfers where `toAccount` is excluded → add to expenses
- If Feature B enabled: filter transfers where `fromAccount` is excluded → add to incomes

This means the `AccTrnsAct` call (which fetches both "from" and "to" transactions) already covers the data needed for both features. No additional DB queries needed.

#### Step B6–B7: Pass from consumers

Same pattern as Feature A — read from SharedPrefs and pass to `CalcIncomeExpenseAct.Input`.

### Affected Files Summary

Same files as Feature A, plus the combined modifications to `CalcIncomeExpenseAct.Input`.

### Strings to Add

```xml
<string name="transfers_from_excluded_as_income">Transfers from excluded as income</string>
<string name="transfers_from_excluded_as_income_desc">Count money received from excluded accounts as income in monthly totals</string>
```

---

## Feature C: Date Filter on Accounts Screen

### Overview

Add a date/period filter to the Accounts screen, similar to the existing filter on the main/home screen. Currently, the accounts screen always shows per-account stats for the **current month only**. This feature allows users to select any month (or custom date range) and see each account's balance **up to** that date, plus income/expense totals **for** that period.

### Current Behaviour

- `AccountsViewModel.startInternally()` hardcodes `TimePeriod.currentMonth()` at line 169–171
- Each `AccountData` shows:
  - `balance`: all-time (no date filter)
  - `monthlyIncome` / `monthlyExpenses`: current month only
- No UI to change the period
- Balance is calculated all-time via `CalcAccBalanceAct` with no range → `ClosedTimeRange.allTimeIvy()`

### Target Behaviour

- A period selector at the top of the Accounts tab (same UI pattern as home screen's `HomeHeader`)
- Default: current month (preserving existing behaviour)
- User can:
  - Swipe left/right to navigate months
  - Tap to open `ChoosePeriodModal` for month/custom/last N/all time selection
- When period changes:
  - Each account's **balance** is recalculated up to the **end** of the selected period (instead of all-time)
  - Each account's **income/expense** is recalculated for the selected period (instead of current month)
  - The total wallet balance (with/without excluded) can optionally stay all-time or follow the period

### Implementation Plan

#### Step C1: Add period state to AccountsViewModel

**File:** `feature/accounts/src/main/java/com/ivy/accounts/AccountsViewModel.kt`

Add:
```kotlin
private var selectedPeriod by mutableStateOf(
    TimePeriod.currentMonth(startDayOfMonth = ivyContext.startDayOfMonth)
)
```

Expose in `AccountsState`:
```kotlin
val period: TimePeriod
```

#### Step C2: Update AccountsState with period

**File:** `feature/accounts/src/main/java/com/ivy/accounts/AccountsState.kt`

Add:
```kotlin
val period: TimePeriod,
```

#### Step C3: Modify startInternally() to use selected period

**File:** `feature/accounts/src/main/java/com/ivy/accounts/AccountsViewModel.kt`

Change `startInternally()`:
```kotlin
private suspend fun startInternally() {
    val period = selectedPeriod  // was: TimePeriod.currentMonth(...)
    val range = period.toRange(ivyContext.startDayOfMonth, timeConverter, timeProvider)

    // ... rest stays the same but uses the selected period's range
    val accountsDataList = accountDataAct(
        AccountDataAct.Input(
            accounts = accounts,
            range = range.toCloseTimeRange(),
            baseCurrency = baseCurrencyCode,
            includeTransfersInCalc = includeTransfersInCalc
        )
    )
    // ...
}
```

**But wait** — `AccountDataAct` currently calculates `balance` as all-time (no range) via `CalcAccBalanceAct`. We need to make the balance respect the period too.

#### Step C4: Modify AccountDataAct to support period-filtered balance

**File:** `temp/legacy-code/src/main/java/com/ivy/legacy/domain/action/viewmodel/account/AccountDataAct.kt`

Currently, `AccountDataAct` calls `CalcAccBalanceAct` which takes a `range` parameter. Check if `AccountDataAct` is already passing the range — if not, pass it through.

**Actually**, looking at the code more carefully: `CalcAccBalanceAct` already accepts a `range` parameter. If `range` is null, it defaults to all-time. So the fix may be as simple as passing the range through from `AccountDataAct.Input.range` to `CalcAccBalanceAct`.

**Design decision:** Should the balance on the accounts screen be:
- (a) Balance up to the END of the selected period, OR
- (b) Still all-time?

The user said: "this filter will make it show balance up to that specified date and expenses and income total of the specified month or up to that date in that month"

So the answer is **(a)**: balance up to the period end. This means:
- If the user selects "July 2026": balance = all transactions up to July 31, 2026
- If user selects a custom range ending July 13: balance = all transactions up to July 13
- Income/expense = only transactions within the range

#### Step C5: Add period navigation events

**File:** `feature/accounts/src/main/java/com/ivy/accounts/AccountsEvent.kt`

Add:
```kotlin
data class SetPeriod(val period: TimePeriod) : AccountsEvent()
data object SelectNextMonth : AccountsEvent()
data object SelectPreviousMonth : AccountsEvent()
```

#### Step C6: Add period selector UI to AccountsTab

**File:** `feature/accounts/src/main/java/com/ivy/accounts/AccountsTab.kt`

Add a period selector header bar at the top of the accounts list. This can reuse the same pattern as `HomeHeader` — an `IvyOutlinedButton` with the period display text that:
- Shows the current period (`period.toDisplayShort(...)`)
- Opens `ChoosePeriodModal` on tap
- Supports swipe left/right for month navigation

The `ChoosePeriodModal` from `temp/legacy-code/.../modal/ChoosePeriodModal.kt` can be reused directly — it's already parameterized and fires a callback with the selected `TimePeriod`.

#### Step C7: Wire period changes to reload

In `AccountsViewModel.onEvent()`:
```kotlin
is AccountsEvent.SetPeriod -> {
    selectedPeriod = event.period
    startInternally()
}
AccountsEvent.SelectNextMonth -> {
    val month = selectedPeriod.month
    val year = selectedPeriod.year ?: timeProvider.localNow().year
    val nextPeriod = month?.incrementMonthPeriod(ivyContext, 1L, year = year)
    if (nextPeriod != null) {
        selectedPeriod = nextPeriod
        startInternally()
    }
}
AccountsEvent.SelectPreviousMonth -> {
    // similar with -1L
}
```

### Affected Files Summary

| File | Change |
|------|--------|
| `feature/accounts/.../AccountsState.kt` | Add `period` field |
| `feature/accounts/.../AccountsEvent.kt` | Add period events |
| `feature/accounts/.../AccountsViewModel.kt` | Add period state, modify `startInternally()`, handle events |
| `feature/accounts/.../AccountsTab.kt` | Add period selector UI + `ChoosePeriodModal` |
| `temp/.../AccountDataAct.kt` | Pass range to `CalcAccBalanceAct` for period-filtered balance |
| `temp/.../CalcAccBalanceAct.kt` | Verify it already supports range (it does — verify no changes needed) |

### UI Layout

```
┌─────────────────────────────────┐
│  [<]  July 2026  [>]     [v]   │  ← Period selector (new)
├─────────────────────────────────┤
│  Total: $5,230.00               │
│  Total (excl.): $4,800.00       │
├─────────────────────────────────┤
│  ┌───────────────────────────┐  │
│  │ Checking        $2,100.00 │  │
│  │ +$500.00  -$320.00       │  │  ← Income/expense for selected period
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │ Savings (Excluded)        │  │
│  │ $3,130.00                 │  │  ← Balance up to period end
│  │ +$0.00  -$0.00            │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

### Strings to Add

No new strings needed (reuses existing period display and modal strings).

---

## Implementation Order & Dependencies

```
1. Feature A (transfers → excluded = expenses)
   │
   └─► 2. Feature B (transfers from excluded = incomes)
        │  Depends on A because they modify the same CalcIncomeExpenseAct.Input
        │  and it's cleaner to add both flags in one pass of that file
        │
        └─► 3. Feature C (accounts date filter)
             Independent of A+B — can be developed in parallel or after
```

### Recommended branch strategy:

| Branch | Feature | Base |
|--------|---------|------|
| `feat/transfer-excluded-expenses` | A only | `main` |
| `feat/transfer-excluded-incomes` | B | After A merges (or A+B together) |
| `feat/accounts-date-filter` | C | `main` |

**Alternative:** Combine A+B into a single branch `feat/transfer-excluded-calc` since they touch the exact same files and logic.

---

## Testing Strategy

### Feature A: Transfers to Excluded Accounts as Expenses

#### Unit Tests

**File:** `temp/legacy-code/src/test/java/com/ivy/legacy/domain/action/wallet/CalcIncomeExpenseActTest.kt` (create if not exists)

| Test ID | Scenario | Expected |
|---------|----------|----------|
| A-UT-1 | Toggle OFF: transfer from included → excluded | Not counted as expense |
| A-UT-2 | Toggle ON: transfer from included → excluded | Counted as expense (fromValue) |
| A-UT-3 | Toggle ON: transfer from included → included | NOT counted (both included) |
| A-UT-4 | Toggle ON: transfer from excluded → excluded | NOT counted (neither included) |
| A-UT-5 | Toggle ON: transfer with different currencies | Exchange rate applied correctly |
| A-UT-6 | Toggle ON: multiple transfers to excluded | All correctly summed |
| A-UT-7 | Toggle ON: no excluded accounts | No additional expenses (no crash) |

#### Integration Tests

| Test ID | Scenario |
|---------|----------|
| A-IT-1 | Settings toggle persists across app restart |
| A-IT-2 | Home screen expense total updates when toggle changes |

#### Manual Test Cases

| Test ID | Scenario | Steps |
|---------|----------|-------|
| A-MT-1 | Basic flow | 1. Create "Checking" (included) and "Savings" (excluded). 2. Transfer €100 from Checking → Savings. 3. Verify monthly expense does NOT include the €100. 4. Enable toggle in settings. 5. Verify monthly expense now includes €100. |
| A-MT-2 | Toggle OFF resets | After A-MT-1, disable toggle. Verify expense returns to previous value. |
| A-MT-3 | Self-transfer | Transfer from Checking to Checking. Verify never counted regardless of toggle. |

### Feature B: Transfers from Excluded Accounts as Incomes

#### Unit Tests

| Test ID | Scenario | Expected |
|---------|----------|----------|
| B-UT-1 | Toggle OFF: transfer from excluded → included | Not counted as income |
| B-UT-2 | Toggle ON: transfer from excluded → included | Counted as income (toValue) |
| B-UT-3 | Toggle ON: transfer from included → included | NOT counted (both included) |
| B-UT-4 | Toggle ON: transfer involving different currencies | Exchange rate applied correctly |

#### Manual Test Cases

| Test ID | Scenario | Steps |
|---------|----------|-------|
| B-MT-1 | Basic flow | 1. Transfer €200 from "Savings" (excluded) → "Checking" (included). 2. Enable toggle. 3. Verify monthly income includes €200. |
| B-MT-2 | Both toggles ON | Transfer €100 Checking→Savings AND €50 Savings→Checking. Verify expense +€100, income +€50. |
| B-MT-3 | Edge case: excluded→excluded | Transfer between two excluded accounts. Verify never counted. |

### Feature C: Accounts Date Filter

#### Unit Tests

| Test ID | Scenario | Expected |
|---------|----------|----------|
| C-UT-1 | Default period is current month | Verify `selectedPeriod` initializes to `currentMonth()` |
| C-UT-2 | Next month navigation | Period advances by one month |
| C-UT-3 | Previous month navigation | Period goes back by one month |
| C-UT-4 | Custom period via ChoosePeriodModal | Period updates correctly |
| C-UT-5 | Balance filtered to period end | Transfers after period end not included in balance |
| C-UT-6 | Income/expense filtered to period | Only transactions within period counted |

#### Screenshot Tests (Paparazzi)

| Test ID | Scenario |
|---------|----------|
| C-ST-1 | Accounts tab with default period (current month) |
| C-ST-2 | Accounts tab with custom period header visible |
| C-ST-3 | Accounts tab with ChoosePeriodModal open |

#### Manual Test Cases

| Test ID | Scenario | Steps |
|---------|----------|-------|
| C-MT-1 | Month navigation | 1. Open Accounts tab. 2. Swipe to previous month. 3. Verify income/expense and balance change. 4. Swipe to next month. 5. Verify return to current month values. |
| C-MT-2 | Balance correctness | 1. Create transactions across multiple months. 2. Select a past month. 3. Verify balance = sum up to end of that month. 4. Verify income/expense = only that month's transactions. |
| C-MT-3 | Start day of month | 1. Set start day of month to 15. 2. Verify period display shows "Jul 15 – Aug 14". 3. Verify calculations use correct range. |
| C-MT-4 | Custom date range | 1. Open ChoosePeriodModal. 2. Select custom from/to dates. 3. Verify calculations use the custom range. |

---

## Risk Assessment

### Feature A & B

| Risk | Severity | Mitigation |
|------|----------|------------|
| Incorrect currency conversion for transfer amounts | Medium | Always apply `ExchangeAct` before summing; use existing exchange logic |
| Performance: additional transfer filtering for each included account | Low | Transfers are already fetched by `AccTrnsAct`; filtering is in-memory and O(n) |
| Self-transfers incorrectly counted | Low | Explicitly check `fromAccount != toAccount` |
| Breaking existing pie chart / reports that use `CalcIncomeExpenseAct` | Medium | New flags default to `false`; all existing callers get unchanged behaviour |

### Feature C

| Risk | Severity | Mitigation |
|------|----------|------------|
| `AccountDataAct` doesn't pass range to `CalcAccBalanceAct` | Low | Verified that `CalcAccBalanceAct` already accepts optional `range` parameter |
| Widget balance not respecting period | Low | Widget uses `CalcWalletBalanceAct` separately — not affected by accounts screen period |
| Performance with large date ranges | Medium | Use `ClosedTimeRange.allTimeIvy()` for "all time" to avoid unbounded queries |
| `ChoosePeriodModal` visual compatibility with accounts screen | Low | Modal is already used on home and transactions screens; reuse directly |

---

## Rollback

Each feature is isolated:
- **A & B:** Disable the toggle → behaviour reverts to current. No data migration.
- **C:** The default period is `currentMonth()` which matches current behaviour. If issues arise, the period selector can be hidden behind a feature flag.

All changes are additive — no database migrations required.

---

## Estimated Effort

| Feature | Files | Effort |
|---------|-------|--------|
| A: Transfers to excluded as expenses | ~7 files | 3–4 hours |
| B: Transfers from excluded as incomes | ~7 files (mostly same as A) | 2–3 hours (less if done with A) |
| C: Accounts date filter | ~6 files | 4–6 hours |
| **Total (if done separately)** | | **9–13 hours** |
| **Total (A+B combined, C separate)** | | **7–10 hours** |

---

## Open Questions

1. **Feature A+B: Should these be in main Settings or Advanced Features (FeaturesScreen)?**
   - Recommendation: Main Settings, alongside the existing "Transfers as income/expense" toggle. They are financial calculation preferences, not experimental UI tweaks.

2. **Feature C: Should the total wallet balance at the top of accounts also respect the period filter?**
   - Currently shows all-time balance (two versions: with/without excluded). The user's description suggests yes. Recommendation: Add a third line or toggle showing period-end balance.

3. **Feature C: Should "All time" option set balance to all-time but income/expense to period?**
   - Recommendation: "All time" in the period selector sets the range to `allTimeIvy()`, meaning both balance and income/expense cover all time. If users want all-time balance with monthly income/expense, that's the default view.

---

*Plan prepared for implementation. Awaiting approval before creating branches and writing code.*
