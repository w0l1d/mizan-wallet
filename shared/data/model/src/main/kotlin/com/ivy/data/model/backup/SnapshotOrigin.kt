package com.ivy.data.model.backup

/**
 * Why a snapshot exists.
 *
 * A sealed type rather than an `isProtected` boolean: retention protects [Safety] specifically
 * (MVP-013), and a boolean would let a future rule protect the wrong thing.
 */
sealed interface SnapshotOrigin {

    /** Written by the periodic background job. */
    data object Scheduled : SnapshotOrigin

    /** Written because the user asked for a backup now. */
    data object Manual : SnapshotOrigin

    /** The state of the wallet immediately before a restore. Never pruned by retention. */
    data object Safety : SnapshotOrigin
}
