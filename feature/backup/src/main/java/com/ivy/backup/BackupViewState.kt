package com.ivy.backup

import com.ivy.data.model.backup.SnapshotRef
import kotlinx.collections.immutable.ImmutableList

/**
 * What the backup settings screen shows — a sealed hierarchy rather than a data class of nullable
 * fields, so "loading", "not configured" and "ready" cannot be conflated by accident.
 */
sealed interface BackupViewState {

    /** No destination folder has been picked. The only action is "choose folder". */
    data object NotConfigured : BackupViewState

    /** A folder exists but we are still listing snapshots or running a backup. */
    data object Loading : BackupViewState

    /** The happy path: a folder is configured and the listing has arrived. */
    data class Ready(
        val folderName: String,
        val snapshots: ImmutableList<SnapshotRef>,
        val unreadableCount: Int,
    ) : BackupViewState

    /** Something went wrong. The user sees a message and can retry or re-pick a folder. */
    data class Failed(
        val message: String,
    ) : BackupViewState
}
