package com.ivy.backup

import com.ivy.data.model.backup.SnapshotRef

sealed interface BackupEvent {
    /** The user picked a folder from the system file picker. */
    data class FolderPicked(val treeUri: String) : BackupEvent

    /** The user tapped "back up now". */
    data object BackupNow : BackupEvent

    /** The user tapped a snapshot to see its details / start a restore. */
    data class SnapshotSelected(val ref: SnapshotRef) : BackupEvent

    /** The user confirmed the restore after seeing the comparison. */
    data class RestoreConfirmed(val ref: SnapshotRef) : BackupEvent

    /** The user dismissed the restore confirmation. */
    data object RestoreDismissed : BackupEvent

    /** Refresh the listing. */
    data object Refresh : BackupEvent
}
