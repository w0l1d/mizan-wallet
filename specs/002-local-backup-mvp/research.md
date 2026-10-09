# Phase 0 Research: Local Backup MVP

**Date**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

Six decisions were required before design. All are resolved; none remains NEEDS CLARIFICATION.

---

## R1 — How the chosen folder is reached

**Decision**: Storage Access Framework. `ACTION_OPEN_DOCUMENT_TREE` for selection,
`contentResolver.takePersistableUriPermission` with read+write to survive restarts and reboots (MVP-027),
and `DocumentFile` for listing, creating, reading and deleting.

**Rationale**: minSdk 28 with compileSdk 34 means scoped storage. An app cannot write to an arbitrary
user-chosen folder by path, and a persisted tree grant is the only mechanism that both lets the user choose
freely and survives a reboot.

**Alternatives considered**:
- *App-specific external directory.* No permission needed, trivially fast — and deleted when the app is
  uninstalled, which destroys the data in precisely the scenario the feature exists for. Disqualifying.
- *`MediaStore`.* Wrong semantics for opaque archives, and offers no user-chosen location.
- *`MANAGE_EXTERNAL_STORAGE`.* A Play Store policy problem for a feature that does not need it.

**Consequences carried into design**:
- `DocumentFile.listFiles()` costs one IPC round trip per child, so listing is O(n) with a high constant.
  With retention capped at 30 snapshots this is comfortably inside SC-011; it is also the concrete reason
  the deferred event record would have needed consolidation.
- `DocumentFile.createFile()` **de-duplicates a colliding name rather than failing** — it silently creates
  `name (1)`. This is why the naming scheme must be collision-free by construction (R4) and why
  create-if-absent must never be used as a lock (MVP-023).
- A persisted grant can still be revoked, and the folder can be deleted. Every operation must map this to
  `BackupError.AccessDenied` or `NotConfigured` rather than throwing (MVP-025, MVP-032).

---

## R2 — Whether restore can reuse the existing import

**Decision**: No. A separate restore path is written. `BackupDataUseCase.importJson` is left untouched and
continues to serve the manual import and CSV flows.

**Rationale**: two defects in the existing implementation are incompatible with the spec, both verified by
reading `shared/data/core/src/main/java/com/ivy/data/backup/BackupDataUseCase.kt`:

1. **Account and category identity is matched by name, not by id.** `getReplacementPairs` groups existing
   and snapshot accounts by `name`, takes pairs, and `accommodateExistingAccountsAndCategories` then
   rewrites the snapshot's UUIDs by string-replacing them across the entire JSON document. The effect is
   that a renamed account in the snapshot becomes a *duplicate* rather than being matched to its record,
   and an account renamed to match another's name is silently merged into it. MVP-015 requires merge by
   record identity with the snapshot authoritative.
2. **The insert is not transactional.** `insertDataToDb` issues `transactionWriter.saveMany(...)` followed
   by several concurrent `async` `saveMany` calls with no enclosing transaction. An interruption part-way
   leaves the wallet in a state that is neither the old one nor the new one. MVP-019 requires all-or-nothing.

A third, smaller issue: `groupBy { it.name }.filter { it.value.size == 2 }` silently does nothing when three
records share a name, so the remap is not even internally consistent.

**Alternatives considered**:
- *Fix `importJson` in place.* Rejected for this slice. It is shared with the user-facing manual import and
  the CSV import flow, so changing its merge semantics changes behaviour outside this feature's scope, in a
  slice chosen for being small. Recorded in the plan's Complexity Tracking as debt to settle when
  replace-mode restore lands and the two paths can be unified deliberately.
- *Wrap `importJson` in a transaction and accept name matching.* Rejected: it would satisfy MVP-019 while
  still violating MVP-015, and the name-matching behaviour is the more damaging of the two.

**Note on the specification**: the 2026-09-29 clarification in `001` records, as supporting reasoning for
snapshot-wins merge, that "it is also the behaviour the existing manual import already implements". For
transactions that holds; for accounts and categories it does not, for the reason above. The clarification's
*decision* is unaffected — snapshot-wins remains correct — but that one supporting sentence is wrong and
should not be relied on in planning.

---

## R3 — Restore atomicity

**Decision**: the entire merge runs inside a single Room transaction, via a `@Transaction`-annotated suspend
function or `IvyRoomDatabase.withTransaction { }`, with the safety snapshot captured and confirmed written
*before* the transaction opens.

**Rationale**: MVP-019 says a restore completes fully or leaves the wallet unchanged. A transaction gives
that for free and for the right reason — the database rolls back. Relying on the safety snapshot to undo a
half-applied restore makes recovery depend on an operation that has just demonstrated it can fail, and it
cannot help at all if the process is killed before the undo runs.

**Alternatives considered**:
- *Safety snapshot as the primary guard.* Rejected per above; it remains the second line and a user-visible
  recovery route (MVP-016).
- *Per-entity transactions.* Rejected: it is exactly the partial state MVP-019 forbids.

**Consequence**: the concurrent `async` writes used by the existing import cannot be carried over — Room
transactions are confined to a single coroutine context. The restore path writes sequentially.

---

## R4 — Snapshot object naming

**Decision**: `wallet-<yyyyMMdd>T<HHmmss>Z--<device>--<origin>.zip`, for example
`wallet-20261003T041200Z--a3f2--sched.zip`, where `origin` is `sched`, `manual` or `safety`.

**Rationale**: satisfies MVP-009 on both counts. Lexicographic order equals chronological order, so listing
never consults a timestamp the storage reports. The character set is `A–Z a–z 0–9 - .` only, which every
filesystem in scope stores unchanged — **colons are illegal on FAT32 and exFAT**, which rules out ISO-8601's
natural `HH:MM:SS` form and is the single most likely way this requirement gets violated by accident.

The device segment is present even though this slice is single-device: it costs nothing now, and it is what
stops two installations choosing the same name later, given that `DocumentFile.createFile()` de-duplicates
rather than refusing a collision (R1).

**Alternatives considered**:
- *ISO-8601 with colons.* Rejected: unstorable on removable media, and would fail only on a user's SD card.
- *Epoch milliseconds.* Sorts correctly and is collision-resistant, but is unreadable to a user browsing the
  folder — and MVP-002 means a user may well be browsing it by hand.
- *Opaque UUID names with metadata inside.* Rejected: it would make listing require opening every file,
  defeating MVP-006 and SC-011.

**Same-second collisions**: two captures within one second produce the same name. The capture path appends a
short disambiguating suffix rather than relying on the storage to reject the name. MVP-020 makes this
near-impossible for restores; the on-demand button makes it merely unlikely.

---

## R5 — Snapshot format and where the summary lives

**Decision**: keep the existing zip-with-one-JSON layout and **add** a second entry, `manifest.json`,
carrying the schema and app version, the capture instant, the origin, the content fingerprint and the
summary required by MVP-006. See [contracts/snapshot-format.md](./contracts/snapshot-format.md).

**Rationale**: MVP-002 requires the existing manual import to still accept the file. The manual import
locates a single `.json` entry inside the archive, so the manifest must be distinguishable from the data
entry — hence a fixed, reserved name the data entry can never take. Adding an entry preserves the existing
import; changing the data entry would break it.

Reading one small manifest per snapshot is what makes listing and comparison cheap: 30 manifests of a few
hundred bytes each, versus 30 archives that may each hold 10,000 transactions (SC-010, SC-011).

**Verified constraint**: the existing extraction asserts it unzipped **exactly one** file and errors
otherwise. Adding `manifest.json` therefore breaks the current `importBackupFile` path unless that assertion
is relaxed to "exactly one `.json` entry that is not the manifest". This is a required, deliberate change to
existing code and must not be discovered during implementation — it is listed as a task precondition.

**Alternatives considered**:
- *A sidecar manifest file beside the archive.* Rejected: two objects per snapshot that can be separated,
  and a sidecar that outlives its archive is a state the model should not be able to represent.
- *Summary inside the existing JSON.* Rejected: reading it would mean parsing the whole data document, which
  is exactly the cost MVP-006 exists to avoid.

---

## R6 — Retention parameters

**Decision**: keep the **30 most recent** snapshots, subject to MVP-011 (never delete before a newer one is
confirmed written), MVP-012 (never leave the folder empty) and MVP-013 (protected safety snapshots).

**Rationale**: at one scheduled snapshot a day, 30 is roughly a month of history, which covers SC-002's
24-hour recovery target with a wide margin. It is a strict superset of what a decreasing-density curve of
comparable span would retain, so adopting the curve later discards nothing the count rule would have kept —
this is what makes MVP-010's relaxation safe rather than merely convenient.

Storage cost is bounded and small: a 10,000-transaction wallet compresses to a few hundred KB, so 30
snapshots is single-digit MB.

**Alternatives considered**:
- *Age-based window.* Rejected: an app unused for two months would prune itself to the MVP-012 floor.
- *Density curve now.* Rejected as unnecessary complexity for one destination; deferred to the full feature.

---

## R7 — Scheduling

**Decision**: `PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)` enqueued with
`enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)`, constrained with
`setRequiresBatteryNotLow(true)`, and returning `Result.retry()` on a transient failure so WorkManager
applies its backoff policy.

**Rationale**: verified against current WorkManager documentation. `KEEP` means re-enqueueing at every app
start does not reset the schedule. Periodic work **does not backfill missed runs** — a device that was off
for three days performs one run on wake, not three, which is exactly MVP-030 and requires no logic of our
own. `Result.retry()` with the default exponential backoff satisfies MVP-029.

No network constraint is set: the destination is local, so requiring connectivity would be wrong. Battery is
constrained rather than charging, so a daily backup is not postponed indefinitely on a device that is rarely
plugged in.

**Alternative considered**: `AlarmManager` with an exact alarm. Rejected — it needs a special permission on
API 31+, is hostile to battery, and would still be killed by the same manufacturer power management.

**Known limitation, accepted and specified**: device manufacturers suppress background work to varying and
undocumented degrees and no application can prevent it. This is why MVP-031 and SC-008 surface the age of
the newest snapshot rather than promising an interval — the feature makes suppression visible instead of
claiming to defeat it.
