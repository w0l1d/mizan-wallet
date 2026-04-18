# Worked Examples

Numerical scenarios drawn from the NotebookLM source (see [`README.md`](README.md)). Each
example is also a fixture for a unit test in
`feature/zakat/src/test/java/…/CheckZakatNisabUseCaseTest.kt` — **do not drift**: if you change
numbers here, update the test; if the scholarly source changes a rule, update both.

Assumed nisab value (for readability): **85g gold × 250 SAR/g = 21,250 SAR** in examples that
don't specify otherwise. Currency is illustrative; the code is currency-agnostic.

---

## Example 1 — Fixed salary, partial savings (unified annual date)

| Input | Value |
|-------|-------|
| Monthly salary | 10,000 SAR |
| Monthly essential expenses | 7,000 SAR |
| Monthly savings | 3,000 SAR |
| Cumulative savings after 12 months | 36,000 SAR |
| Nisab | 21,250 SAR (85g gold × 250) |

Zakat = 36,000 × 0.025 = **900 SAR**.

---

## Example 2 — Irregular income (self-employed)

| Input | Value |
|-------|-------|
| Annual gross income | 150,000 SAR (variable by month) |
| Annual essential expenses | 90,000 SAR |
| Net savings on zakat date | 60,000 SAR |
| Nisab | 21,250 SAR |

Zakat = 60,000 × 0.025 = **1,500 SAR**.

Notes: variability of monthly income is irrelevant under the unified-date method — only the
balance on the chosen date matters.

---

## Example 3 — Mixed assets (cash + physical gold)

| Input | Value |
|-------|-------|
| Savings from salary | 15,000 SAR |
| Cash on hand | 5,000 SAR |
| Physical gold (investment, not jewelry worn) | 100 g |
| Gold price | 250 SAR/g |
| Physical gold value | 25,000 SAR |
| Total zakatable base | 45,000 SAR |
| Nisab | 21,250 SAR |

Zakat = 45,000 × 0.025 = **1,125 SAR**.

Note: gold held as **monetary investment** is zakatable. Gold held as **jewelry for personal
adornment** is a separate juristic discussion — the current app treats all `physicalGoldGrams`
as monetary; users who own adornment jewelry should exclude it from the input.

---

## Example 4 — Debts subtracted from base

| Input | Value |
|-------|-------|
| Total savings | 50,000 SAR |
| Currently due debt (installment due now) | 10,000 SAR |
| Zakatable base | 50,000 − 10,000 = 40,000 SAR |
| Nisab | 21,250 SAR |

Zakat = 40,000 × 0.025 = **1,000 SAR**.

Note: only **currently due** debts (الديون الحالة) are deducted. Long-term installments that
aren't due are not.

---

## Example 5 — Mid-year dip below nisab interrupts hawl

| Date (Hijri) | Balance | State |
|--------------|---------|-------|
| 1 Muḥarram | 30,000 SAR (above nisab 20,000) | `NISAB_REACHED`, hawl started |
| 1 Rajab (mid-year) | 5,000 SAR (below nisab after a withdrawal) | `CONFIGURED`, hawl reset |
| 1 Dhū al-Ḥijjah (year-end) | 40,000 SAR (new savings) | `NISAB_REACHED` — new hawl starts here |

Zakat on the original date = **0** (hawl broken). Zakat on the original date of the next cycle
is owed only if wealth remained ≥ nisab for a full Hijri year from the day it was restored.

---

## Example 6 — Deductions exceed wealth (floor at 0)

Not from the NotebookLM source; added as a programmer-safety case.

| Input | Value |
|-------|-------|
| Total wealth | 5,000 SAR |
| Deductions | 10,000 SAR |
| Zakatable base | max(0, 5,000 − 10,000) = 0 |

Zakat = **0**. No negative values.

---

## Example 7 — Silver nisab standard

| Input | Value |
|-------|-------|
| `nisabStandard` | `SILVER` |
| Silver price | 3 SAR/g |
| Silver nisab | 595 g × 3 = 1,785 SAR |
| Total wealth | 10,000 SAR |

10,000 ≥ 1,785 → above nisab. If hawl complete: Zakat = 10,000 × 0.025 = **250 SAR**.

Silver nisab is typically a lower threshold than gold — choosing silver means zakat becomes due
sooner, which is more favorable to recipients and more conservative for the payer.
