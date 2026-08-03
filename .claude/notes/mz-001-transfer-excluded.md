# MZ-001 — Excluded-Transfer Income/Expense Toggles

**Status:** ✅ Done (on `develop`)
**Branch:** `feat/transfer-excluded-calc` → `d171e606`
**Date:** 2026-07

## What was done

Add two settings toggles in Settings → App Settings:

1. **Transfers to excluded as expense** — counts transfers from included accounts *to* excluded accounts as expenses in monthly totals.
2. **Transfers from excluded as income** — counts transfers *from* excluded accounts *to* included accounts as income.

Both default to **off**, preserving existing behaviour.

## Files

| File | Change |
|------|--------|
| `shared/base/src/main/java/com/ivy/base/legacy/SharedPrefs.kt` | Added `TRANSFERS_TO_EXCLUDED_AS_EXPENSE` and `TRANSFERS_FROM_EXCLUDED_AS_INCOME` keys |
| `feature/settings/src/main/java/com/ivy/settings/SettingsState.kt` | Added boolean fields |
| `feature/settings/src/main/java/com/ivy/settings/SettingsEvent.kt` | Added `SetTransfers*` events |
| `feature/settings/src/main/java/com/ivy/settings/SettingsViewModel.kt` | State, init, persist, composable getters |
| `feature/settings/src/main/java/com/ivy/settings/SettingsScreen.kt` | Two `AppSwitch` rows |
| `temp/legacy-code/src/main/java/com/ivy/legacy/domain/action/wallet/CalcIncomeExpenseAct.kt` | Core logic: sums qualifying transfers, extracted into small helpers (`excludedTransferAdjustment`, `transferExpense`, `transferIncome`, `exchangeToBase`) |
| `feature/home/src/main/java/com/ivy/home/HomeViewModel.kt` | Reads both flags and passes to `CalcIncomeExpenseAct.Input` |
| `shared/ui/core/src/main/res/values/strings.xml` | 4 new strings |
| `CHANGELOG.md` | Added section |

## Architecture

`CalcIncomeExpenseAct` runs its normal pipeline (income + expense from standard transactions), then calls `excludedTransferAdjustment()` which:

1. Builds the set of excluded account IDs
2. For each included account, fetches all transactions in range
3. Filters to `Transfer` instances only
4. Checks `toAccount` / `fromAccount` against the excluded-account set
5. Applies currency exchange before summing

Self-transfers and included→included / excluded→excluded are correctly skipped.

## Detekt fixes

Original detekt failures (pre-existing on develop):
- `NestedBlockDepth` — resolved by extracting the triple-nested `if/for/for/if` into `excludedTransferAdjustment`, `transferExpense`, `transferIncome`
- `DataClassDefaultValues` — added `@Suppress("DataClassDefaultValues")` on `Input` (pattern used by sibling actions)
- `NoUnusedImports` — `BigDecimal` now genuinely used by the extracted helpers
- `ArgumentListWrapping` in `HomeViewModel` — each `getBoolean()` arg on its own line
