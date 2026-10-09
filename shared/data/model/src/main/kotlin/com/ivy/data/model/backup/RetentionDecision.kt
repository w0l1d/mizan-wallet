package com.ivy.data.model.backup

/**
 * The outcome of applying the retention rule to a listing.
 *
 * [delete] is only ever acted on after the new snapshot's write has been confirmed (MVP-011); that
 * ordering is enforced by the capture use case, not by this type.
 */
data class RetentionDecision(
    val keep: List<SnapshotRef>,
    val delete: List<SnapshotRef>,
)
