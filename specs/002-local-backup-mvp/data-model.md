# Phase 1 Data Model: Local Backup MVP

**Date**: 2026-10-03 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

These are domain types, not Room entities. **Nothing in this feature is stored in the app database.**
Everything persisted lives in the backup folder as immutable objects (MVP-008); the only app-side state
is the destination configuration. This is deliberate: a backup that depends on app state to be readable is
useless in the scenario where the app state is gone.

Constitution Principle III applies throughout — a state the model cannot represent is a state no caller has
to defend against.

---

## SnapshotRef

One immutable backup object in the destination. Identity is the storage name, which is unique by
construction (research R4).

| Field | Type | Notes |
|---|---|---|
| `name` | `String` | Portable name, the identity. Sorts chronologically (MVP-009). |
| `uri` | `Uri` | Storage handle. Not persisted; re-derived on every listing. |
| `capturedAt` | `Instant` | Read from the manifest, **not** from the storage's reported modification time. |
| `origin` | `SnapshotOrigin` | Why it exists. |
| `summary` | `SnapshotSummary` | Embedded at capture (MVP-006). |
| `sizeBytes` | `Long` | For the storage-use display. |

**Rules**
- A `SnapshotRef` only exists for an object whose manifest parsed successfully. An unreadable or foreign
  file never becomes a `SnapshotRef` — it is counted and reported separately (MVP-007), never listed as a
  snapshot the user could restore.
- `uri` is transient. A persisted `Uri` can outlive the grant that makes it usable, so it is never stored.

## SnapshotOrigin

```
sealed interface SnapshotOrigin { Scheduled | Manual | Safety }
```

`Safety` is protected from retention (MVP-013) and is what makes a restore recoverable (MVP-016). Encoding
this as a type rather than a boolean `isProtected` is what stops a future rule from protecting the wrong
thing.

## SnapshotSummary

The small, cheap description shown in the list and used for comparison. Written into the manifest at
capture time so listing never opens the data document.

| Field | Type |
|---|---|
| `transactionCount` | `Int` |
| `accountCount` | `Int` |
| `categoryCount` | `Int` |
| `budgetCount` | `Int` |
| `newestTransactionAt` | `Instant?` — null for an empty wallet |

**Rules**
- Counts are non-negative. An empty wallet is representable and is a legitimate thing to back up (MVP-012's
  floor must never be defended by refusing to capture).
- `newestTransactionAt` is nullable rather than epoch-zero: "no transactions" and "a transaction dated 1970"
  are different facts and the comparison screen must not conflate them.

## SnapshotComparison

Produced before a restore is confirmed (MVP-017). Derived, never stored.

| Field | Type |
|---|---|
| `current` | `SnapshotSummary` — the live wallet |
| `candidate` | `SnapshotSummary` — the snapshot being restored |
| `deltas` | per-count signed difference |

**Rule**: a comparison is computed from two summaries only. It never requires parsing the snapshot's data
document, which is what keeps it inside SC-010.

## BackupDestination

The only app-side persisted state (DataStore, not Room).

| Field | Type |
|---|---|
| `treeUri` | `String?` — null means not configured |
| `configuredAt` | `Instant?` |

**State transitions**

```
NotConfigured --user picks folder--> Configured
Configured --grant revoked / folder deleted--> Configured-but-unreachable
Configured-but-unreachable --user re-picks--> Configured
Configured --user clears--> NotConfigured
```

Unreachability is **not** a stored state. It is discovered on use and surfaced as `BackupError.AccessDenied`
(MVP-025). Storing it would make it possible for the stored flag and reality to disagree, and the stale one
would be the one shown to the user.

## BackupError

```
sealed interface BackupError {
    NotConfigured
    AccessDenied          // grant revoked, or folder deleted
    OutOfSpace
    WriteFailed(cause)
    SnapshotUnreadable(name, reason)
    RestoreFailed(cause)  // wallet unchanged — the transaction rolled back
}
```

Per Principle II, every operation in this feature returns `Either<BackupError, T>`. `RestoreFailed` carries
the guarantee in its name: it is only produced on a path where the transaction rolled back, so a caller
never has to ask whether the wallet was partially written.

## RetentionDecision

Derived from a listing; the input to pruning.

| Field | Type |
|---|---|
| `keep` | `List<SnapshotRef>` |
| `delete` | `List<SnapshotRef>` |

**Invariants**, checked in the use case and asserted in tests:
- `keep + delete` is exactly the input set; nothing is invented or dropped.
- `keep` is never empty when the input is non-empty (MVP-012).
- No `Safety`-origin snapshot appears in `delete` (MVP-013).
- `delete` is only acted on after the new snapshot is confirmed written (MVP-011) — enforced by call order
  in the capture use case, not by this type.

---

## Relationship to the full feature

The deferred event record (`FR-012`–`FR-020` in `001`) adds a second kind of object to the same folder. It
does not change any type above: `SnapshotRef` keeps its identity and manifest, and a later history view is a
union of events found with snapshots found. This is what makes the deferral additive rather than a migration
(see spec "Relationship to the Full Feature").
