package com.ivy.domain.usecase.backup

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.RetentionDecision
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import javax.inject.Inject

/**
 * Decides which snapshots stay in the folder and removes the rest.
 *
 * The decision describes the folder as it is afterwards, not as it was meant to be: a snapshot
 * whose deletion failed comes back in [RetentionDecision.keep], because it is still there.
 */
class ApplyRetentionUseCase @Inject constructor(
    private val storage: BackupStorage,
) {
    /**
     * [justWritten] is the snapshot the caller believes it has just created. Nothing is deleted
     * unless [listing] actually contains an object by that name — old copies are only ever given
     * up once the new one is known to exist.
     */
    suspend operator fun invoke(
        justWritten: SnapshotRef,
        listing: List<SnapshotRef>,
    ): Either<BackupError, RetentionDecision> = either {
        if (listing.none { it.name == justWritten.name }) {
            return@either RetentionDecision(keep = listing, delete = emptyList())
        }

        // Names encode a UTC timestamp, so sorting by name is sorting by age, and it survives two
        // archives that happen to share a capturedAt.
        val (protected, expendable) = listing
            .sortedByDescending { it.name }
            .partition { it.origin == SnapshotOrigin.Safety }

        val kept = expendable.take(KEEP_COUNT)
        val condemned = expendable.drop(KEEP_COUNT)

        val failedToDelete = condemned.filter { storage.delete(it).isLeft() }

        RetentionDecision(
            keep = protected + kept + failedToDelete,
            delete = condemned - failedToDelete.toSet(),
        )
    }

    companion object {
        /**
         * Enough that a problem noticed weeks later still has a copy from before it, few enough
         * that a daily backup does not fill a phone.
         */
        const val KEEP_COUNT = 30
    }
}
