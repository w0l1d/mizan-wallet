package com.ivy.domain.usecase.backup

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeProvider
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.backup.local.SnapshotArchive
import com.ivy.data.backup.local.SnapshotManifest
import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Writes one snapshot of the wallet into the backup folder, then prunes the old ones.
 *
 * The data document inside the archive is the export the existing manual backup produces, byte
 * for byte, so a snapshot stays a file the app's own import already accepts.
 */
class CaptureSnapshotUseCase @Inject constructor(
    private val storage: BackupStorage,
    private val computeSummary: ComputeWalletSummaryUseCase,
    private val backupData: BackupDataUseCase,
    private val applyRetention: ApplyRetentionUseCase,
    private val environment: BackupEnvironment,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatchersProvider,
) {
    suspend operator fun invoke(
        origin: SnapshotOrigin,
    ): Either<BackupError, SnapshotRef> = withContext(dispatchers.io) {
        either {
            val capturedAt = timeProvider.utcNow()
            val summary = computeSummary()
            val data = backupData.generateJsonBackup().toByteArray(DATA_CHARSET)

            // Listing first is also how an unreachable or unconfigured folder is discovered
            // before any work is done with the wallet's contents.
            val before = storage.list().bind()
            val name = SnapshotNaming.buildUnique(
                capturedAt = capturedAt,
                device = environment.deviceName,
                origin = origin,
                taken = before.snapshots.map { it.name }.toSet(),
            )

            val manifest = SnapshotManifest(
                capturedAt = capturedAt,
                origin = origin,
                appVersion = environment.appVersion,
                dataSchemaVersion = environment.dataSchemaVersion,
                dataEntry = DATA_ENTRY,
                dataSha256 = "", // filled in by the archive writer from the bytes it writes
                summary = summary,
            )

            val written = storage.write(name) { out ->
                SnapshotArchive.write(out, manifest, data)
            }.bind()

            // Re-reading the folder is what "confirmed written" means: a storage that reported
            // success and a folder that contains the object are two different claims.
            val after = storage.list().bind()
            applyRetention(justWritten = written, listing = after.snapshots).bind()

            written
        }
    }

    private companion object {
        const val DATA_ENTRY = "wallet.json"

        /** The charset the existing export writes; changing it would break the manual import. */
        val DATA_CHARSET = Charsets.UTF_16
    }
}
