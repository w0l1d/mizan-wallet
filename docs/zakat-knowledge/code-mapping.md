# Code Mapping

Each scholarly rule from [`salary-zakat-rulings.md`](salary-zakat-rulings.md) → the file:line that
implements it.

Update this file whenever the referenced code moves.

## Core calculation

`feature/zakat/src/main/java/com/ivy/zakat/usecase/CheckZakatNisabUseCase.kt`

| Rule | Location |
|------|----------|
| Nisab: 85g gold, 595g silver | lines 19-20 (`GoldNisabGrams`, `SilverNisabGrams`) |
| Rate: 2.5% | line 21 (`ZakatRate`) |
| Wealth = accounts + physical gold + physical silver | line 51 |
| Zakatable = wealth − deductions, floored at 0 | line 52 |
| Nisab threshold = 85g × gold price (or 595g × silver price) | lines 55-58 |
| Due when zakatable ≥ nisab AND hawl complete | lines 62, 67-71 |
| Amount = zakatable × 0.025 | line 68 |

## State machine

`feature/zakat/src/main/java/com/ivy/zakat/usecase/CheckZakatNisabUseCase.kt`, `computeNewState`

| Rule | Location |
|------|----------|
| `CONFIGURED → NISAB_REACHED` when wealth first crosses nisab | lines 171-173 |
| `NISAB_REACHED → CONFIGURED` when wealth dips below nisab mid-hawl (interrupted hawl restarts) | lines 179-181 |
| `NISAB_REACHED → HAWL_COMPLETE` after 1 Hijri year | lines 184-185 |
| `HAWL_COMPLETE` persists until payment (no premature reset) | lines 191-194 |
| `ZAKAT_PAID` → new cycle when wealth ≥ nisab | lines 196-201 |

## Hijri hawl

`feature/zakat/src/main/java/com/ivy/zakat/usecase/HijriCalendarUtils.kt`

| Rule | Location |
|------|----------|
| Hawl duration = 1 Hijri lunar year (`HijrahDate.plus(1, YEARS)`) | `hawlEndDateMillis` |
| Regional offset (`hijriOffset` in days) applied to the start date | `hawlEndDateMillis` |
| Fallback to 354 days if `HijrahDate.from` fails | `hawlEndDateMillis` catch branch |
| `isHawlComplete` = `now >= hawlEnd` | `isHawlComplete` |

## Metal prices

`feature/zakat/src/main/java/com/ivy/zakat/usecase/FetchMetalPricesUseCase.kt`

| Rule | Location |
|------|----------|
| XAU (gold) rate → SAR/g via troy ounce (31.1035g) | `goldPricePerGram` calculation |
| XAG (silver) rate → SAR/g via troy ounce | `silverPricePerGram` calculation |
| Auto fetch null → fall back to manual price | `CheckZakatNisabUseCase.getEffectivePrices` lines 150-155 |
| Zero or negative rate → treat as unavailable (null) | `takeIf { it > 0 }` |

## Payment generation

`feature/zakat/src/main/java/com/ivy/zakat/usecase/GenerateZakatExpenseUseCase.kt`

| Rule | Location |
|------|----------|
| Creates an `Expense` transaction in the main ledger | `generate` function |
| Links `ZakatPayment` row to the config | `zakatPaymentRepository.save(payment)` |
| Sets state → `ZAKAT_PAID` (starts a new cycle on next check) | `zakatConfigRepository.save(config.copy(trackingState = ZAKAT_PAID))` |
