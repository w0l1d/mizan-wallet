---

description: "Task list for the Local Backup MVP"
---

# Tasks: Local Backup MVP

**Input**: Design documents from `/specs/002-local-backup-mvp/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: included, and not optional here. Constitution Principle IV makes tests mandatory for every use
case, mapper and calculation, written against behaviour and before the implementation they cover.

**Organization**: by user story. US1 is the whole point of the slice and ships alone; US2 makes it honest.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel — different files, no dependency on an incomplete task
- **[Story]**: US1 or US2; setup, foundational and polish tasks carry no story label

## Path conventions

| Layer | Path |
|---|---|
| Domain models | `shared/data/model/src/main/kotlin/com/ivy/data/model/backup/` |
| Storage | `shared/data/core/src/main/java/com/ivy/data/backup/local/` |
| Use cases | `shared/domain/src/main/java/com/ivy/domain/usecase/backup/` |
| Feature UI | `feature/backup/src/main/java/com/ivy/backup/` |

Module path syntax: `shared/data/core` → `:shared:data:core`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: make the new module exist and build before anything is written into it.

- [ ] T001 Create the `feature/backup` module directory with `build.gradle.kts` applying `id("ivy.feature")` and `namespace = "com.ivy.backup"`, depending on `projects.shared.base`, `projects.shared.data.core`, `projects.shared.domain`, `projects.shared.ui.core`, `projects.shared.ui.navigation`, mirroring `feature/settings/build.gradle.kts`
- [ ] T002 Register `include(":feature:backup")` in `settings.gradle.kts` in alphabetical position, and add `implementation(projects.feature.backup)` to `app/build.gradle.kts`
- [ ] T003 Create the four package directories listed under Path conventions above, each with a `.gitkeep`, so later `[P]` tasks never race on directory creation
- [ ] T004 Verify the empty module builds: `./gradlew :feature:backup:assembleDebug` and `./gradlew detekt`

**Checkpoint**: `:feature:backup` compiles and is wired into the app.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the domain vocabulary, the storage contract, and two changes to existing code that every
later task depends on.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

### Domain types

- [ ] T005 [P] Create `BackupError` as a `sealed interface` in `shared/data/model/src/main/kotlin/com/ivy/data/model/backup/BackupError.kt` with `NotConfigured`, `AccessDenied`, `OutOfSpace`, `WriteFailed`, `SnapshotUnreadable`, `RestoreFailed`, per [data-model.md](./data-model.md)
- [ ] T006 [P] Create `SnapshotOrigin` (`Scheduled` | `Manual` | `Safety`) in `shared/data/model/src/main/kotlin/com/ivy/data/model/backup/SnapshotOrigin.kt` — a sealed type, not a boolean flag (Principle III)
- [ ] T007 [P] Create `SnapshotSummary` in `shared/data/model/src/main/kotlin/com/ivy/data/model/backup/SnapshotSummary.kt` with the five fields from [data-model.md](./data-model.md); `newestTransactionAt` is nullable, never epoch-zero
- [ ] T008 Create `SnapshotRef` in `shared/data/model/src/main/kotlin/com/ivy/data/model/backup/SnapshotRef.kt` carrying `name`, transient `uri`, `capturedAt`, `origin`, `summary`, `sizeBytes` (depends on T006, T007)

### Snapshot format

- [ ] T009 [P] Write failing tests for snapshot naming in `shared/data/core/src/test/java/com/ivy/data/backup/local/SnapshotNamingTest.kt`: names sort chronologically as strings, contain no colon and no character outside `A–Za–z0–9-.`, round-trip to origin and instant, and two same-second captures produce different names
- [ ] T010 Implement `SnapshotNaming` in `shared/data/core/src/main/java/com/ivy/data/backup/local/SnapshotNaming.kt` per [contracts/snapshot-format.md](./contracts/snapshot-format.md) until T009 passes
- [ ] T011 [P] Write failing tests for the manifest in `shared/data/core/src/test/java/com/ivy/data/backup/local/SnapshotManifestTest.kt`: round-trips, **ignores unknown fields**, and rejects an unrecognised `formatVersion` as unreadable rather than guessing (MVP-007)
- [ ] T012 Implement `SnapshotManifest` and its `kotlinx.serialization` form in `shared/data/core/src/main/java/com/ivy/data/backup/local/SnapshotManifest.kt` until T011 passes

### Changes to existing code

- [ ] T013 Relax the "exactly one unzipped file" assertion in `shared/data/core/src/main/java/com/ivy/data/backup/BackupDataUseCase.kt` to "exactly one `.json` entry that is not `manifest.json`", keeping every existing behaviour otherwise — **MVP-002 is unsatisfiable without this** ([contracts/snapshot-format.md](./contracts/snapshot-format.md))
- [ ] T014 Add a test in `shared/data/core/src/test/java/com/ivy/data/backup/BackupDataUseCaseTest.kt` proving the existing manual import still accepts both a legacy single-entry archive and a new two-entry archive containing `manifest.json`
- [ ] T015 Add a transaction helper to `shared/data/core/src/main/java/com/ivy/data/db/IvyRoomDatabase.kt` (or an injectable wrapper beside it) exposing a single `suspend fun <T> withTransaction(block: suspend () -> T): T` — the class currently has none and MVP-019 cannot be met without one

### Storage contract

- [ ] T016 Define `BackupStorage` in `shared/data/core/src/main/java/com/ivy/data/backup/local/BackupStorage.kt` with **exactly four operations** — `write`, `list`, `read`, `delete` — each returning `Either<BackupError, T>`, per [contracts/backup-storage.md](./contracts/backup-storage.md). Adding a fifth operation violates MVP-023
- [ ] T017 Build `FakeBackupStorage` in `shared/data/core/src/testFixtures/java/com/ivy/data/backup/local/FakeBackupStorage.kt` reproducing the **awkward** real behaviours: create silently de-duplicates a colliding name, delete-of-missing succeeds, and an object can vanish between `list` and `read`. A fake that is nicer than reality tests nothing
- [ ] T018 [P] Create `BackupDestinationConfig` in `shared/data/core/src/main/java/com/ivy/data/backup/local/BackupDestinationConfig.kt` persisting only `treeUri` and `configuredAt` to DataStore, following `shared/data/core/src/main/java/com/ivy/data/datastore/Datastore.kt`. Reachability is **not** stored — it is discovered on use
- [ ] T019 Implement `SafFolderStorage` in `shared/data/core/src/main/java/com/ivy/data/backup/local/SafFolderStorage.kt` against `DocumentFile`, including `takePersistableUriPermission` on selection, and **verifying the created object's actual name matches the requested name** after every write (research R1)
- [ ] T020 Bind `BackupStorage` to `SafFolderStorage` in a Hilt module at `shared/data/core/src/main/java/com/ivy/data/di/BackupStorageModule.kt`
- [ ] T021 Write instrumentation tests in `shared/data/core/src/androidTest/java/com/ivy/data/backup/local/SafFolderStorageTest.kt` covering write/list/read/delete, grant survival across process death, and a revoked grant surfacing as `AccessDenied`

**Checkpoint**: snapshots can be written to and read from a real folder; no feature behaviour exists yet.

---

## Phase 3: User Story 1 — Recover from a self-inflicted data accident (Priority: P1) 🎯 MVP

**Goal**: a user picks a folder, backups accumulate on their own, and a bad day is undone by merging an
older snapshot back in.

**Independent test**: pick a folder, let a scheduled backup run, delete or corrupt data in the app, restore
the most recent snapshot, and confirm the affected records match their pre-accident contents while records
created afterwards survive untouched.

### Tests first (Principle IV)

- [ ] T022 [P] [US1] Write failing tests for `ComputeWalletSummaryUseCase` in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/ComputeWalletSummaryUseCaseTest.kt`, including the empty wallet (`newestTransactionAt` is null, counts are zero)
- [ ] T023 [P] [US1] Write failing tests for `ApplyRetentionUseCase` in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/ApplyRetentionUseCaseTest.kt` asserting every invariant in [data-model.md](./data-model.md): `keep + delete` equals the input exactly, `keep` is never empty, no `Safety` snapshot is ever in `delete`, and nothing is deleted when the newer write failed
- [ ] T024 [P] [US1] Write failing tests for `ListSnapshotsUseCase` in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/ListSnapshotsUseCaseTest.kt`: newest first by name, a corrupt object is counted as unreadable and never listed as restorable, and an empty folder is a success rather than an error
- [ ] T025 [P] [US1] Write failing tests for `CompareSnapshotUseCase` in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/CompareSnapshotUseCaseTest.kt` proving the comparison is computed from two summaries only and never opens the data document
- [ ] T026 [P] [US1] Write failing tests for `CaptureSnapshotUseCase` in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/CaptureSnapshotUseCaseTest.kt`: the manifest's summary matches the wallet, retention runs only after a confirmed write, and a failed write leaves no partial object and deletes nothing
- [ ] T027 [P] [US1] Write failing tests for `RestoreSnapshotUseCase` in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/RestoreSnapshotUseCaseTest.kt`: snapshot-authoritative merge **by record identity**, records added after the snapshot survive, a mid-restore failure leaves the wallet entirely unchanged, a digest mismatch is refused **before** the safety snapshot is taken, and a newer schema version is refused with a stated reason

### Domain implementation

- [ ] T028 [US1] Implement `ComputeWalletSummaryUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/ComputeWalletSummaryUseCase.kt`
- [ ] T029 [US1] Implement `CaptureSnapshotUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/CaptureSnapshotUseCase.kt`, reusing `BackupDataUseCase.generateJsonBackup()` unchanged for the data entry and zipping it with the manifest (depends on T010, T012, T016, T028)
- [ ] T030 [US1] Implement `ApplyRetentionUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/ApplyRetentionUseCase.kt` with N = 30 (research R6), invoked by `CaptureSnapshotUseCase` only after the new snapshot's write has returned successfully
- [ ] T031 [P] [US1] Implement `ListSnapshotsUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/ListSnapshotsUseCase.kt`, reading manifests only — never data documents
- [ ] T032 [P] [US1] Implement `CompareSnapshotUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/CompareSnapshotUseCase.kt` producing `SnapshotComparison`
- [ ] T033 [US1] Implement `RestoreSnapshotUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/RestoreSnapshotUseCase.kt` as a **new** merge path — verify digest and schema version, capture the safety snapshot and confirm it is written, then merge inside a single `withTransaction` block, writing sequentially. Do **not** call `BackupDataUseCase.importJson`: it matches accounts and categories by name and is non-transactional (research R2)
- [ ] T034 [US1] Add a single-flight guard to `shared/domain/src/main/java/com/ivy/domain/usecase/backup/RestoreSnapshotUseCase.kt` so a second restore cannot start while one is running (MVP-020), with a test in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/RestoreSnapshotUseCaseTest.kt` asserting the second call is rejected rather than queued

### Scheduling

- [ ] T035 [US1] Implement `BackupWorker` in `feature/backup/src/main/java/com/ivy/backup/BackupWorker.kt` as a `@HiltWorker` `CoroutineWorker`, following `temp/legacy-code/.../notification/TransactionReminderWorker.kt`, returning `Result.retry()` for transient failures and `Result.failure()` for `NotConfigured`
- [ ] T036 [US1] Implement `BackupScheduler` in `feature/backup/src/main/java/com/ivy/backup/BackupScheduler.kt` using `enqueueUniquePeriodicWork` with `ExistingPeriodicWorkPolicy.KEEP`, a 24-hour period and `setRequiresBatteryNotLow(true)` — `KEEP` is what stops re-enqueueing at app start from resetting the schedule, and periodic work's lack of backfill is what satisfies MVP-030 (research R7)
- [ ] T037 [US1] Call `BackupScheduler` from app startup in `app/src/main/java/com/ivy/wallet/IvyAndroidApp.kt` (or the existing startup path), scheduling only when a destination is configured
- [ ] T038 [US1] Add `shared/data/core`/`feature/backup` WorkManager tests in `feature/backup/src/test/java/com/ivy/backup/BackupSchedulerTest.kt` using `androidx-work-testing` to assert the unique-work policy and constraints

### UI

- [ ] T039 [P] [US1] Create `BackupViewState` in `feature/backup/src/main/java/com/ivy/backup/BackupViewState.kt` as a sealed hierarchy — `NotConfigured`, `Loading`, `Ready`, `Failed` — not a data class of nullable fields (Principle III)
- [ ] T040 [US1] Implement `BackupViewModel` in `feature/backup/src/main/java/com/ivy/backup/BackupViewModel.kt` mapping use-case results to `BackupViewState`, holding no business rules of its own
- [ ] T041 [P] [US1] Implement `BackupSettingsScreen` in `feature/backup/src/main/java/com/ivy/backup/BackupSettingsScreen.kt` with folder selection via `ACTION_OPEN_DOCUMENT_TREE` and an on-demand "back up now" action (MVP-022)
- [ ] T042 [P] [US1] Implement `SnapshotListScreen` in `feature/backup/src/main/java/com/ivy/backup/SnapshotListScreen.kt` showing each snapshot's date, time, size and origin, newest first, with safety snapshots clearly labelled as the state preceding a named restore
- [ ] T043 [US1] Implement `RestoreConfirmScreen` in `feature/backup/src/main/java/com/ivy/backup/RestoreConfirmScreen.kt` showing the comparison and stating in plain language what merging will do, with explicit confirmation and a cancel path that changes nothing
- [ ] T044 [US1] Register `BackupScreen` in `shared/ui/navigation/src/main/java/com/ivy/navigation/Screens.kt` and wire the route in `NavigationRoot.kt`, adding an entry point from the existing settings screen
- [ ] T045 [P] [US1] Add Paparazzi screenshot tests in `feature/backup/src/test/java/com/ivy/backup/BackupScreensScreenshotTest.kt` covering not-configured, populated list, comparison, and each failure state

**Checkpoint**: US1 is independently shippable. The quickstart's Scenarios 1–4, 7 and 8 should pass.

---

## Phase 4: User Story 2 — Know the backup is actually working (Priority: P2)

**Goal**: the settings screen tells the truth about whether backups are still happening, and says what broke
in words the user can act on.

**Independent test**: configure a folder, let backups run, revoke access to the folder, and confirm the
settings screen reports the failure in plain language and states how old the newest snapshot is.

- [ ] T046 [P] [US2] Write failing tests for `BackupStatus` derivation in `shared/domain/src/test/java/com/ivy/domain/usecase/backup/BackupStatusUseCaseTest.kt`: age is derived from the newest snapshot's **manifest** `capturedAt`, never from a storage-reported modification time, and a wallet with no snapshots is a distinct state from one whose backups are failing
- [ ] T047 [US2] Add last-attempt state (`attemptedAt`, outcome) to `BackupDestinationConfig` in `shared/data/core/src/main/java/com/ivy/data/backup/local/BackupDestinationConfig.kt` — this is a record of an event, not a cached reachability flag
- [ ] T048 [US2] Implement `BackupStatusUseCase` in `shared/domain/src/main/java/com/ivy/domain/usecase/backup/BackupStatusUseCase.kt` returning last successful snapshot time, its age, the snapshot count, and the last attempt's outcome
- [ ] T049 [US2] Record every attempt's outcome from `BackupWorker` in `feature/backup/src/main/java/com/ivy/backup/BackupWorker.kt`, including failures, so a silent failure cannot look like success
- [ ] T050 [US2] Map each `BackupError` to specific user-facing text in `feature/backup/src/main/java/com/ivy/backup/BackupErrorMessages.kt`, distinguishing a missing or unreachable folder, a full disk, and a temporary problem — a generic "backup failed" fails MVP-032
- [ ] T051 [US2] Surface status and errors in `feature/backup/src/main/java/com/ivy/backup/BackupSettingsScreen.kt`, leading with the **age** of the newest snapshot rather than a claim about the schedule, and offering a re-pick action when the folder is unreachable (MVP-031, research R7)
- [ ] T052 [P] [US2] Add a test in `feature/backup/src/test/java/com/ivy/backup/BackupViewModelTest.kt` asserting that a storage failure never removes an existing snapshot and never reports success

**Checkpoint**: both stories are complete. Quickstart Scenarios 5 and 6 should pass.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T053 Run the cold-recovery test from [quickstart.md](./quickstart.md) Scenario 3 by hand: capture, **uninstall**, reinstall, re-pick the folder, restore. A snapshot readable only by the installation that wrote it has failed the feature regardless of unit-test results
- [ ] T054 [P] Automate the round-trip of [quickstart.md](./quickstart.md) Scenario 7 in `shared/data/core/src/test/java/com/ivy/data/backup/SnapshotRoundTripTest.kt`: capture a snapshot, feed it to the existing manual import, confirm it is accepted (MVP-002)
- [ ] T055 [P] Run [quickstart.md](./quickstart.md) Scenario 8 against a seeded wallet of ~10,000 transactions and confirm capture, list and restore stay responsive and the archive stays in the hundreds of KB
- [ ] T056 Verify the negative requirements hold against `feature/backup/src/main/java/com/ivy/backup/` and `shared/data/core/src/main/java/com/ivy/data/backup/local/`: no object other than a snapshot is written to the folder (MVP-037), no replace-mode restore is reachable in the UI (MVP-036), and nothing synchronises between devices (MVP-035)
- [ ] T057 Green gates (Principle V): run `./gradlew detekt`, `./gradlew testDebugUnitTest`, `./gradlew assembleDemo` and `./gradlew verifyPaparazziDebug`, and report each from observed output — a `NO-SOURCE` or `UP-TO-DATE` task is not verification
- [ ] T058 Record any defect found during Phase 5 in `.wolf/buglog.json` with its root cause and fix before the work is considered done

---

## Dependencies

```
Phase 1 (T001–T004)
   └─> Phase 2 (T005–T021)
          ├─> Phase 3 / US1 (T022–T045)   ← independently shippable MVP
          └─> Phase 4 / US2 (T046–T052)   ← depends on US1's capture + worker
                 └─> Phase 5 (T053–T058)
```

Within Phase 2: T005–T007 are parallel; T008 needs T006 and T007; T010 needs T009; T012 needs T011;
T016 gates T017, T019 and T020; T019 needs T018.

Within Phase 3: all seven test tasks (T022–T027) are parallel and come first. T029 needs T010, T012, T016
and T028. T030 is called by T029. T033 needs T015 and T016. UI tasks T039, T041, T042 and T045 are parallel;
T040 needs T039; T043 needs T032 and T040; T044 needs T041–T043.

US2 is **not** independent of US1 — it reports on the capture path and the worker US1 builds. US1 ships
without US2; US2 does not ship without US1.

## Parallel execution examples

**Phase 2, after T004**: T005, T006, T007, T009, T011, T018 can all proceed at once — six different files
with no shared dependency.

**Phase 3, first wave**: T022 through T027 are six independent test files. Writing all six before any
implementation is what makes Principle IV real rather than ceremonial.

**Phase 3, UI wave**: T039, T041, T042 and T045 touch four different files.

## Implementation strategy

**MVP = Phases 1–3.** US1 alone is a shippable, useful feature: automatic local snapshots and a restore
that recovers a bad day. Stop there and the slice has delivered its purpose.

**Then US2**, which is small but changes the feature's honesty. Scheduled work on Android is interrupted by
the OS and by manufacturers in ways no app can prevent; without US2 the feature can fail silently for
weeks, which is worse than having no backup because it replaces a known risk with a false one.

**Two tasks are riskier than they look** and should not be left to the end of a phase: T013, which edits
shipped import code that users rely on today, and T033, which is the one irreversible operation in the
slice.
