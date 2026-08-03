# MZ-002 — Accounts Screen Date/Period Filter

**Status:** ✅ Done (on `develop`)
**Branch:** `feat/accounts-date-filter` → `6ddf55b7`
**Date:** 2026-07

## What was done

Added a period/date selector to the Accounts tab, matching the pattern from the home screen:

- **Month navigation** — swipe left/right or tap to open `ChoosePeriodModal`
- **Custom range** — any month, custom `from→to`, or "all time"
- **Reset button** — returns to the default view
- **Paparazzi screenshot test** — covers custom-period state with visible reset button

## Default behaviour (restored by reset)

| Component | Default range |
|-----------|--------------|
| Per-account **balance** | All-time (up to now) — unchanged from pre-filter behaviour |
| Per-account **income/expense** | Current month — unchanged from pre-filter behaviour |

When the user actively selects a period (via modal or month arrows), **both** balance and income/expense share that range.

## Files

| File | Change |
|------|--------|
| `feature/accounts/src/main/java/com/ivy/accounts/AccountsState.kt` | Added `period`, `periodDisplayText`, `isCustomPeriod` fields |
| `feature/accounts/src/main/java/com/ivy/accounts/AccountsEvent.kt` | Added `SetPeriod`, `SelectNextMonth`, `SelectPreviousMonth`, `ResetPeriod` events |
| `feature/accounts/src/main/java/com/ivy/accounts/AccountsViewModel.kt` | Period state, `startInternally()` uses `selectedPeriod`, split balance/income-expense ranges |
| `feature/accounts/src/main/java/com/ivy/accounts/AccountsTab.kt` | Period selector header with reset button; `ChoosePeriodModal` integration |
| `temp/legacy-code/src/main/java/com/ivy/legacy/domain/action/viewmodel/account/AccountDataAct.kt` | `Input` now takes separate `balanceRange` (nullable) and `incomeExpenseRange` |
| `temp/legacy-code/src/main/java/com/ivy/legacy/domain/deprecated/logic/WalletAccountLogic.kt` | Updated call to pass both ranges |
| `shared/ui/core/src/main/res/values/strings.xml` | Added `reset` string |
| `feature/accounts/src/test/java/com/ivy/accounts/AccountsTabPaparazziTest.kt` | Reset-button snapshot test |
| `feature/accounts/src/test/snapshots/images/` | Updated + new Paparazzi snapshot PNGs |
| `CHANGELOG.md` | Added section |

## Key design decision

The original plan doc (Feature C, Step C4) intended balance to be "cumulative up to the END of the selected period." The first implementation accidentally passed the full `[from, to]` window to balance, meaning selecting July would show *only July's net movement* (not total up to July 31). This was corrected by splitting ranges: `balanceRange` is `null` (all-time) by default, and only narrowed when the user explicitly picks a period.
