# Quickstart: Validating the Local Backup MVP

**Date**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

How to prove the feature works. The scenarios below map onto the spec's success criteria; the numbers in
brackets are the criteria each one discharges.

## Prerequisites

- A device or emulator on API 28+ (minSdk).
- A debug build: `./gradlew assembleDebug`
- A wallet with data in it. For the volume scenarios, a seeded wallet of ~10,000 transactions.

## Commands

```bash
./gradlew :shared:domain:testDebugUnitTest --tests="com.ivy.domain.backup.*"
./gradlew :shared:data:core:testDebugUnitTest --tests="com.ivy.data.backup.*"
./gradlew :feature:backup:testDebugUnitTest
./gradlew detekt
./gradlew connectedDebugAndroidTest    # SafFolderStorage; requires a device
```

Per Principle V, none of these counts as verification unless its output was observed. A `NO-SOURCE` or
`UP-TO-DATE` task proves nothing.

---

## Scenario 1 — First configuration [SC-001, SC-009]

1. Open backup settings. Expect: clearly not configured, with one action to fix it.
2. Pick a folder. Expect: a first snapshot is captured immediately, without a second prompt.
3. Reboot the device, reopen the app, capture again. Expect: it still works — the grant survived
   (MVP-027). **This is the step most likely to be skipped and the one most likely to be broken.**

## Scenario 2 — Recovery from a data accident [SC-002, SC-003, SC-010]

1. Note the current transaction count.
2. Delete a handful of transactions.
3. Open the snapshot list. Expect: each entry shows its date and counts without a perceptible wait — the
   summaries are read from manifests, not from the data documents.
4. Choose yesterday's snapshot. Expect: a comparison of current vs snapshot **before** anything is applied.
5. Confirm. Expect: the deleted transactions are back, and transactions created since the snapshot are
   still there (merge, not replace — MVP-015).
6. Open the list again. Expect: a new `safety` snapshot taken just before the restore (MVP-016).

## Scenario 3 — Cold recovery [SC-004]

The scenario the feature exists for, and the only one that tests the real failure.

1. Capture a snapshot.
2. **Uninstall the app**, then reinstall it.
3. Pick the same folder. Expect: the snapshots are listed and restorable.

A snapshot that is only readable by the installation that wrote it has failed this test, whatever the unit
tests say.

## Scenario 4 — Retention [SC-005, SC-006]

1. Capture 35 snapshots (a debug action or a loop over the capture use case).
2. Expect: 30 remain, the newest 30, plus any `safety` snapshots, which are never pruned.
3. Simulate a write failure on the 36th. Expect: **nothing was deleted** — pruning only happens after a
   confirmed write (MVP-011).

## Scenario 5 — Things going wrong [SC-007, SC-012]

Each of these must produce a stated, specific message and leave the wallet unchanged:

| Do this | Expect |
|---|---|
| Revoke the folder permission in system settings, then capture | A clear "folder is no longer reachable", with a re-pick action — not a crash, not silence |
| Delete the folder from a file manager, then open the list | The same, specifically |
| Put an unrelated `.txt` in the folder | It is reported as present-but-not-a-snapshot; the listing still works |
| Truncate a snapshot zip by hand, then try to restore it | Refused on the digest check, **before** a safety snapshot is taken |
| Fill the storage, then capture | "Out of space", no partial object left in the folder |

## Scenario 6 — The schedule is honest [SC-008]

1. Leave the app for more than a day, or force the worker:
   `adb shell cmd jobscheduler run -f com.ivy.wallet.debug <job id>`
2. Expect: the newest snapshot's **age** is displayed, not a promise about the schedule.
3. Turn off battery optimisation exemptions, or test on a device with aggressive power management. Expect:
   the backup may not run — and the age display makes that visible. That is the designed behaviour, not a
   defect (research R7).

## Scenario 7 — The file is still an ordinary export [SC-013, MVP-002]

1. Capture a snapshot.
2. Use the app's existing manual import on that same file.
3. Expect: accepted and imported.

If this fails, the zip-extraction assertion described in
[contracts/snapshot-format.md](./contracts/snapshot-format.md) was not relaxed.

## Scenario 8 — Volume [SC-011]

With ~10,000 transactions: capture, list and restore each complete without the UI becoming unresponsive,
and the snapshot is a few hundred KB rather than megabytes.
