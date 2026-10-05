package com.ivy.domain.usecase.backup

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.backup.IvyWalletCompleteData
import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.backup.local.SnapshotArchive
import com.ivy.data.backup.local.WalletDataWriter
import com.ivy.data.db.transaction.DatabaseTransaction
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Puts a snapshot's records back into the wallet.
 *
 * Everything that could make the restore pointless is checked before anything is changed: the
 * archive has to be readable, written by a schema this build understands, and carry the bytes its
 * own digest claims. Only then is a safety snapshot taken, and only then is anything written.
 *
 * The write is a merge by record identity inside one transaction. Records the snapshot carries
 * win; records created since it was taken are untouched; a failure part-way leaves the wallet
 * exactly as it was, which is the guarantee [BackupError.RestoreFailed] carries in its name.
 *
 * Returns the safety snapshot, so the screen that confirmed the restore can say where the undo is.
 *
 * One restore at a time. A second one that arrives while the first is in flight is refused on the
 * spot rather than queued: by the time a queued restore ran, the wallet it was shown a comparison
 * against would no longer exist, and the user would be confirming a decision about a wallet that
 * had already been replaced.
 */
class RestoreSnapshotUseCase @Inject constructor(
    private val storage: BackupStorage,
    private val capture: CaptureSnapshotUseCase,
    private val transaction: DatabaseTransaction,
    private val writer: WalletDataWriter,
    private val environment: BackupEnvironment,
    private val json: Json,
    private val dispatchers: DispatchersProvider,
) {
    private val inProgress = Mutex()

    suspend operator fun invoke(
        ref: SnapshotRef,
    ): Either<BackupError, SnapshotRef> = withContext(dispatchers.io) {
        if (!inProgress.tryLock()) {
            Either.Left(BackupError.RestoreFailed(IllegalStateException(ALREADY_RUNNING)))
        } else {
            try {
                restore(ref)
            } finally {
                inProgress.unlock()
            }
        }
    }

    private suspend fun restore(ref: SnapshotRef): Either<BackupError, SnapshotRef> =
        either {
            val manifest = storage.read(ref) { input ->
                SnapshotArchive.readManifest(ref.name, input)
            }.bind().bind()

            ensure(manifest.dataSchemaVersion <= environment.dataSchemaVersion) {
                BackupError.SnapshotUnreadable(
                    ref.name,
                    "this snapshot was written by a newer version of the app " +
                        "(it stores data version ${manifest.dataSchemaVersion}; this app " +
                        "reads up to ${environment.dataSchemaVersion}). Update the app, then " +
                        "restore it.",
                )
            }

            val bytes = storage.read(ref) { input ->
                SnapshotArchive.readDataEntry(ref.name, input, manifest)
            }.bind().bind()

            ensure(SnapshotArchive.sha256(bytes) == manifest.dataSha256) {
                BackupError.SnapshotUnreadable(
                    ref.name,
                    "the snapshot's contents do not match the digest recorded when it was " +
                        "written, so it is damaged or incomplete",
                )
            }

            val data = runCatching {
                json.decodeFromString(
                    IvyWalletCompleteData.serializer(),
                    String(bytes, DATA_CHARSET),
                )
            }.getOrElse {
                raise(
                    BackupError.SnapshotUnreadable(
                        ref.name,
                        "the snapshot's data could not be read: ${it.message}",
                    ),
                )
            }

            // Taken last, so a restore that was never going to happen does not leave the user a
            // safety snapshot they have to work out the meaning of.
            val safety = capture(SnapshotOrigin.Safety).bind()

            runCatching {
                transaction.withTransaction { writer.write(data) }
            }.getOrElse { raise(BackupError.RestoreFailed(it)) }

            safety
        }

    private companion object {
        /** The charset the export is written in; see the capture use case. */
        val DATA_CHARSET = Charsets.UTF_16

        const val ALREADY_RUNNING =
            "a restore is already running; wait for it to finish before starting another"
    }
}
