package com.ivy.data.model.backup

/**
 * Every backup operation returns `Either<BackupError, T>` (Constitution Principle II).
 *
 * Each case names a cause the user can act on differently; a single "backup failed" would be
 * indistinguishable from a full disk, a deleted folder, or a transient write problem.
 */
sealed interface BackupError {

    /** No destination folder has been picked yet. */
    data object NotConfigured : BackupError

    /** The persisted grant was revoked, or the folder was moved or deleted. */
    data object AccessDenied : BackupError

    /** The destination has no room for the snapshot. */
    data object OutOfSpace : BackupError

    /** The write started and did not complete. No usable object was left behind. */
    data class WriteFailed(val cause: Throwable?) : BackupError

    /** An object in the folder could not be read as a snapshot. It is never offered for restore. */
    data class SnapshotUnreadable(val name: String, val reason: String) : BackupError

    /**
     * The restore did not happen and the wallet is unchanged — the transaction rolled back.
     * The guarantee is in the name so no caller has to ask whether the wallet was partially written.
     */
    data class RestoreFailed(val cause: Throwable?) : BackupError
}
