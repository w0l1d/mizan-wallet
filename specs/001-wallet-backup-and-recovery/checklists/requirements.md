# Specification Quality Checklist: Wallet Backup and Recovery

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-28
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

## Notes

### Iteration 1 — 2026-09-28

Two items failed.

**"No [NEEDS CLARIFICATION] markers remain"** — one marker on restore semantics. Escalated
to the user rather than guessed, because the existing manual import merges by upserting, so
inheriting that behaviour would silently leave behind records created after the snapshot was
taken — not what a user asking to "go back to Monday" expects.

**"No implementation details leak"** — resolved inline during drafting. Named provider APIs,
scope identifiers, SDK names, scheduler and storage-framework class names, and byte-level
format naming were all removed in favour of user-facing language.

### Iteration 2 — 2026-09-28

Both failing items now pass.

**Clarification resolved.** The user chose *per-restore choice* (replace or merge), and added
requirements with it: a reviewable history of every backup, restore and undo; comparison
before restoring; undo of a restore; undo itself recorded; the record stored alongside the
snapshots at each destination.

**Implementation-detail leak resolved.** Naming **Google Drive** (the user selects it by name
in the app) and the **private vs. visible** distinction (the user chooses between them and the
consequences differ for them) are user-visible product choices, not mechanisms.

**Extensibility added** after the user required the destination manager be reimplementable for
future providers. Phrased as constraints on behaviour and vocabulary rather than as an
interface definition — the interface itself is a planning artefact.

### Iteration 3 — 2026-09-28 (history record structure)

Triggered by a design discussion that exposed a defect in the iteration-2 spec rather than a
gap in it. Feature renamed from *Automatic Wallet Backup* to **Wallet Backup and Recovery**,
and the directory from `001-automatic-wallet-backup` to `001-wallet-backup-and-recovery`: the
old name described only the P1 story, while restore, comparison, undo and history had grown to
roughly half the spec. "Timeline" was renamed "history" throughout, because the record is no
longer one object and the word implied a single ordered file.

**The defect.** Iteration 2 specified the history as one mutable file appended to by every
device, which made it the sole exception to immutability (old FR-007) and the only place two
devices could conflict — reintroducing, in miniature, the concurrency problem the no-sync
decision existed to eliminate. Two remedies were considered and rejected on evidence:

- *Compare a fingerprint before writing.* Detects a concurrent write but cannot prevent one:
  no destination in scope offers an atomic compare-and-swap, so the check and the write cannot
  be a single step and both devices can observe an unchanged record.
- *A lock object that fails if it already exists.* The primitive is absent where it is needed —
  filenames are not unique keys at the cloud destinations, and the local storage framework
  de-duplicates a colliding name instead of refusing it. Its failure mode is also inverted: a
  process that dies holding a lock halts all future backups silently, which is worse than the
  loss it prevents.

**The resolution — structural, not algorithmic.** Events are now independently stored objects
that are never modified (FR-028), so two devices never address the same object and there is
nothing to serialise. Consequences recorded across the spec:

| Change | Requirements |
|---|---|
| Immutability now absolute; the record is no longer an exception | FR-008 |
| Events independently stored; recording never reads or modifies | FR-028 |
| Union by event identity, order-independent and idempotent | FR-035 |
| Per-device total order; cross-device order presentational only | FR-036, FR-037 |
| Causality by reference, never by comparing clocks | FR-033 |
| Consolidation: interruptible, lock-free, duplicates never gaps | FR-038 |
| Integrity: fingerprints on snapshots and on each event | FR-006, FR-030 |
| Missing vs. corrupt distinguished for the user | FR-007 |
| Comparison never reads a snapshot (makes SC-013 reachable) | FR-031, FR-046 |
| No capability beyond the four storage operations | FR-061 |
| Portable object names across all destinations | FR-010 |

Added: SC-018 (no interrupted write ever loses an event or destroys a snapshot), five edge
cases (torn event write, concurrent consolidation, interrupted consolidation, clock set
backwards, user-moved files in a browsable folder), acceptance scenario 2.9, the Device
Identifier entity, and four assumptions (no compare-and-swap/create-if-absent/rename/locking/
trustworthy timestamps anywhere; mutual exclusion deliberately not attempted; device identifier
is app-generated and permission-free; consolidation bounded by object count, not size).

All 16 items pass. 67 functional requirements and 18 success criteria, contiguous, all
cross-references verified.

### Risks carried into planning

1. ~~The history record is the one mutable artefact and the only multi-device conflict point.~~
   **Retired at iteration 3.** No object is mutable and no two writers share an object. What
   replaces it is a *correctness obligation*: FR-035's union must be genuinely idempotent and
   order-independent, and FR-038's consolidation must be crash-safe by ordering its steps
   (write the consolidated object and verify it before removing any object it absorbed). If
   planning inverts that order, a crash loses history.
2. **Replace mode is new behaviour.** The existing import only upserts; nothing in the codebase
   deletes the whole dataset. FR-025 (all-or-nothing) applies to a code path that does not exist
   yet and is the highest-risk item in the feature.
3. **FR-031 (summaries recorded at capture)** is what makes SC-013 and FR-046 achievable. If
   planning drops it and computes comparisons by reading snapshots, SC-013 fails.
4. **SC-017 (one shared suite across all three destinations)** is the practical test that FR-059
   and FR-062 were honoured. Build it as a shared conformance suite from the start, not
   retrofitted. FR-061 is testable the same way: a destination offering only the four operations
   must pass the whole suite.
5. **FR-010 (portable names) is easy to violate late.** Colons and other characters accepted by
   internal storage are rejected by removable media, and the failure surfaces only on a user's
   SD card. The name grammar must be fixed once, early, and shared by all destinations.

### Resolution status

All 16 items pass. Spec is ready for `/speckit-clarify` (optional) or `/speckit-plan`.
