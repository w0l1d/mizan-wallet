# Specification Quality Checklist: Local Backup MVP

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-03
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Validation Notes

**Iteration 1** — initial pass against the written spec.

Two items failed and were corrected before this checklist was finalised:

1. *Scope is clearly bounded.* The first draft described deferrals only as a table, which stated what was
   cut but not what the cut costs a user. Added MVP-035 to MVP-037 as explicit negative requirements, so a
   deferral is testable rather than merely documented, and added the "Merge alone is sufficient" assumption
   naming the one scenario this slice genuinely cannot serve (removing records that should not exist).
2. *Success criteria are measurable.* "Users can confirm backups are working" was unverifiable. Replaced by
   SC-008, bounded by what is on screen and how quickly.

**Cross-reference integrity**: every `FR-NNN` citation in this spec (50 distinct) was verified to resolve to
a defined requirement in `specs/001-wallet-backup-and-recovery/spec.md`. Requirement numbering is contiguous
(MVP-001…037, SC-001…013) with no gaps or duplicates.

## Scope Boundary Notes

This spec is a delivery slice of feature 001, not an independent feature. Two properties carry the risk:

- **No stored object written by this slice may need changing when the deferred capabilities arrive.**
  SC-013 states this as a success criterion. The three non-deferrable decisions — the storage contract
  (MVP-023), summaries embedded at capture (MVP-006), and portable names (MVP-009) — exist solely to
  guarantee it. If any one is compromised during planning or implementation, SC-013 fails and the slice
  stops being a safe subset.
- **MVP-010 relaxes retention from a density curve to a fixed count.** This is the one requirement that is
  weaker rather than merely narrower. It is safe because the count rule keeps a superset of recent
  snapshots; replacing it later removes nothing a user would have had.

## Risks Carried Into Planning

1. **MVP-019 (all-or-nothing restore) is the only data-destroying path in this slice.** Merge overwrites
   records present in both. It must be atomic at the data layer, not guarded by the safety snapshot —
   relying on the safety snapshot as the primary guard makes recovery depend on the operation that just
   failed.
2. **MVP-009 (portable names) fails only on removable media.** Ordinary testing will not catch a violation,
   and immutability (MVP-008) forbids correcting it by renaming. Needs a deliberate test.
3. **MVP-006 (summaries at capture) is irreversible per snapshot.** A snapshot written without a summary can
   never acquire one. If this is implemented late, every snapshot written before it is permanently degraded.
4. **MVP-031/SC-008 depend on background execution the platform does not guarantee.** The requirement is
   written to make suppression visible rather than to prevent it; planning must not quietly convert it into
   a promise about backup frequency.

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- All 16 items pass as of 2026-10-03.
