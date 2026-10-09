package com.ivy.data.model.backup

import java.time.Instant

/**
 * One immutable backup object in the destination.
 *
 * Identity is [name], which is unique by construction. A `SnapshotRef` exists only for an object
 * whose manifest parsed successfully — an unreadable or foreign file is counted separately and is
 * never offered for restore (MVP-007).
 *
 * [uri] is a transient storage handle. It is deliberately not persisted anywhere: a stored URI can
 * outlive the grant that makes it usable, and the stale one would be the one we act on.
 */
data class SnapshotRef(
    val name: String,
    val uri: String,
    val capturedAt: Instant,
    val origin: SnapshotOrigin,
    val summary: SnapshotSummary,
    val sizeBytes: Long,
)
