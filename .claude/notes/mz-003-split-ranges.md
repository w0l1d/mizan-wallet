# MZ-003 — Split Balance / Income-Expense Ranges

**Status:** ✅ Done (on `develop`, commit `41a8367f`)
**Date:** 2026-08-03

## What was done

Corrected the accounts screen so the default view reproduces the pre-date-filter behaviour:

- **Default (no period selected):** balance = all-time, income/expense = current month
- **Period selected:** both share the selected range

Previously the first implementation used one shared range for everything, meaning:
- Default showed income/expense as *all-time* instead of *current month*
- Selecting a month showed balance as *only that month's net change* instead of cumulative-up-to-period-end

## Change

`AccountDataAct.Input` now takes:
```kotlin
val balanceRange: ClosedTimeRange?,      // null = all-time (default)
val incomeExpenseRange: ClosedTimeRange, // current month (default)
```

ViewModel logic:
```kotlin
val balanceRange = range.takeIf { isCustomPeriod }
```

## Files

| File | Change |
|------|--------|
| `temp/.../AccountDataAct.kt` | Split `range` into `balanceRange` + `incomeExpenseRange` |
| `feature/accounts/.../AccountsViewModel.kt` | Default `currentMonth()`, `balanceRange = range.takeIf { isCustomPeriod }` |
| `temp/.../WalletAccountLogic.kt` | Updated call (passes all-time for both) |
