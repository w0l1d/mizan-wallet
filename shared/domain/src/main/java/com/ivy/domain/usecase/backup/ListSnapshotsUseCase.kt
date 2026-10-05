package com.ivy.domain.usecase.backup

import arrow.core.Either
import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.model.backup.BackupError
import javax.inject.Inject

/**
 * What is in the backup folder: the snapshots this app can restore, newest first, and a count of
 * everything else it found. An empty folder is an answer, not a failure.
 */
@Suppress("UnnecessaryPassThroughClass") // the UI layer does not get to know BackupStorage exists
class ListSnapshotsUseCase @Inject constructor(
    private val storage: BackupStorage,
) {
    suspend operator fun invoke(): Either<BackupError, BackupStorage.Listing> = storage.list()
}
