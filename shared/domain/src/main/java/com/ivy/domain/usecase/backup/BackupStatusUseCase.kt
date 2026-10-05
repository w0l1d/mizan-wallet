package com.ivy.domain.usecase.backup

import com.ivy.data.backup.local.BackupDestinationConfig
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

sealed interface BackupStatus {
    data object NotConfigured : BackupStatus
    data object NeverBackedUp : BackupStatus
    data class Healthy(
        val lastSnapshotAt: Instant,
        val snapshotCount: Int,
    ) : BackupStatus

    data class Failing(
        val lastSnapshotAt: Instant?,
        val lastAttemptError: String?,
    ) : BackupStatus

    data object Unreachable : BackupStatus
}

class BackupStatusUseCase @Inject constructor(
    private val listSnapshots: ListSnapshotsUseCase,
    private val destinationConfig: BackupDestinationConfig,
) {
    suspend operator fun invoke(): BackupStatus {
        val destination = destinationConfig.destination.first()
            ?: return BackupStatus.NotConfigured

        val attempt = destinationConfig.lastAttempt.first()

        return listSnapshots().fold(
            ifLeft = { BackupStatus.Unreachable },
            ifRight = { listing ->
                val newest = listing.snapshots.maxByOrNull { it.capturedAt }
                when {
                    newest == null && attempt == null -> BackupStatus.NeverBackedUp
                    newest == null -> BackupStatus.NeverBackedUp
                    attempt != null && !attempt.success -> BackupStatus.Failing(
                        lastSnapshotAt = newest.capturedAt,
                        lastAttemptError = attempt.errorMessage,
                    )
                    else -> BackupStatus.Healthy(
                        lastSnapshotAt = newest.capturedAt,
                        snapshotCount = listing.snapshots.size,
                    )
                }
            },
        )
    }
}
