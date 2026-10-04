# Implementation Plan: Local Backup MVP

**Branch**: `002-local-backup-mvp` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-local-backup-mvp/spec.md`

## Summary

Automatic, recurring, immutable snapshots of the complete wallet dataset into one user-chosen local folder,
with a merge-only restore preceded by an automatic safety snapshot and a summary comparison.

The decisive finding from reading the existing code is that **the current import path cannot be reused for
restore**. `BackupDataUseCase.importJson` matches accounts and categories **by name**, rewrites the
snapshot's identifiers to the existing ones by string-replacing UUIDs across the whole JSON document, and
writes through several concurrent `saveMany` calls with no enclosing transaction. That behaviour contradicts
MVP-015 (merge by record identity, snapshot authoritative) and MVP-019 (all-or-nothing restore). The plan
therefore introduces a separate restore path and leaves the existing manual import untouched.

Export is in better shape: `generateJsonBackup()` and the existing zip writer are reused essentially as-is,
extended with a manifest entry carrying the summary and fingerprint that MVP-006 requires.

## Technical Context

**Language/Version**: Kotlin (JVM target per `buildSrc` convention plugins), Jetpack Compose

**Primary Dependencies**: Hilt, Room 2.6.1, Kotlinx Serialization, Arrow (`Either`), AndroidX WorkManager
2.9.1 + `androidx.hilt:hilt-work` 1.2.0 (both already in `gradle/libs.versions.toml`), Storage Access
Framework (`DocumentFile`, platform API — no new dependency)

**Storage**: Room (SQLite) as the source of wallet data; snapshots as zip archives in a user-chosen folder
reached through SAF; destination configuration in the existing DataStore

**Testing**: JUnit4 + Kotest assertions, `TestParameterInjector` for parameterised cases, MockK at
boundaries, `androidx.work:work-testing` for the scheduler, Room in-memory for restore tests

**Target Platform**: Android, minSdk 28, compileSdk 34

**Project Type**: Mobile application (multi-module Android)

**Performance Goals**: restore list and comparison open in under 2 s at the full retention limit without
reading snapshot bodies (SC-011); capture and restore of a 10,000-transaction wallet with no perceptible
delay in normal app use (SC-010)

**Constraints**: no new third-party dependency; snapshot must stay importable by the existing manual import
(MVP-002); no stored object may ever be modified (MVP-008); no capability beyond the four storage
operations (MVP-023); snapshots written now must remain valid for the full feature with no migration
(SC-013)

**Scale/Scope**: one destination, one device, 30 retained snapshots, ~4 new screens, 1 worker

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1 design.*

| Principle | Assessment |
|---|---|
| **I. Layered Architecture** | PASS by design. Storage contract and its SAF implementation live in `shared/data/core`; capture, restore, retention and comparison are use cases in `shared/domain`; screens and the worker live in a new `feature/backup` module. Snapshot entities never reach a Composable — ViewModels map to ViewState. No upward dependency is introduced. |
| **II. Typed Errors Over Exceptions** | PASS by design, but this is the principle most at risk. The existing `BackupDataUseCase` uses `error(...)` in four places and `getOrNull()` that discards failures. New code returns `Either<BackupError, T>` with a sealed hierarchy mirroring MVP-025's vocabulary. The existing throwing code is **not** refactored in this slice (out of scope); new code must not call into it in a way that lets a throw escape untyped. |
| **III. Illegal States Unrepresentable** | PASS by design. `SnapshotRef` carries a parsed name, never a raw string; `RestorePlan` is a sealed hierarchy rather than a struct with nullable fields; a snapshot that failed verification is a distinct type from one that is restorable, so the restore list cannot offer an unverified snapshot (MVP-005). |
| **IV. Test-First for Domain Logic** | PASS, mandatory here. Retention selection, name formatting and parsing, summary computation and merge semantics are pure logic and get tests before implementation. Property tests must pass an explicit `runTest(timeout = ...)` via `PropertyTestTimeout`. |
| **V. Green Gates Before Merge** | PASS. `./gradlew detekt`, `testDebugUnitTest` and `assembleDemo` before delivery; `verifyPaparazziDebug` if a module with screenshot tests is touched, reported honestly (a `NO-SOURCE` result proves nothing). |
| **Technology & Dependency Constraints** | PASS. No new library. WorkManager and `hilt-work` already exist in the catalog; SAF is a platform API. The new module uses the `ivy.feature` convention plugin. No code is added to `temp/`. |
| **Delivery Workflow** | Branch `feat/local-backup-mvp`, annotated `merged/...` record tag, `--no-ff` merge into `develop`, single `develop → main` PR. No AI attribution in any commit, PR or file. |

**No violations requiring justification.** One item is recorded in Complexity Tracking below because it adds
code that duplicates something already in the repository.

## Project Structure

### Documentation (this feature)

```text
specs/002-local-backup-mvp/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── backup-storage.md    # The four-operation contract (MVP-023)
│   └── snapshot-format.md   # On-disk snapshot layout and manifest
├── checklists/
│   └── requirements.md
└── tasks.md             # Created by /speckit-tasks, not here
```

### Source Code (repository root)

```text
shared/data/model/src/main/java/com/ivy/data/model/backup/
├── SnapshotRef.kt              # Parsed object name: instant, origin, device, extension
├── SnapshotOrigin.kt           # Scheduled | OnDemand | SafetyFor(restoreId)
├── SnapshotSummary.kt          # Counts, date range, balance totals, digest
└── BackupError.kt              # Sealed hierarchy, MVP-025 vocabulary

shared/data/core/src/main/java/com/ivy/data/backup/
├── BackupDataUseCase.kt        # EXISTING — reused for capture, untouched for import
└── local/
    ├── BackupStorage.kt        # The four operations (MVP-023)
    ├── SafFolderStorage.kt     # Sole implementation in this slice
    ├── BackupDestinationConfig.kt   # Persisted tree Uri + enabled state (DataStore)
    └── SnapshotNaming.kt       # Portable name format + parser (MVP-009)

shared/domain/src/main/java/com/ivy/domain/backup/
├── CaptureSnapshotUseCase.kt   # Capture + manifest + write + verify
├── ListSnapshotsUseCase.kt     # List, parse names, read manifests, exclude unverifiable
├── CompareSnapshotUseCase.kt   # Summary diff against current wallet (MVP-017)
├── RestoreSnapshotUseCase.kt   # Safety snapshot, then atomic merge (MVP-015/016/019)
├── ApplyRetentionUseCase.kt    # Keep-N with protections (MVP-010/011/012/013)
└── ComputeWalletSummaryUseCase.kt

feature/backup/src/main/java/com/ivy/backup/
├── BackupSettingsScreen.kt     # Status, folder picker, back-up-now (US2)
├── SnapshotListScreen.kt       # MVP-021
├── RestoreConfirmScreen.kt     # MVP-017/018
├── BackupViewModel.kt          # + ViewState mappers
├── BackupWorker.kt             # @HiltWorker CoroutineWorker
└── BackupScheduler.kt          # enqueueUniquePeriodicWork, called at app start
```

**Structure decision**: a new `feature/backup` module rather than extending `feature/settings`, because this
slice adds three screens and a worker and the constitution calls for one module per feature screen. No
`public/` API module is created: the only cross-module consumer is the app module calling `BackupScheduler`
at startup, so the module exposes that one type and keeps everything else `internal`. If a second consumer
appears, the `public/` split happens then.

## Phase 0 — Research

See [research.md](./research.md). Seven decisions were required; all are resolved and none is left as
NEEDS CLARIFICATION. The two consequential ones:

- **R2 — restore cannot reuse `importJson`.** Name-based account and category remapping plus a
  non-transactional multi-writer insert are both incompatible with the spec. A new restore path is written.
- **R6 — retention N = 30.** Published, fixed, and a strict superset of what any density curve would keep,
  so MVP-010 can be replaced later without having discarded a snapshot the curve would have retained.

## Phase 1 — Design & Contracts

See [data-model.md](./data-model.md), [contracts/](./contracts/) and [quickstart.md](./quickstart.md).

The storage contract is deliberately the narrowest thing that works: `write`, `list`, `read`, `delete` — the four operations MVP-023 allows and no more — with
no rename, no append, no conditional create, no lock, and no reliance on any timestamp the storage reports.
This is not generality for its own sake — it is the property that lets a cloud destination be added later
without touching capture, retention, listing, comparison or restore (MVP-024).

### Post-Design Constitution Re-Check

Re-evaluated after the data model and contracts were written. All principles still PASS. Two points
confirmed by the design rather than merely asserted before it:

- **Principle III** is satisfied structurally: `ListSnapshotsUseCase` returns `RestorableSnapshot` and
  `UnreadableSnapshot` as distinct types, so MVP-005's "never offer a snapshot that cannot be read" is
  enforced by the type system rather than by a check at the call site.
- **Principle II** is satisfied end-to-end: `BackupStorage` returns `Either<BackupError, T>` on every
  operation, so no SAF exception can reach a ViewModel untyped.

## Complexity Tracking

| Addition | Why it is needed | Simpler alternative rejected because |
|---|---|---|
| A second restore path alongside `BackupDataUseCase.importJson` | `importJson` matches accounts and categories by name and remaps the snapshot's UUIDs onto existing records by string substitution, then writes non-transactionally. MVP-015 requires merge by record identity with the snapshot authoritative; MVP-019 requires all-or-nothing. | Changing `importJson` in place would alter the behaviour of the existing user-facing manual import and the CSV import flow that shares it, in a slice whose scope is backup. The duplication is recorded as a debt to resolve when replace-mode restore lands and the two paths can be unified. |

## Open Risks Carried From the Spec

1. **MVP-019 atomicity** is the only data-destroying path here and the existing code offers no transaction
   to copy. Restore must run inside a single Room transaction; the safety snapshot is the second line of
   defence, never the first.
2. **MVP-009 portable names** fail only on removable media, which ordinary testing will not exercise. A
   deliberate test against a FAT32/exFAT volume, or at minimum a unit test asserting the character set, is
   required before delivery.
3. **MVP-006 summaries** are irreversible per snapshot. The manifest must be part of the first capture
   implemented, not added afterwards.
4. **MVP-031 / SC-008** must not become a promise about backup frequency. The requirement is that
   suppression is *visible*, not prevented.
