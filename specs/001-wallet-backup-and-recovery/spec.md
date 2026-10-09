# Feature Specification: Wallet Backup and Recovery

**Feature Branch**: `feat/wallet-backup-and-recovery`

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "Automatic, recurring backup of all wallet data to destinations the user already
owns. Three destinations — a local folder the user picks, a private Google Drive area invisible to the user,
and a visible Google Drive folder — with the user free to enable one or several at once. Backups are
immutable point-in-time snapshots, never overwritten, pruned only after a retention period. Restoring lets
the user choose whether to replace or merge, and every backup, restore and undo is recorded in a reviewable
history stored alongside the snapshots, so the user can compare snapshots before restoring and undo a
restore afterwards. Data synchronisation between devices is explicitly excluded."

## Clarifications

### Session 2026-09-28

- **Q: Does restoring a snapshot merge into existing data or replace it entirely?**
  → **A: The user chooses per restore.** Both modes are offered at the point of restore, the choice is
  recorded, and the operation is reversible.
- **Follow-on requirements stated with that answer:** every backup, restore and undo must be recorded in a
  history the user can review; the user must be able to compare snapshots before restoring; a restore must
  be undoable; an undo is itself a recorded event; and the record must live alongside the snapshots at each
  destination so it can be reviewed whenever backing up or restoring.
- **Follow-on requirement on extensibility:** the destination mechanism must be designed so that the local
  folder, both Google variants, and future providers are interchangeable implementations of one contract.

### Session 2026-09-28 — history record structure

Resolved during design discussion, after an earlier draft treated the history record as a single file that
every device rewrote.

- **Q: How is the history record kept consistent when two devices write the same destination?**
  → **A: By removing the shared mutable object entirely.** The record is a set of independently stored,
  never-modified events. Two devices never address the same object, so there is nothing to conflict over.
  This also removes the record's former status as an exception to immutability.
- **Rejected: compare-a-fingerprint-before-writing.** It detects a concurrent write but cannot prevent one,
  because no destination in scope offers an atomic compare-and-swap. The check and the write cannot be made
  a single step, so two devices can both observe an unchanged record and both write.
- **Rejected: a lock object that fails when it already exists.** The primitive does not exist where it is
  needed — filenames are not unique keys at the cloud destinations, and the local storage framework
  de-duplicates a colliding name rather than refusing it. More importantly the failure mode is inverted: a
  process that dies holding a lock stops all future backups silently, which is a worse outcome than the
  occasional lost history entry it would prevent.
- **Accepted for integrity rather than concurrency:** fingerprints recorded at capture time, over both
  snapshot contents and each individual event, so that a truncated or partially written object is detected
  and ignored instead of being presented as valid.
- **Accepted:** causal questions — chiefly "has this restore already been undone?" — are answered by events
  referencing other events by identifier, never by comparing clock times across devices.

### Session 2026-09-29

- **Q: In merge mode, when a record exists in both the snapshot and the current wallet with the same
  identity but different contents, which version survives?**
  → **A: The snapshot wins.** Merge means the snapshot is authoritative for every record it contains;
  records the snapshot does not contain are left untouched. Chosen because User Story 1's headline case is
  repairing records mangled by a bad import, which requires the snapshot to overwrite them — a
  current-wins merge would leave the corruption in place and make the mode useless for the scenario the
  feature exists for. (An earlier version of this entry stated that the existing manual import already
  implements this. That is true only for transactions: the current import matches accounts and categories
  **by name** and rewrites the snapshot's identifiers to match existing records, which is not merge by
  record identity. The decision above is unaffected; the supporting claim was wrong and is corrected here.)

- **Q: What must an older app do with history events written by a newer version of the app?**
  → **A: Keep them, and never strip what it cannot interpret.** Events carry a format version. An older
  app lists events it does not fully understand, showing an unknown kind as a plain entry, and MUST carry
  unrecognised content through verbatim when consolidating. Refusing to read (a newer device would black
  out the history, contradicting FR-040) and silently skipping (events lost, contradicting FR-035) were
  both rejected. The verbatim clause is the operative part: without it the oldest app in use strips the
  newest app's data during routine consolidation.

- **Q: When does a restore stop being undoable, releasing its safety snapshot to ordinary retention?**
  → **A: 30 days after the restore, except that the most recent restore at each destination stays
  undoable indefinitely.** A plain time limit reproduces the failure the feature exists to catch — User
  Story 1's user notices "an hour, or a week, later", with no guarantee of noticing inside any window. The
  floor costs one pinned snapshot per destination and guarantees the last change made to a user's data is
  never permanently irreversible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recover from a self-inflicted data accident (Priority: P1)

A user imports a bank statement that turns out to be malformed, or bulk-deletes the wrong category, and
notices an hour — or a week — later. They open backup settings, see a list of dated snapshots taken
automatically without them ever configuring anything beyond picking a folder, choose the one from before
the accident, decide whether to replace their current data or merge the snapshot into it, and put their
wallet back the way it was.

**Why this priority**: this is the most probable failure, by a wide margin. It needs no account, no network,
no sign-in and no third party, so it is the smallest slice that independently delivers real protection. It
also forces the snapshot format, the retention rules, and the restore flow into existence — every later
story reuses them.

**Independent Test**: enable the local destination, let a scheduled backup run, delete or corrupt data in
the app, then restore the most recent snapshot in replace mode and confirm the wallet matches its
pre-accident state. Delivers complete protection against in-app data loss with no other story implemented.

**Acceptance Scenarios**:

1. **Given** no destination is configured, **When** the user opens backup settings and picks a folder,
   **Then** the folder is remembered across app restarts and a first snapshot is written immediately.
2. **Given** the local destination is enabled, **When** a scheduled backup runs, **Then** a new snapshot
   file appears whose name carries the creation date and time, and no previously written snapshot is
   modified or removed.
3. **Given** several snapshots exist, **When** the user opens the restore screen, **Then** each snapshot is
   listed with its date, time and size, newest first.
4. **Given** the user selects a snapshot, **When** they reach the confirmation step, **Then** they are asked
   to choose between replacing all current data and merging the snapshot into it, with the consequence of
   each stated in plain language, and neither option preselected.
5. **Given** the user chose replace, **When** the restore completes, **Then** the wallet contains exactly
   what the snapshot contained, and any record created after the snapshot was taken is gone.
6. **Given** the user chose merge, **When** the restore completes, **Then** the snapshot's records are
   present, records created after the snapshot was taken are still present, and any record appearing in
   both has the snapshot's contents.
7. **Given** the user selects a snapshot, **When** they cancel at the confirmation prompt, **Then** no data
   changes.
8. **Given** a snapshot older than the retention window exists, **When** retention runs after a newer
   snapshot has been successfully written, **Then** the expired snapshot is removed and the newer one is
   retained.

---

### User Story 2 - Review the history, compare, and undo a restore (Priority: P2)

A user restores a snapshot, then realises it was the wrong one — or simply wants to check what a restore
would do before committing to it. They open a history that shows every backup, restore and undo that has
ever happened, with what each one contained or changed. They can compare a snapshot against their current
wallet before restoring, and they can reverse a restore they regret, with that reversal itself appearing in
the history.

**Why this priority**: ranked directly after P1 because it is what makes P1 safe. Offering a choice between
replace and merge without a recorded, reversible history hands the user an irreversible decision at the
moment they are most likely to be panicking. The history is also the only way a user can answer "what
actually happened to my data?" — a question a finance app must be able to answer.

**Independent Test**: perform a backup, a replace-restore and an undo in sequence, then verify the history
lists all three with accurate summaries, and that the wallet after the undo matches its state before the
restore. Fully testable against the local destination alone.

**Acceptance Scenarios**:

1. **Given** backups and restores have occurred, **When** the user opens the history, **Then** every
   backup, restore, undo and pruning is listed in reverse chronological order with its date, time, outcome,
   the device that performed it, and which destination it concerned.
2. **Given** a snapshot is selected for restore, **When** the user asks to compare it, **Then** the app
   shows how that snapshot differs from the current wallet — record counts by type, the date range covered,
   and balance totals — without the user having to restore anything and without the snapshot being read.
3. **Given** the user is choosing between replace and merge, **When** the comparison is shown, **Then** it
   states what each mode would do to the differences identified.
4. **Given** a restore has completed, **When** the user opens the history, **Then** that restore appears
   with the snapshot used, the mode chosen, and an "undo" action available.
5. **Given** the user chooses to undo a restore, **When** they confirm, **Then** the wallet returns to
   exactly its state immediately before that restore.
6. **Given** an undo has completed, **When** the user opens the history, **Then** the undo is listed as
   its own event, referencing the restore it reversed.
7. **Given** the history record is missing or unreadable at a destination, **When** the user opens the
   history, **Then** the app rebuilds what it can from the snapshots present and says plainly that earlier
   history could not be recovered.
8. **Given** a restore is undone, **When** the user views the history later, **Then** the original restore
   remains visible and is marked as reversed, rather than being deleted from the record.
9. **Given** one recorded event is unreadable, **When** the user opens the history, **Then** every other
   event is still listed and the history remains usable.

---

### User Story 3 - Recover a lost, stolen or reset device (Priority: P3)

A user's phone is destroyed. They install the app on a new phone, sign in to the Google account they
already use, and their entire wallet — accounts, transactions, categories, budgets, loans, planned
payments, tags and settings — comes back without them ever having exported anything by hand.

**Why this priority**: this is the most severe failure, but it is also the least frequent, and it depends on
the snapshot format, restore flow and history that Stories 1 and 2 establish. It is ranked third on
dependency order and frequency, not on importance — it remains the reason the feature exists.

**Independent Test**: enable a Google destination on device A, let a backup run, install the app fresh on
device B, sign in to the same account, and restore — verify the wallet matches device A with no file ever
handled manually.

**Acceptance Scenarios**:

1. **Given** the user chooses a Google destination, **When** they complete the account connection,
   **Then** the destination is shown as connected with the account identified, and a first snapshot is
   written immediately.
2. **Given** a Google destination is connected, **When** the user chooses the visible variant, **Then**
   the snapshots appear in a clearly named folder the user can find and open through Google Drive itself.
3. **Given** a Google destination is connected, **When** the user chooses the private variant, **Then**
   snapshots are stored where no other app and no manual browsing can reach them, and the app warns the
   user that the files cannot be retrieved by hand.
4. **Given** snapshots exist in a Google destination, **When** the app is installed fresh on another device
   and the same account is connected, **Then** those snapshots are listed, the history recorded at that
   destination is readable, and any snapshot can be restored.
5. **Given** the device has no network connection, **When** a scheduled backup is due, **Then** the attempt
   is deferred rather than recorded as a failure, and runs once connectivity returns.
6. **Given** the user disconnects the account, **When** they confirm, **Then** the app stops backing up to
   that destination and states plainly that snapshots already stored there are left untouched.

---

### User Story 4 - Spread snapshots across several destinations (Priority: P4)

A user who does not want a single point of failure enables both the local folder and a Google destination.
Each one is backed up independently; the backup settings screen shows, per destination, when it last
succeeded, how many snapshots it holds, and what went wrong if anything did.

**Why this priority**: genuine redundancy, but only valuable once at least two destinations work on their
own. It is an increment on the earlier stories rather than a prerequisite for any of them.

**Independent Test**: enable two destinations, force one to fail (revoke the folder, or disconnect the
network with only the Google destination reachable), run a backup, and verify the healthy destination still
receives a complete snapshot while the failing one reports a specific, actionable error.

**Acceptance Scenarios**:

1. **Given** two or more destinations are enabled, **When** a backup runs, **Then** every enabled
   destination receives an identical snapshot.
2. **Given** two destinations are enabled and one is unreachable, **When** a backup runs, **Then** the
   reachable destination is written successfully and only the unreachable one is marked as failed.
3. **Given** a destination has failed, **When** the user opens backup settings, **Then** that destination
   shows the time of the failure and a description of the cause in plain language.
4. **Given** snapshots exist in more than one destination, **When** the user opens the restore screen,
   **Then** snapshots from all destinations are listed together, each labelled with where it came from.
5. **Given** the user enables both Google destinations, **When** the second is enabled, **Then** the app
   states that both live in the same account and therefore do not protect against losing access to it.
6. **Given** the user disables a destination, **When** they confirm, **Then** no further snapshots are sent
   there and existing snapshots there are neither deleted nor pruned.

---

### Edge Cases

**Destination failures**

- **The picked folder disappears** — the card is removed, the folder is deleted, or the granted permission
  is revoked by the system. The destination must report this specifically rather than failing silently or
  repeatedly retrying in the background.
- **Cloud storage is full or the account is over quota.** The failure must be reported as a storage problem
  the user can act on, distinct from a transient network error.
- **Account access is revoked** from the provider's side, or the connection expires. The destination must
  prompt for reconnection rather than silently stopping.
- **The device is offline or idle for weeks.** Missed backups must not accumulate into a burst of
  redundant snapshots when the device wakes.
- **The user moves, renames or deletes files in a destination they can browse.** The app must report the
  affected snapshots as no longer present at that destination, which is not the same statement as the data
  having been destroyed, and must not treat an unrecognised name as an error state.

**Snapshot integrity**

- **The app is killed mid-write.** A partially written snapshot must never appear in the restore list as
  though it were complete.
- **Two snapshots in the same minute** — a manual backup fired while a scheduled one is running. Names must
  not collide and one run must not corrupt the other.
- **A snapshot from a newer app version** is restored onto an older installation. The app must refuse
  clearly rather than importing data it cannot interpret.
- **A corrupt, truncated or unreadable snapshot** is selected. The restore must abort with the user's
  current data untouched.
- **Device clock is wrong or changes timezone.** Snapshot ordering must remain stable and correct.
- **A destination is written to by a device whose storage names characters differently** — for example
  removable media that rejects characters a phone's internal storage accepts. Names must be chosen so that
  every destination in scope can store them unchanged.

**Retention**

- **Retention would delete the only remaining snapshot.** At least one snapshot must always survive,
  regardless of age.
- **Retention runs when the newest backup failed.** Nothing may be pruned until a newer snapshot has been
  confirmed written.
- **Retention would delete a snapshot that is still the only way to undo a restore.** Safety snapshots must
  outlive the normal age rules for as long as their undo remains available.

**History and undo**

- **The history record is absent, truncated or unreadable** at a destination. It must be rebuilt from the
  snapshots present, with the gap stated to the user rather than hidden.
- **One recorded event is truncated** because the app was killed while writing it. That event must be
  ignored without the rest of the history becoming unreadable.
- **The history at a destination was last written by a different device.** Events from both devices must
  survive; neither device may erase the other's history.
- **Two devices record events at the same destination simultaneously.** No event may be lost, and the
  record must not become unreadable.
- **Two devices consolidate older history at the same time.** The result must be the same history, with
  duplicated copies collapsing rather than conflicting.
- **Consolidation is interrupted part-way.** The outcome must be duplicated events, never missing ones.
- **An older app consolidates events written by a newer one.** Fields and event kinds the older app does
  not understand must survive the rewrite untouched.
- **A device's clock is set backwards between two of its own events.** That device's own sequence of events
  must remain correctly ordered, and the inconsistency must be detectable.
- **Undo is requested but the safety snapshot is gone** — the destination was disconnected, the folder was
  cleared, or the file was deleted manually. The app must say the restore can no longer be undone, and why,
  rather than offering an action that will fail.
- **Undo of an undo.** Reversing an undo must be possible and must appear in the history as its own event,
  without the record becoming self-contradictory.
- **A restore is requested while another restore is still running.** Only one may proceed.
- **The app is killed mid-restore.** The wallet must be left either fully restored or fully unchanged, and
  the history must reflect which.

**Empty and first-run states**

- **The user has never configured any destination.** The app must make the absence of protection visible
  without nagging on every launch.
- **A destination holds snapshots but no history yet** — for instance a folder written by an older version
  of the app. Snapshots must remain listable and restorable.

## Requirements *(mandatory)*

### Functional Requirements

**Snapshot content and integrity**

- **FR-001**: The system MUST capture, in a single snapshot, all wallet data the existing manual export
  captures: accounts, transactions, categories, budgets, loans, loan records, planned payments, tags, tag
  associations and application settings.
- **FR-002**: Every destination MUST receive a byte-identical snapshot produced by a single capture; the
  system MUST NOT produce a different format per destination.
- **FR-003**: A snapshot MUST be restorable by the existing manual import path, so that a file retrieved by
  hand from any destination can be restored without this feature being available.
- **FR-004**: A snapshot MUST record the application version and data schema version at the time of
  capture.
- **FR-005**: The system MUST refuse to restore a snapshot whose schema version is newer than the running
  application supports, and MUST explain why.
- **FR-006**: The system MUST record a fingerprint of each snapshot's contents at the moment of capture,
  MUST verify a snapshot against that fingerprint before offering it for restore, and MUST exclude any
  snapshot that fails verification or cannot be read from the restore list.
- **FR-007**: The system MUST distinguish, in what it tells the user, between a snapshot that is no longer
  present at a destination and one that is present but fails verification.

**Immutability and retention**

- **FR-008**: The system MUST NOT modify or overwrite any object it has stored at a destination. This
  applies to snapshots and to recorded history events alike; there is no exception. Recording new
  information MUST be achieved by storing a new object, never by changing an existing one.
- **FR-009**: Stored object names MUST encode creation date and time such that objects sort chronologically
  by name alone, without relying on timestamps reported by the destination.
- **FR-010**: Stored object names MUST be constructed so that every destination in scope, including
  removable media, can store them unchanged and without substitution.
- **FR-011**: The system MUST apply a retention policy that removes snapshots older than the retention
  window while preserving a decreasing-density history (recent snapshots kept densely, older ones sparsely).
- **FR-012**: The system MUST NOT delete any snapshot until a newer snapshot has been confirmed
  successfully written to that same destination.
- **FR-013**: The system MUST always retain at least one snapshot per destination regardless of age.
- **FR-014**: Retention MUST be applied per destination and MUST NOT delete snapshots from a destination
  the user has disabled.
- **FR-015**: The system MUST NOT prune a safety snapshot (FR-047) while the restore it protects is still
  undoable, irrespective of its age. A restore MUST remain undoable for 30 days after it completed, and the
  most recent restore at each destination MUST remain undoable indefinitely regardless of its age. Once a
  restore ceases to be undoable, its safety snapshot MUST become subject to ordinary retention.

**Destinations**

- **FR-016**: Users MUST be able to choose a local folder as a backup destination, and that choice MUST
  survive app restarts and device reboots without re-prompting.
- **FR-017**: Users MUST be able to connect a Google account and select either a private storage area
  invisible to the user, or a visible folder the user can browse in Google Drive, or both.
- **FR-018**: Users MUST be able to enable any combination of destinations simultaneously, including none.
- **FR-019**: Each destination MUST back up independently; a failure at one MUST NOT prevent, delay or roll
  back any other.
- **FR-020**: The system MUST warn the user when the only enabled destinations share a single point of
  failure — specifically when both Google destinations are enabled and no other destination is.

**Restore**

- **FR-021**: The system MUST offer the user an explicit choice, at each restore, between **replacing** all
  current wallet data with the snapshot's contents and **merging** the snapshot into the current data.
  Neither mode may be preselected or applied by default. In merge mode the snapshot MUST be authoritative
  for every record it contains: a record present in both the snapshot and the current wallet MUST be
  replaced by the snapshot's version, and a record present only in the current wallet MUST be left
  untouched.
- **FR-022**: The system MUST state, in plain language at the point of choosing, what each mode will do to
  data created after the snapshot was taken.
- **FR-023**: The system MUST require explicit confirmation before a restore modifies any data.
- **FR-024**: The system MUST list available snapshots from every enabled destination in one place,
  newest first, each showing its creation date and time, size, and originating destination.
- **FR-025**: The system MUST leave existing data unchanged if a restore fails at any point; a restore MUST
  complete fully or not at all.
- **FR-026**: Users MUST be able to trigger a backup on demand at any time, independently of the schedule.

**History record**

- **FR-027**: The system MUST maintain, at each destination alongside that destination's snapshots, a
  machine-readable record of every backup, restore, undo and pruning that has affected it.
- **FR-028**: The record MUST be composed of independently stored events. Recording a new event MUST NOT
  require modifying, replacing or reading any object already stored, so that two devices recording events
  at the same destination never contend for the same object.
- **FR-029**: Each recorded event MUST carry a unique identifier, an identifier for the device that
  performed it, the instant it occurred, the kind of event, which snapshot it concerned, the outcome, and
  the version of the event format it was written with.
- **FR-030**: Each recorded event MUST be independently verifiable against a fingerprint of its own
  contents. An event that fails verification MUST be excluded from the history and MUST NOT prevent any
  other event from being read. An event written in a format newer than the running application understands
  MUST NOT be excluded on that basis: it MUST be listed using whatever the application does recognise, and
  an unrecognised kind of event MUST appear as a plain entry rather than being hidden.
- **FR-031**: Each recorded backup event MUST include a summary of the snapshot's contents — record counts
  by type, the date range of the transactions it covers, balance totals, and the snapshot's fingerprint —
  captured at the time the snapshot is taken, so that the history and comparison features never need to
  read a snapshot.
- **FR-032**: Each recorded restore event MUST include the snapshot restored, the mode chosen, the safety
  snapshot captured beforehand, and a summary of the wallet's contents both before and after.
- **FR-033**: An undo event MUST reference, by identifier, the event it reverses. Whether a restore has
  been undone MUST be determined from these references alone, and MUST NOT depend on comparing times
  recorded by different devices.
- **FR-034**: The record MUST be append-only. The system MUST NOT alter or remove an event once written;
  an event that has been reversed MUST be marked as such by adding a new event, not by editing it.
- **FR-035**: When the record at a destination is written by more than one device, the system MUST preserve
  events from every device. Combining any two views of the record MUST be based on event identity, MUST
  produce the same history irrespective of the order in which events are encountered, and MUST be
  unaffected by an event being encountered more than once.
- **FR-036**: Events recorded by a single device MUST have a total order that does not depend on that
  device's clock. Ordering between devices is presentational only and MUST NOT determine any behaviour.
- **FR-037**: A device's clock being wrong, adjusted or in a different timezone MUST NOT cause an event to
  be lost, to be reordered relative to other events from the same device, or to be attributed to the wrong
  device; and the system MUST be able to detect that a device's recorded times disagree with its own order
  of events.
- **FR-038**: The system MAY consolidate older events into fewer stored objects to bound the number of
  objects at a destination. Consolidation MUST NOT lose an event, MUST be safe to interrupt at any point,
  and MUST NOT require exclusive access to the destination. An interrupted or concurrent consolidation MUST
  at worst leave duplicate copies of events, never a gap. Consolidation MUST carry through verbatim any
  part of an event the running application does not recognise, so that an older application cannot discard
  information written by a newer one.
- **FR-039**: The system MUST be able to reconstruct a usable history from the snapshots present at a
  destination alone, when the record is missing, unreadable or incomplete, and MUST tell the user that
  earlier history was unavailable.
- **FR-040**: Loss or corruption of the record MUST NOT prevent any snapshot from being listed or restored.
- **FR-041**: Users MUST be able to view the history, showing every event in reverse chronological order
  with its date, time, kind, destination, performing device and outcome.

**Comparison**

- **FR-042**: Users MUST be able to compare any snapshot against the wallet's current contents before
  restoring it, without performing the restore.
- **FR-043**: The comparison MUST report differences in record counts by type, the date range covered, and
  balance totals.
- **FR-044**: The comparison MUST state what each restore mode would do to the differences it identifies.
- **FR-045**: Users MUST be able to compare any two snapshots against each other, not only a snapshot
  against the current wallet.
- **FR-046**: A comparison MUST be produced from the summaries recorded with each backup event and MUST NOT
  require reading or downloading a snapshot.

**Undo**

- **FR-047**: The system MUST capture a snapshot of the wallet's current contents immediately before any
  restore modifies data, and MUST abort the restore if that snapshot cannot be captured.
- **FR-048**: Users MUST be able to undo a completed restore, returning the wallet to exactly its state
  immediately before that restore, regardless of which mode was used.
- **FR-049**: An undo MUST be recorded as its own event referencing the restore it reversed, and MUST
  itself be reversible.
- **FR-050**: The system MUST indicate, for each restore in the history, whether it can still be undone,
  and MUST explain why when it cannot — distinguishing a restore whose undo period has elapsed from one
  whose safety snapshot is no longer present at its destination.
- **FR-051**: The system MUST prevent a restore or undo from starting while another is in progress.

**Scheduling and reporting**

- **FR-052**: The system MUST back up automatically on a recurring schedule without user interaction, under
  conditions that avoid consuming metered data or battery unexpectedly.
- **FR-053**: The system MUST defer, rather than fail, a scheduled backup when a destination is temporarily
  unreachable, and MUST retry without user intervention.
- **FR-054**: The system MUST NOT create multiple snapshots for backup runs missed while the device was
  offline or idle; a single snapshot on wake is sufficient.
- **FR-055**: The system MUST record and display, per destination, the outcome and time of the most recent
  attempt, and the number of snapshots currently stored.
- **FR-056**: Failures MUST be reported to the user in plain language that distinguishes a missing or
  revoked folder, an exhausted storage quota, a lost account connection, and a transient network problem.
- **FR-057**: The system MUST make the absence of any configured destination discoverable to the user
  without repeated interruption.
- **FR-058**: Disabling or disconnecting a destination MUST stop further backups to it and MUST NOT delete
  anything already stored there, and the app MUST say so before the user confirms.

**Destination extensibility**

- **FR-059**: All behaviour that is not storage itself — capture, scheduling, retention, the history
  record, comparison, restore, undo, and error reporting — MUST be defined once and behave identically
  regardless of which destination is in use. A destination MUST NOT carry its own copy of any of it.
- **FR-060**: A destination MUST be definable in terms of four storage operations alone: store a named
  object, list the objects present, read a named object, and remove a named object.
- **FR-061**: No behaviour in this feature may depend on any storage capability beyond those four. In
  particular the system MUST NOT require the ability to rename an object, to append to an existing object,
  to write conditionally on an object's current state, to create an object only if absent, to acquire a
  lock, or to trust a creation or modification time reported by the destination.
- **FR-062**: Adding a new destination MUST require no change to capture, scheduling, retention, history,
  comparison, restore or undo behaviour — only the four operations in FR-060 and that destination's own
  configuration and connection handling.
- **FR-063**: Each destination MUST keep its configuration, connection state and credentials isolated from
  every other destination, so that connecting, failing or disconnecting one cannot affect another.
- **FR-064**: Failures MUST be reported in terms of a destination-independent vocabulary — unreachable,
  not configured, access denied, out of space, object missing, object corrupt, transient — so that the user
  interface and retry behaviour need no knowledge of which destination failed.
- **FR-065**: A destination that cannot support an operation — for example one that cannot delete — MUST
  declare this, and the system MUST degrade predictably and tell the user which guarantees do not hold
  there, rather than failing repeatedly.
- **FR-066**: Destinations MUST be identified such that more than one instance of the same kind — for
  example two separate cloud accounts, or two folders — can be supported later without redesigning the
  model, even though only one instance of each kind is offered initially.

**Scope exclusions**

- **FR-067**: The system MUST NOT synchronise wallet data between devices, MUST NOT merge concurrent
  changes made on different installations, and MUST NOT write to a destination except to add a snapshot,
  add or consolidate history events, or prune an expired snapshot.

### Key Entities

- **Snapshot**: an immutable point-in-time capture of the complete wallet dataset. Attributes: creation
  instant, application and schema version, size, contents fingerprint, the destination it lives in, and
  whether it was captured on a schedule, on demand, or as a safety snapshot before a restore. Never
  modified after creation.
- **Safety Snapshot**: a snapshot captured automatically immediately before a restore, existing so that
  the restore can be undone. Bound to the restore event that triggered it, and protected from ordinary
  retention while that undo remains available.
- **Destination**: a place snapshots and history events are stored. One of: user-picked local folder,
  private cloud area, visible cloud folder. Attributes: enabled or not, its configuration (chosen folder or
  connected account), and its current health.
- **Destination Status**: the outcome of the most recent backup attempt for one destination — when it ran,
  whether it succeeded, the reason if not, and how many snapshots that destination currently holds.
- **History Record**: the complete set of events stored at a destination alongside its snapshots. Not a
  single object: a collection of independently stored, never-modified events, combined by identity rather
  than overwritten when several devices contribute, and reconstructible from the snapshots alone if lost.
- **History Event**: one entry in the record — a backup, restore, undo, pruning or consolidation.
  Attributes: unique identifier, the device that performed it, that device's own position in its sequence
  of events, the instant recorded, kind, outcome, the snapshot concerned, a fingerprint over its own
  contents, references to any event it reverses or supersedes, and for restores the mode chosen and the
  safety snapshot captured.
- **Device Identifier**: a value distinguishing one installation of the app from another, so that events
  from several devices can coexist at one destination and be attributed correctly in the history.
- **Snapshot Summary**: the contents digest recorded with each backup event — record counts by type, the
  transaction date range, balance totals and the snapshot fingerprint. Exists so the history and
  comparisons can be shown without reading snapshot files.
- **Retention Policy**: the rule deciding which snapshots survive, expressed in terms of age and density.
  Applied per destination, constrained never to leave a destination empty and never to remove a safety
  snapshot backing an available undo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who has lost their device can have their complete wallet restored on a new device in
  under 5 minutes from first launch, without handling any file manually.
- **SC-002**: A user can go from no protection to a working, verified first backup in under 2 minutes and
  no more than 4 taps.
- **SC-003**: After any data accident, a user can recover to a state no more than 24 hours old, in at least
  99% of cases where automatic backup has been enabled for a week or more.
- **SC-004**: 100% of snapshots offered in the restore list restore successfully; no snapshot is ever
  offered that cannot be read.
- **SC-005**: No user action or system failure results in the loss of the last remaining snapshot at any
  destination.
- **SC-006**: A failure at one destination never prevents a snapshot being written to another healthy
  destination, verified across every combination of enabled destinations.
- **SC-007**: When a backup fails, a user can tell from the backup settings screen alone what went wrong
  and what to do about it, without consulting logs or support.
- **SC-008**: Automatic backup consumes no more than 1% of daily battery and produces no user-visible
  interruption or slowdown.
- **SC-009**: A wallet of at least 10,000 transactions is captured, compared and restored without failure
  or perceptible delay in normal app use.
- **SC-010**: A user choosing between replace and merge can state, before confirming, exactly what will
  happen to the data currently in their wallet.
- **SC-011**: 100% of restores performed while their safety snapshot survives can be undone, returning the
  wallet to a state identical to the one immediately before the restore.
- **SC-012**: Every backup, restore, undo and pruning performed by the app appears in the history; no
  operation that changes stored data is absent from it.
- **SC-013**: The history and any comparison open in under 2 seconds for a destination holding 12 months
  of history, without downloading snapshot contents.
- **SC-014**: Destroying or corrupting the history costs no snapshot and blocks no restore; the rebuilt
  history still lists every snapshot present.
- **SC-015**: Two devices backing up to the same destination lose no events from either device's history,
  in every interleaving of their operations.
- **SC-016**: A new destination can be added by implementing only its four storage operations and its
  configuration, with no change to any existing destination and no change to capture, scheduling,
  retention, history, comparison, restore or undo behaviour — demonstrated by the fact that the three
  destinations shipped share one implementation of all of it.
- **SC-017**: Every acceptance scenario that does not name a specific destination passes identically
  against each destination, verified by running one shared suite against all three.
- **SC-018**: Interrupting any write — a snapshot, a history event, or a consolidation — at any point never
  loses a previously recorded event, never destroys a previously stored snapshot, and never leaves the
  history unreadable.

## Assumptions

- **Comparison is summary-level, not record-level.** Differences are reported as counts per record type,
  date ranges and balance totals — not as a list of individually changed transactions. Record-level diffing
  is substantially larger and is deferred; the summary answers "is this the snapshot I want?", which is the
  question a user restoring actually asks.
- **No encryption passphrase.** Snapshots are stored unencrypted. Every destination is already
  access-controlled by the device lock or the user's Google account, and a forgotten passphrase would
  permanently destroy the very data the feature exists to protect. This was decided explicitly, not by
  omission.
- **No destination offers atomic compare-and-swap, create-only-if-absent, renaming, locking, or
  trustworthy modification timestamps.** This was checked against the destinations in scope and found to
  hold for all of them. FR-061 exists so no part of the design can come to depend on a capability that is
  not there; any future destination is assumed to be equally limited.
- **Mutual exclusion between devices is not attempted.** A lock would fail in the direction that matters:
  a device that stops while holding one would silently halt all future backups, which is a worse outcome
  than the occasional lost history entry it would prevent. The design removes the need for one instead, by
  never having two writers address the same object.
- **The history is a convenience, never a source of truth.** Everything it says about which snapshots exist
  is derivable from the snapshots themselves, so its loss degrades the experience without costing data. The
  device's own local copy is authoritative for its own events.
- **The device identifier is generated by the app**, is random, is created once per installation, and is
  not derived from hardware, the user, or any advertising or account identifier. It requires no permission
  and exists only so two installations cannot choose the same object name and so the history can attribute
  an event to a device.
- **History volume is small enough that consolidation is about object count, not size.** At a few backups a
  day an event costs well under a kilobyte and a year of history is under a megabyte, so consolidation is
  driven by how many objects a destination can be listed efficiently, not by storage consumed.
- **Retention defaults** to keeping every snapshot from the last 7 days, weekly snapshots for 8 weeks, and
  monthly snapshots for 12 months. Chosen as a standard decreasing-density scheme; adjustable later without
  changing the model.
- **Undo availability is a requirement, not a default** (FR-015): 30 days, with the most recent restore at
  each destination never expiring. It remains subject to the safety snapshot surviving at a still-connected
  destination — a user who clears the folder by hand ends the undo early, and the app says so.
- **Daily cadence** for the automatic schedule, running when the device is idle and on an unmetered
  connection. Snapshots are ~15 KB compressed for a wallet of several hundred transactions, so frequency is
  constrained by battery and disruption, not by size or quota.
- **Android's built-in Auto Backup is out of scope.** It keeps only one overwritten copy, restores only at
  install time, and is deleted after 57 days of device inactivity — none of which satisfies the immutable,
  on-demand-restorable history this feature requires. It may be added later as an independent safety net.
- **Backups to a local folder inherit that folder's fate.** A snapshot in app-private storage does not
  survive uninstalling the app, and one on removable media does not survive removing the card. The app
  states this where the user chooses.
- **Google is the only cloud provider in scope.** Dropbox, OneDrive, WebDAV and others are deliberately
  deferred; the destination model is built so each is an isolated addition rather than a redesign.
- **The two Google destinations are one destination implementation with different settings**, not two
  independent ones. They differ only in where snapshots are placed and what the user is allowed to see, so
  treating them as separate implementations would duplicate logic that FR-059 requires to exist once.
- **Only one instance of each destination kind is offered initially.** The identity model must not
  preclude several later (FR-066), but the user interface for managing multiple instances of one kind is
  out of scope.
- **Every destination is assumed to support all four storage operations.** FR-065 exists so a future
  write-only or append-only destination degrades predictably rather than breaking the model, not because
  any destination in scope has that limitation.
- **The existing manual export and import remain available and unchanged**, and this feature reuses their
  data capture rather than introducing a second format. Merge mode reuses the import's existing upsert
  behaviour; replace mode is new.
- **Google account connection uses scopes that require no third-party security assessment**, so the feature
  carries no recurring compliance cost.
- **A user restores infrequently** — restore is a deliberate, confirmed, occasionally-used action, not a
  routine one, so it is optimised for safety and clarity over speed.
