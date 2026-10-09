package com.ivy.data.backup.local

import arrow.core.Either
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotRef
import java.io.InputStream
import java.io.OutputStream

/**
 * The destination a wallet's snapshots live in.
 *
 * Exactly four operations — [list], [write], [read], [delete] — and no more. Total storage use is
 * derived in the domain layer by summing a listing's sizes rather than asked of the destination,
 * because a destination that cannot report aggregate usage must still be implementable here.
 *
 * Every function is `suspend`, moves to IO itself, and returns `Either<BackupError, T>`: a revoked
 * grant, a full disk and a deleted folder are values a caller branches on, not exceptions.
 *
 * Objects are created and deleted, never edited. Creating an object is never used to claim
 * exclusivity, because the underlying storage de-duplicates a colliding name rather than refusing.
 */
interface BackupStorage {

    /**
     * @param snapshots newest first, ordered by name — names sort chronologically by construction,
     *   so no storage-reported timestamp is consulted.
     * @param unreadable how many objects are present but could not be parsed as a snapshot. They
     *   never appear in [snapshots]: one corrupt object does not fail the listing, and it is never
     *   offered as something the user could restore.
     */
    data class Listing(
        val snapshots: List<SnapshotRef>,
        val unreadable: Int,
    )

    /** An empty destination is `Listing(emptyList(), 0)` — a success, not an error. */
    suspend fun list(): Either<BackupError, Listing>

    /**
     * Writes one new object and returns a ref to it only once the bytes are durable: retention
     * prunes on the strength of this return value.
     *
     * Verifies the created object's actual name matches [name] and fails if it does not — the
     * storage silently renames a colliding file instead of rejecting it, so uniqueness is the
     * caller's guarantee and this is where it is checked.
     *
     * A failure leaves no partial object visible.
     */
    suspend fun write(
        name: String,
        content: suspend (OutputStream) -> Unit,
    ): Either<BackupError, SnapshotRef>

    /**
     * Opens a snapshot's bytes for the duration of [consume].
     *
     * `SnapshotUnreadable` if the object has disappeared or been truncated since it was listed —
     * a real race, because the folder is user-visible and the user may delete from a file manager
     * while the restore screen is open.
     */
    suspend fun <T> read(
        ref: SnapshotRef,
        consume: suspend (InputStream) -> T,
    ): Either<BackupError, T>

    /**
     * Removes one object. Deleting an object that is already gone is a **success**: the
     * post-condition "this snapshot is not in the destination" holds either way.
     */
    suspend fun delete(ref: SnapshotRef): Either<BackupError, Unit>
}
