# Feature Specification: Local Backup MVP

**Feature Branch**: `feat/local-backup-mvp`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "A minimal, independently shippable slice of the Wallet Backup and Recovery
feature (specs/001-wallet-backup-and-recovery). One destination — a local folder the user picks. Automatic
recurring snapshots plus an on-demand button. Snapshots are immutable and pruned by retention. Restore is
merge-only, preceded by an automatic safety snapshot and a summary comparison. History is derived from the
snapshots present in the folder rather than from a separate event record. Cloud destinations, replace-mode
restore, one-tap undo and the event history record are deliberately deferred to the full feature."

## Relationship to the Full Feature

This specification is a delivery slice of `specs/001-wallet-backup-and-recovery/spec.md`, not a replacement
for it and not a change to it. The full specification remains the design of record and the roadmap.

This slice ships the smallest thing that independently protects a user against losing their wallet data. It
is scoped so that everything deferred can be added later **additively** — by storing new kinds of objects
alongside the existing ones — without migrating, rewriting or invalidating anything this slice stores.

Requirements in this document are numbered independently (MVP-001…) and each cites the requirement it
narrows or inherits from the full specification. Where this slice is narrower, it is narrower by scope
only: it never contradicts the full specification, and nothing here may be later reversed.

### What this slice ships

| Capability | Full feature | This slice |
|---|---|---|
| Destinations | Local folder, private cloud area, visible cloud folder | Local folder only |
| Capture | Scheduled and on demand | Unchanged |
| Immutability | Never modify a stored object | Unchanged |
| Retention | Decreasing-density window | Simple "keep the N most recent" |
| Restore modes | Replace or merge, chosen per restore | Merge only |
| Safety snapshot | Captured before every restore | Unchanged |
| Undo | One-tap, recorded, reversible | Manual restore of the safety snapshot |
| History | Event record stored alongside snapshots | Derived from the snapshots present |
| Comparison | Snapshot vs wallet, and snapshot vs snapshot | Snapshot vs wallet only |

### What this slice defers, and why it stays cheap to add

- **Cloud destinations** (FR-017, FR-020). Deferred because the work is predominantly external — account
  registration, consent configuration and per-build credential setup — none of which makes the feature
  better for a user with one device. MVP-023 keeps the storage contract in place so a cloud destination is
  added as an implementation of it.
- **Replace-mode restore** (FR-021 replace half, FR-025). Deferred because it is the only operation in the
  feature that destroys data, and because merge already satisfies User Story 1 — the snapshot is
  authoritative for every record it contains, so a merge repairs records a bad import mangled.
- **One-tap undo** (FR-048, FR-049, FR-050). Undoing a merge requires replace semantics, so it cannot ship
  before replace does. The safety snapshot that makes undo possible is captured regardless (MVP-016), so
  the recovery guarantee holds in this slice through an ordinary restore.
- **The event history record** (FR-027 to FR-041). Its purpose is reconciling events across several devices
  and destinations; with one device and one folder there is nothing to reconcile, and the set of snapshots
  present is itself a faithful record of every snapshot taken. Adding the record later is purely additive:
  events are independently stored objects (FR-028), so a history view unions the events it finds with the
  snapshots it finds, and snapshots written by this slice simply have no corresponding event.
- **Snapshot-to-snapshot comparison** (FR-045). Comparing a snapshot against the current wallet is what a
  user restoring actually needs; comparing two snapshots is a review convenience.

### Decisions that cannot be deferred

Three constraints cost almost nothing now and cannot be retrofitted onto objects already written. They are
in scope for this slice even though nothing in it exercises their generality.

1. **The storage contract** (MVP-023, from FR-060/FR-061). One implementation ships, but the seam exists.
   Retrofitting an abstraction after the user interface has grown directly into one storage mechanism is
   the expensive version of this work.
2. **Summaries recorded at capture** (MVP-006, from FR-031). A snapshot written without an embedded summary
   can never acquire one, and listing a folder of such snapshots would require opening every one.
3. **Portable object names** (MVP-009, from FR-010). A name that a destination cannot store unchanged
   fails only on removable media, so ordinary testing will not reveal it, and correcting it later would
   mean renaming stored objects — which immutability forbids.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recover from a self-inflicted data accident (Priority: P1)

A user imports a bank statement that turns out to be malformed, or bulk-deletes the wrong category, and
notices an hour — or a week — later. They open backup settings, see a list of dated snapshots taken
automatically since they picked a folder, pick the one from before the accident, see how it differs from
what they have now, and merge it back in. The records the import mangled are returned to their earlier
contents; anything they have legitimately added since is still there.

**Why this priority**: this is the most probable way a user loses data, by a wide margin, and it needs no
account, no network and no third party. It is the whole reason this slice exists.

**Independent Test**: pick a folder, let a scheduled backup run, corrupt or delete data in the app, then
restore the most recent snapshot and confirm the affected records match their pre-accident contents.

**Acceptance Scenarios**:

1. **Given** no folder is configured, **When** the user picks one in backup settings, **Then** the folder is
   remembered across app restarts and device reboots, and a first snapshot is written immediately.
2. **Given** a folder is configured, **When** a scheduled backup runs, **Then** a new snapshot appears whose
   name carries its creation date and time, and no previously written snapshot is modified or removed.
3. **Given** several snapshots exist, **When** the user opens the restore screen, **Then** each is listed
   with its date, time and size, newest first.
4. **Given** the user selects a snapshot, **When** they reach the confirmation step, **Then** they are shown
   how the snapshot differs from their current wallet and what merging will do, and no data has changed yet.
5. **Given** the user confirms, **When** the restore completes, **Then** every record the snapshot contains
   is present with the snapshot's contents, and every record created after the snapshot was taken is still
   present and unchanged.
6. **Given** the user selects a snapshot, **When** they cancel at the confirmation prompt, **Then** no data
   changes.
7. **Given** a restore has completed, **When** the user decides it was a mistake, **Then** the safety
   snapshot captured immediately before it is present in the restore list, clearly identified as such, and
   restorable like any other snapshot.
8. **Given** more snapshots exist than the retention limit, **When** retention runs after a newer snapshot
   has been successfully written, **Then** the oldest snapshots beyond the limit are removed and at least
   one snapshot always remains.

### User Story 2 - Know the backup is actually working (Priority: P2)

A user who set this up weeks ago wants to confirm it is still running, without opening a file manager. They
open backup settings and see when the last snapshot was taken, how many exist, and — if something is wrong —
what broke and what to do about it.

**Why this priority**: a backup feature that fails silently is worse than none, because it replaces a known
risk with a false sense of safety. Scheduled background work on Android is routinely interrupted by the
operating system and by device manufacturers, so visible status is not a nicety here.

**Independent Test**: configure a folder, let backups run, then revoke access to the folder and confirm the
settings screen reports the failure in plain language and states how old the newest snapshot is.

**Acceptance Scenarios**:

1. **Given** backups are running normally, **When** the user opens backup settings, **Then** they see the
   time of the most recent successful snapshot, its age, and how many snapshots are stored.
2. **Given** the chosen folder has been deleted or its access revoked, **When** a backup next runs, **Then**
   the failure is reported in plain language naming the folder as the cause and offering to pick a new one.
3. **Given** the device storage is full, **When** a backup runs, **Then** the failure is distinguished from a
   missing folder and from a temporary problem, and no previously stored snapshot is removed.
4. **Given** no folder has ever been configured, **When** the user has used the app for some time, **Then**
   the absence of any backup is discoverable in settings without the app repeatedly interrupting them.

### Edge Cases

**Folder and storage**

- The chosen folder is deleted, renamed, or its access is withdrawn by the operating system.
- The chosen folder is on removable media that is not currently mounted.
- Storage is exhausted part-way through writing a snapshot.
- The user picks a folder that already contains snapshots from a previous installation.
- The user picks a folder inside a location the operating system later restricts.

**Snapshot integrity**

- A snapshot is truncated by an interrupted write and fails verification.
- A snapshot was written by a newer version of the app than the one now running.
- A file in the folder is not a snapshot at all, or has a snapshot's name but unreadable contents.
- Every snapshot in the folder fails verification.

**Restore**

- The app is killed part-way through a restore.
- The safety snapshot cannot be captured because storage is full.
- A restore is started while another is still running.
- The wallet is empty when a restore begins.
- The snapshot contains records referring to accounts or categories that no longer exist.

**Scheduling**

- The device was off or offline across several scheduled runs.
- The operating system or device manufacturer suppresses background work entirely.
- The device clock moves backwards, making a new snapshot appear older than an existing one.
- Two snapshots are captured within the same second.

## Requirements *(mandatory)*

### Functional Requirements

**Snapshot content and integrity**

- **MVP-001**: The system MUST capture, in a single snapshot, all wallet data the existing manual export
  captures: accounts, transactions, categories, budgets, loans, loan records, planned payments, tags, tag
  associations and application settings. *(FR-001)*
- **MVP-002**: A snapshot MUST be restorable by the existing manual import path, so a file copied out of the
  folder by hand can be restored without this feature being available. *(FR-003)*
- **MVP-003**: A snapshot MUST record the application version and data schema version at the time of
  capture. *(FR-004)*
- **MVP-004**: The system MUST refuse to restore a snapshot whose schema version is newer than the running
  application supports, and MUST explain why. *(FR-005)*
- **MVP-005**: The system MUST record a fingerprint of each snapshot's contents at the moment of capture,
  MUST verify a snapshot against that fingerprint before offering it for restore, and MUST exclude any
  snapshot that fails verification or cannot be read from the restore list. *(FR-006)*
- **MVP-006**: Each snapshot MUST carry, recorded at the moment of capture, a summary of its own contents:
  record counts by type, the date range of the transactions it covers, balance totals, and its fingerprint.
  Listing snapshots and comparing one against the current wallet MUST use these summaries and MUST NOT
  require reading snapshot contents. *(FR-031, FR-046)*
- **MVP-007**: The system MUST distinguish, in what it tells the user, between a snapshot that is no longer
  present in the folder and one that is present but fails verification. *(FR-007)*

**Immutability and retention**

- **MVP-008**: The system MUST NOT modify or overwrite any object it has stored in the folder. Recording new
  information MUST be achieved by storing a new object, never by changing an existing one. *(FR-008)*
- **MVP-009**: Stored object names MUST encode creation date and time such that objects sort chronologically
  by name alone without relying on timestamps reported by the storage, and MUST be constructed so that every
  destination in scope of the full feature, including removable media, can store them unchanged and without
  substitution. *(FR-009, FR-010)*
- **MVP-010**: The system MUST retain the most recent N snapshots and remove older ones, where N is a fixed
  published number. *(narrows FR-011)*
- **MVP-011**: The system MUST NOT delete any snapshot until a newer snapshot has been confirmed
  successfully written to the folder. *(FR-012)*
- **MVP-012**: The system MUST always retain at least one snapshot regardless of age or count. *(FR-013)*
- **MVP-013**: The system MUST NOT remove a safety snapshot by retention for 30 days after the restore it
  protects, and MUST NOT remove the safety snapshot of the most recent restore regardless of its age.
  *(FR-015)*
- **MVP-014**: The system MUST NOT write to the folder except to add a snapshot or to remove a snapshot that
  retention has expired. *(FR-067)*

**Restore**

- **MVP-015**: The system MUST restore a snapshot by merging it into the current wallet, with the snapshot
  authoritative for every record it contains: a record present in both the snapshot and the current wallet
  MUST be replaced by the snapshot's version, and a record present only in the current wallet MUST be left
  untouched. *(FR-021, merge half)*
- **MVP-016**: The system MUST capture a snapshot of the wallet's current contents immediately before a
  restore modifies any data, MUST abort the restore if that snapshot cannot be captured, and MUST identify
  that snapshot in the restore list as the state preceding a named restore. *(FR-047; replaces FR-048 undo
  for this slice)*
- **MVP-017**: The system MUST show the user, before any data changes, how the selected snapshot differs
  from the current wallet — differences in record counts by type, the date range covered, and balance
  totals — and MUST state in plain language what merging will do to those differences. *(FR-042, FR-043,
  FR-044, FR-022)*
- **MVP-018**: The system MUST require explicit confirmation before a restore modifies any data. *(FR-023)*
- **MVP-019**: A restore MUST complete fully or leave the wallet entirely unchanged; no partial restore may
  survive an interruption or failure. *(FR-025)*
- **MVP-020**: The system MUST prevent a restore from starting while another is in progress. *(FR-051)*
- **MVP-021**: The system MUST list the snapshots present in the folder, newest first, each showing its
  creation date and time, size, and whether it was captured on a schedule, on demand, or before a restore.
  *(FR-024)*
- **MVP-022**: Users MUST be able to trigger a backup on demand at any time, independently of the schedule.
  *(FR-026)*

**Storage contract**

- **MVP-023**: The folder MUST be reached through a contract expressed in exactly four operations — store a
  named object, list the objects present, read a named object, remove a named object — and no behaviour in
  this slice may depend on any capability beyond those four. In particular the system MUST NOT require the
  ability to rename an object, to append to an existing object, to write conditionally on an object's
  current state, to create an object only if absent, to acquire a lock, or to trust a creation or
  modification time reported by the storage. *(FR-060, FR-061)*
- **MVP-024**: All behaviour that is not storage itself — capture, scheduling, retention, listing,
  comparison, restore and error reporting — MUST be defined independently of the folder, such that adding a
  further destination later requires implementing only the four operations of MVP-023 and that
  destination's own configuration and connection handling. *(FR-059, FR-062)*
- **MVP-025**: Failures MUST be reported in terms of a storage-independent vocabulary — unreachable, not
  configured, access denied, out of space, object missing, object corrupt, transient. *(FR-064)*
- **MVP-026**: The folder MUST be identified such that more than one destination can be supported later
  without redesigning the model, even though only one is offered here. *(FR-066)*

**Configuration, scheduling and reporting**

- **MVP-027**: Users MUST be able to choose a local folder, and that choice MUST survive app restarts and
  device reboots without re-prompting. *(FR-016)*
- **MVP-028**: The system MUST back up automatically on a recurring schedule without user interaction, under
  conditions that avoid consuming battery unexpectedly. *(FR-052)*
- **MVP-029**: The system MUST defer, rather than fail, a scheduled backup when the folder is temporarily
  unavailable, and MUST retry without user intervention. *(FR-053)*
- **MVP-030**: The system MUST NOT create multiple snapshots for scheduled runs missed while the device was
  off or idle; a single snapshot on wake is sufficient. *(FR-054)*
- **MVP-031**: The system MUST display the outcome and time of the most recent backup attempt, the age of
  the newest snapshot, and the number of snapshots currently stored. *(FR-055)*
- **MVP-032**: Failures MUST be reported to the user in plain language that distinguishes a missing or
  revoked folder, exhausted storage, and a transient problem. *(FR-056)*
- **MVP-033**: The system MUST make the absence of a configured folder discoverable without repeated
  interruption. *(FR-057)*
- **MVP-034**: Removing the configured folder MUST stop further backups and MUST NOT delete anything already
  stored there, and the app MUST say so before the user confirms. *(FR-058)*

**Scope exclusions**

- **MVP-035**: This slice MUST NOT synchronise wallet data between devices and MUST NOT merge concurrent
  changes made on different installations. *(FR-067)*
- **MVP-036**: This slice MUST NOT offer replace-mode restore. Where the full feature presents a choice of
  mode, this slice MUST describe the single behaviour it performs rather than presenting an unselected
  choice. *(defers FR-021 replace half)*
- **MVP-037**: This slice MUST NOT store any object in the folder other than snapshots. Any history the user
  is shown MUST be derived from the snapshots present and their recorded summaries. *(defers FR-027 to
  FR-041)*

### Key Entities

- **Snapshot**: an immutable point-in-time capture of the complete wallet dataset. Attributes: creation
  instant, application and schema version, size, contents fingerprint, its embedded summary, and whether it
  was captured on a schedule, on demand, or before a restore. Never modified after creation.
- **Safety Snapshot**: a snapshot captured automatically immediately before a restore, so that the restore
  can be reversed by restoring it. Identified as such in the restore list, bound to the restore it preceded,
  and protected from retention while that reversal remains available.
- **Snapshot Summary**: the record counts by type, transaction date range, balance totals and fingerprint
  recorded inside a snapshot at capture, so listing and comparison never read snapshot contents.
- **Destination**: the place snapshots are stored — in this slice, the single user-chosen local folder.
  Attributes: its configuration, whether it is currently reachable, and its health.
- **Destination Status**: the outcome of the most recent backup attempt — when it ran, whether it succeeded,
  the reason if not, and how many snapshots are stored.
- **Retention Policy**: the rule deciding which snapshots survive, expressed as a count of most-recent
  snapshots, constrained never to leave the folder empty and never to remove a protected safety snapshot.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from no protection to a working, verified first snapshot in under 2 minutes and
  no more than 4 taps.
- **SC-002**: After any data accident, a user can recover to a state no more than 24 hours old, in at least
  99% of cases where backup has been configured for a week or more and the operating system has not
  suppressed background work.
- **SC-003**: 100% of snapshots offered in the restore list restore successfully; no snapshot is ever
  offered that cannot be read.
- **SC-004**: No user action or system failure results in the loss of the last remaining snapshot.
- **SC-005**: A user can state, before confirming a restore, exactly what will happen to the data currently
  in their wallet.
- **SC-006**: 100% of restores performed while their safety snapshot survives can be reversed, returning the
  wallet to a state identical to the one immediately before the restore.
- **SC-007**: When a backup fails, a user can tell from the backup settings screen alone what went wrong and
  what to do about it, without consulting logs or support.
- **SC-008**: A user can tell, from the backup settings screen alone, how old their newest snapshot is,
  within one minute of opening the app.
- **SC-009**: Automatic backup consumes no more than 1% of daily battery and produces no user-visible
  interruption or slowdown.
- **SC-010**: A wallet of at least 10,000 transactions is captured, compared and restored without failure or
  perceptible delay in normal app use.
- **SC-011**: The restore list and any comparison open in under 2 seconds for a folder holding the full
  retention limit of snapshots, without reading snapshot contents.
- **SC-012**: Interrupting any write — a snapshot or a retention removal — at any point never destroys a
  previously stored snapshot and never leaves the folder in a state the app cannot list.
- **SC-013**: Every snapshot this slice writes is listable and restorable by the full feature once the
  deferred capabilities are added, with no migration, rewriting or reformatting of stored objects.

## Assumptions

- **The set of snapshots is the history, in this slice.** With one device and one folder there is nothing to
  reconcile between writers, and every snapshot records its own creation time, origin and summary. Restores
  and retention removals therefore go unrecorded in the folder. This is a real and accepted loss of
  information, bounded by the fact that the safety snapshot preceding each restore remains visible and
  labelled, which is what a user actually needs after a regretted restore.
- **Adding the event record later is additive and needs no migration.** Events in the full feature are
  independently stored objects, so a later history view unions the events it finds with the snapshots it
  finds; snapshots written by this slice simply have no corresponding event. This is the property that makes
  deferring the record safe rather than merely convenient.
- **Merge alone is sufficient for the primary scenario.** Because the snapshot is authoritative for every
  record it contains, a merge repairs records a bad import mangled. What merge cannot do is remove records
  that should not exist at all — a bulk import that created unwanted records leaves them in place. Such a
  user must delete them by hand in this slice. This is the principal functional cost of deferring replace.
- **Reversing a restore is a manual restore in this slice.** The safety snapshot is captured and protected
  exactly as the full feature requires; only the one-tap flow is absent. A user reverses a restore by
  restoring the safety snapshot from the ordinary list.
- **Retention is a fixed count, not a density curve.** A count is trivially explainable and trivially
  testable, and the density curve is a refinement that changes which snapshots survive without changing any
  guarantee. Replacing it later removes no snapshot that the count rule would have kept.
- **No encryption passphrase.** Snapshots are stored unencrypted, as in the full feature. The folder is
  already protected by the device lock, and a forgotten passphrase would permanently destroy the data the
  feature exists to protect. Decided explicitly, not by omission.
- **Background execution is not guaranteed by the platform.** Android device manufacturers suppress
  scheduled work to varying and undocumented degrees, and no application can prevent it. The feature
  therefore does not promise an interval; it reports the age of the newest snapshot so that suppression is
  visible to the user rather than silent. This is why User Story 2 is in the MVP rather than deferred.
- **One device.** Multiple devices writing the same folder is out of scope here; the full specification
  covers it and the deferred event record is what makes it correct.
- **The folder may be shared with other software.** The app reads only objects it recognises as its own
  snapshots and ignores anything else present, and never removes an object it did not write.
