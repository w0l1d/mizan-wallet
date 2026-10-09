package com.ivy.backup

import com.ivy.data.model.backup.BackupError

fun BackupError.userMessage(): String = when (this) {
    BackupError.NotConfigured -> "No backup folder configured."
    BackupError.AccessDenied ->
        "Cannot reach the backup folder. It may have been moved or its permission revoked."
    BackupError.OutOfSpace -> "The backup folder is full."
    is BackupError.WriteFailed -> "The backup could not be written: ${cause?.message}"
    is BackupError.SnapshotUnreadable -> "Cannot read snapshot '$name': $reason"
    is BackupError.RestoreFailed ->
        "Restore failed — the wallet is unchanged: ${cause?.message}"
}
