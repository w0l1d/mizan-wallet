# Contract: Snapshot Format

**Date**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

This is the only artifact of this feature that outlives the app. It is written once and read by versions of
the code that do not exist yet, so it is fixed here rather than discovered during implementation
(SC-013).

---

## Object name

```
wallet-<yyyyMMdd>T<HHmmss>Z--<device>--<origin>.zip
```

Example: `wallet-20261003T041200Z--a3f2--sched.zip`

- Timestamp is **UTC**, so names sort chronologically regardless of where the device was.
- `origin` is `sched` | `manual` | `safety`.
- `device` is a short stable per-installation identifier — unused in this slice, present so two
  installations writing to the same folder later cannot collide.
- Character set is `A–Z a–z 0–9 - .` only. **No colons**: FAT32 and exFAT do not permit them, and removable
  media reached through SAF is in scope (research R4).
- Two captures in the same second get a short disambiguating suffix appended by the caller. The storage is
  never relied upon to reject a collision.

## Archive contents

A zip with exactly two entries:

```
manifest.json        reserved name; this feature's metadata
<anything>.json      the wallet data document, unchanged from the existing export
```

The data entry is byte-identical to what `BackupDataUseCase.generateJsonBackup()` produces today, including
its **UTF-16** encoding. It is not re-encoded, reformatted or re-ordered — that is what keeps MVP-002 true
(the existing manual import accepts the file).

## manifest.json

UTF-8. A small, flat document — it is read once per snapshot on every listing, so it stays small on purpose.

```json
{
  "formatVersion": 1,
  "capturedAt": "2026-10-03T04:12:00Z",
  "origin": "sched",
  "appVersion": "...",
  "dataSchemaVersion": 0,
  "dataEntry": "ivy-wallet-backup.json",
  "dataSha256": "...",
  "summary": {
    "transactionCount": 0,
    "accountCount": 0,
    "categoryCount": 0,
    "budgetCount": 0,
    "newestTransactionAt": null
  }
}
```

| Field | Purpose |
|---|---|
| `formatVersion` | Integer, starts at 1. A reader that does not recognise the value reports the snapshot as unreadable rather than guessing (MVP-007). |
| `capturedAt` | The authority on when this snapshot was taken. The storage's modification time is never used — it changes when files are copied between devices. |
| `origin` | Drives retention protection (MVP-013) and the list display. |
| `appVersion`, `dataSchemaVersion` | What wrote it. A snapshot from a newer schema than the running app is refused, not partially applied (MVP-004). |
| `dataEntry` | Names the data entry, so a reader never has to guess which entry is which. |
| `dataSha256` | Integrity. A mismatch is `SnapshotUnreadable` — detected **before** the safety snapshot is taken, so a corrupt file never triggers a restore attempt. |
| `summary` | MVP-006. Present so listing and comparison never parse the data document. |

**Unknown fields are ignored on read.** A future version adding a field must not make the snapshot
unreadable by this one; that is what keeps `formatVersion` reserved for changes that genuinely break
compatibility.

---

## Required change to existing code

`BackupDataUseCase`'s zip extraction currently asserts that **exactly one** file was unzipped and errors
otherwise. Adding `manifest.json` breaks the existing manual-import path unless that assertion is relaxed to
"exactly one `.json` entry other than `manifest.json`".

This is a deliberate, required edit to shipped code, not an incidental one. It is a precondition of the
first task that writes a snapshot, and MVP-002 is not satisfiable without it. A round-trip test — capture a
snapshot, feed it to the existing manual import, assert it is accepted — is the proof.
