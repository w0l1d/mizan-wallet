package com.ivy.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.domain.usecase.backup.CaptureSnapshotUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The daily backup, run by WorkManager.
 *
 * It does one thing: capture a snapshot. Everything that decides whether that is possible — a
 * folder being configured, space, retention — belongs to the use case, so a scheduled backup and
 * a "back up now" from the settings screen cannot drift apart.
 *
 * The outcome is translated into a WorkManager result by one rule: retry only what another
 * attempt could plausibly fix. A missing folder or a revoked grant needs the user, not a retry,
 * and a destination that is full will not empty itself in the next few minutes. None of those
 * stop the schedule — periodic work runs again at the next interval regardless.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val capture: CaptureSnapshotUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = capture(SnapshotOrigin.Scheduled).fold(
        ifLeft = ::resultFor,
        ifRight = { Result.success() },
    )

    private fun resultFor(error: BackupError): Result = when (error) {
        is BackupError.WriteFailed -> Result.retry()
        else -> Result.failure()
    }
}
