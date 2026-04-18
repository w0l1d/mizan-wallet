# Zakat Knowledge Base

Canonical reference for the zakat feature. Captures scholarly positions (from the Arabic NotebookLM
source below) and maps them to the concrete code that implements them, so future contributors can
verify Sharia-compliance without re-deriving it each time.

## Source

- **NotebookLM**: `a1bde0b5-be1f-4e1b-91ce-bba1078f1c40`
- **Title**: زكاة الراتب في الفقه الإسلامي المعاصر وطرق احتسابها (*Salary Zakat in Contemporary Islamic
  Jurisprudence and Methods of Calculation*)
- **Queried**: 2026-04-18

## Files

| File | Purpose |
|------|---------|
| [`salary-zakat-rulings.md`](salary-zakat-rulings.md) | Summary of scholarly positions used to validate the current implementation. |
| [`worked-examples.md`](worked-examples.md) | Numerical examples, also used as fixtures for the unit test suite. |
| [`code-mapping.md`](code-mapping.md) | Each principle → file:line in the codebase. |

## Scope

This KB covers **monetary wealth zakat** (نقود ومدخرات) — salary savings, cash in accounts, and
monetary gold/silver holdings. Out of scope (not implemented by the app and not covered here):

- Zakat on trade goods (زكاة عروض التجارة)
- Zakat on livestock (الأنعام)
- Zakat on agricultural produce (الزروع والثمار)
- Zakat on minerals and treasure (المعادن والركاز)

## How to update

If a scholarly ruling changes or a new edge case emerges:

1. Re-query NotebookLM (`notebooklm use a1bde0b5-be1f-4e1b-91ce-bba1078f1c40 && notebooklm ask "..."`).
2. Update the relevant file and add the new source excerpt.
3. If code behavior must change, update both the code and `code-mapping.md` in the same commit.
4. If a worked example changes, update the corresponding test case so they stay in lockstep.
