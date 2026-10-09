# Contract: BackupStorage

**Date**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

The destination abstraction. One implementation in this slice (`SafFolderStorage`, a user-chosen folder);
the interface exists so a cloud destination can be added later without touching the domain layer
(MVP-024, narrowing FR-055).

Lives in `shared/data/core`. Returns `Either<BackupError, T>` throughout (Principle II). All functions are
`suspend` and main-safe (Principle I).

---

## Operations

### `suspend fun list(): Either<BackupError, Listing>`

Returns every snapshot object the destination holds, newest first.

```
data class Listing(
    val snapshots: List<SnapshotRef>,   // newest first
    val unreadable: Int,                // present but not parseable as a snapshot
)
```

- Ordering is by name, descending — names sort chronologically by construction, so no storage timestamp is
  consulted (MVP-009).
- A file whose manifest is missing or unparseable is counted in `unreadable` and **never** appears in
  `snapshots` (MVP-005, MVP-007). The whole listing does not fail because one object is corrupt.
- An empty folder is `Listing(emptyList(), 0)` — success, not an error.
- `NotConfigured` when no destination is set; `AccessDenied` when the grant is gone or the folder has been
  deleted.

### `suspend fun write(name: String, content: Source): Either<BackupError, SnapshotRef>`

Writes one new object and returns a ref to it.

- **Returns only after the bytes are durable.** A caller that has a `SnapshotRef` may act as though the
  snapshot exists — MVP-011 depends on this, since retention prunes on the strength of this return value.
- Must never overwrite an existing object (MVP-008). The implementation must not rely on the storage
  rejecting a duplicate name: `DocumentFile.createFile()` silently de-duplicates to `name (1)` instead of
  failing (research R1), so uniqueness is the caller's guarantee via naming (R4), and this function
  **verifies the created object's actual name matches `name`** and fails if it does not.
- A failure leaves no partial object visible. A partial write is deleted before returning
  `WriteFailed`/`OutOfSpace`.

### `suspend fun read(ref: SnapshotRef): Either<BackupError, Source>`

Opens a snapshot's bytes for restore. `SnapshotUnreadable` if the object has disappeared or is truncated
since it was listed — a real race, because the folder is user-visible and the user may delete from a file
manager while the restore screen is open.

### `suspend fun delete(ref: SnapshotRef): Either<BackupError, Unit>`

Removes one object. **Deleting an object that is already gone is a success**, not an error: the
post-condition "this snapshot is not in the destination" holds either way, and treating it as a failure
would make concurrent cleanup report spurious errors.

---

## Invariants the implementation must hold

0. **Exactly four operations.** `write`, `list`, `read`, `delete` and nothing else (MVP-023). Total
   storage use, shown to the user, is derived in the domain layer by summing `Listing.snapshots`' sizes —
   it is not a fifth storage operation, because a destination that cannot report aggregate usage must still
   be implementable against this interface.

1. **Append-only.** No operation modifies an existing object's bytes. Objects are created and deleted, never
   edited (MVP-008).
2. **No create-as-lock.** Creating an object is never used to claim exclusivity, because the underlying
   storage de-duplicates rather than refusing (R1). There is no mutual exclusion primitive here, and the
   design must not need one.
3. **Every failure is typed.** No operation throws for a condition the caller could encounter in normal use
   — a revoked grant, a full disk and a deleted folder are all values, not exceptions.
4. **Main-safe.** Every function moves to the IO dispatcher itself; no caller is required to wrap it.

## Testing

`BackupStorage` is an interface specifically so the domain use cases are tested against an in-memory fake
rather than SAF. The fake must reproduce the awkward behaviours, not an idealised storage — in particular
de-duplicating create, delete-of-missing succeeding, and a readable object disappearing between `list` and
`read`. A fake that is nicer than reality tests nothing worth testing.

`SafFolderStorage` itself is covered by instrumentation tests (`src/androidTest/`), since SAF has no JVM
equivalent.
