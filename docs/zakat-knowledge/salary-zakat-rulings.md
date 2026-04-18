# Salary Zakat — Scholarly Positions

Salary is classified as **مال مستفاد** (newly acquired wealth): money the Muslim newly owns, such
as wages, gifts, or inheritance.

## 1. When does zakat become due?

### Majority (جمهور) — implemented
Salary is **not** subject to zakat the moment it is received. Two conditions must hold:

1. Total monetary wealth reaches the **nisab** threshold.
2. A full **hawl** (1 Hijri lunar year) elapses while wealth remains ≥ nisab.

Endorsed by the Permanent Committee for Iftā' (Saudi Arabia), Ibn Bāz, Ibn ʿUthaymīn, and the
major contemporary fiqh councils.

### Minority
Muḥammad al-Ghazālī and (in one of his positions) Yūsuf al-Qaraḍāwī held that zakat is due
immediately on receipt if the amount reaches nisab, by analogy with crops. **Contemporary
scholars consider this view weak** (مرجوح) because it violates the settled rule that money
requires hawl.

## 2. Deduction of essential expenses

Zakat is due only on wealth **surplus to essential needs** (الحاجات الأصلية) — housing, food,
healthcare, clothing — and net of **currently due debts** (الديون الحالة).

- An employee who spends the entire salary on their family's needs each month and does not
  accumulate savings has **no zakat obligation**.
- At calculation time, annual expenses are already implicitly deducted (only residual savings
  remain). Debts currently due are subtracted explicitly from the zakatable base.

## 3. Calculation methods

### (a) الحول المستقل — independent hawl per deposit
Treat each month's savings as a separate capital with its own hawl. Zakate Muḥarram-deposited
money in the following Muḥarram, etc. **Most precise but operationally painful** (مشقة) — not
practical without tooling.

### (b) الموعد السنوي الموحد — unified annual date (implemented by the app)
Pick a single day each year (e.g., 1 Ramaḍān). On that day, total all savings regardless of
when each portion was deposited, and pay 2.5% on the total. Amounts that haven't yet completed
their hawl are treated as **تعجيل الزكاة** (paying zakat early), which is permitted.

This is the method the app implements. Rationale:
- Easier for the user (one date, one calculation).
- More beneficial to the poor (they receive funds sooner).
- Endorsed by contemporary fiqh councils as valid.

## 4. Nisab and rate

| Parameter | Value |
|-----------|-------|
| Gold nisab | 85 grams |
| Silver nisab | 595 grams |
| Rate | 2.5% (ربع العشر) |

Some contemporary researchers argue the **gold nisab** is the more appropriate modern reference
for salary zakat, because silver's purchasing power has eroded relative to historical times. The
app supports both and lets the user choose.

## 5. Pension and end-of-service gratuity

### During employment
No zakat on amounts withheld for pension or end-of-service, because the employee lacks
**ملك تام** (complete ownership) — they cannot receive or dispose of the funds.

### After receipt
Once the decision is issued and the funds are actually received, they become مال مستفاد and
are zakated on the user's annual date like any other accumulated savings.

## 6. Edge cases covered by the implementation

| Situation | Handling |
|-----------|----------|
| Wealth drops below nisab mid-hawl | Hawl is **interrupted and restarts** when wealth next crosses nisab. |
| Deductions exceed wealth | Zakatable base is **floored at 0**. No negative zakat. |
| User pays zakat | State → `ZAKAT_PAID`; a new cycle starts only once wealth crosses nisab again. |
| Hijri calendar regional offset | Supported via `hijriOffset` (±days) to match local moon-sighting conventions. |
| Auto metal-price fetch fails | Fall back to the user's manually entered prices. |
