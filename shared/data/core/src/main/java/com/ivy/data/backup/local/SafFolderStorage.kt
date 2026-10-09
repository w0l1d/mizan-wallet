package com.ivy.data.backup.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupStorage] over a folder the user picked through the Storage Access Framework.
 *
 * Two SAF behaviours shape this class:
 *
 * - creating a file whose name is taken does not fail, it creates `name (1)` instead. So every
 *   write checks the name it actually got back and fails loudly when it differs, rather than
 *   quietly producing a second snapshot the rest of the feature would never find.
 * - `listFiles()` costs one round trip per child, so a listing walks the folder exactly once and
 *   does everything it needs from that one walk.
 */
@Singleton
class SafFolderStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: BackupDestinationConfig,
    private val dispatchers: DispatchersProvider,
) : BackupStorage {

    /**
     * Keeps the folder readable after a reboot or a reinstall-and-re-pick. Called with the uri the
     * `ACTION_OPEN_DOCUMENT_TREE` result carried; without it the grant dies with the process.
     */
    suspend fun remember(treeUri: Uri, now: Instant): Either<BackupError, Unit> =
        withContext(dispatchers.io) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                config.set(treeUri = treeUri.toString(), configuredAt = now)
            }.fold(
                onSuccess = { Unit.right() },
                onFailure = { BackupError.AccessDenied.left() },
            )
        }

    override suspend fun list(): Either<BackupError, BackupStorage.Listing> =
        withContext(dispatchers.io) {
            folder().map { folder ->
                val described = folder.listFiles()
                    .filter { it.isFile }
                    .map(::describe)

                BackupStorage.Listing(
                    snapshots = described.filterNotNull().sortedByDescending { it.name },
                    unreadable = described.count { it == null },
                )
            }
        }

    override suspend fun write(
        name: String,
        content: suspend (OutputStream) -> Unit,
    ): Either<BackupError, SnapshotRef> = withContext(dispatchers.io) {
        folder().flatMap { folder ->
            val created = folder.createFile(MIME_ZIP, name)
                ?: return@flatMap BackupError.WriteFailed(null).left()

            // SAF renames rather than refusing; a renamed file is a snapshot nothing will find.
            if (created.name != name) {
                created.delete()
                return@flatMap BackupError.WriteFailed(
                    IllegalStateException("the folder created '${created.name}', not '$name'"),
                ).left()
            }

            val written = runCatching {
                context.contentResolver.openOutputStream(created.uri, "wt")
                    ?.use { content(it) }
                    ?: throw IOException("the folder would not open '$name' for writing")
            }

            written.fold(
                onSuccess = {
                    describe(created)?.right()
                        ?: BackupError.SnapshotUnreadable(name, "just-written snapshot is unreadable")
                            .left()
                },
                onFailure = { cause ->
                    created.delete() // a failed write leaves no partial object
                    writeFailure(cause).left()
                },
            )
        }
    }

    override suspend fun <T> read(
        ref: SnapshotRef,
        consume: suspend (InputStream) -> T,
    ): Either<BackupError, T> = withContext(dispatchers.io) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(ref.uri))
                ?.use { consume(it) }
                ?: throw FileNotFoundException(ref.name)
        }.fold(
            onSuccess = { it.right() },
            onFailure = { BackupError.SnapshotUnreadable(ref.name, gone(it)).left() },
        )
    }

    override suspend fun delete(ref: SnapshotRef): Either<BackupError, Unit> =
        withContext(dispatchers.io) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(ref.uri))?.delete() }
                .fold(
                    // A document that is already gone satisfies the post-condition too.
                    onSuccess = { Unit.right() },
                    onFailure = { BackupError.AccessDenied.left() },
                )
        }

    private suspend fun folder(): Either<BackupError, DocumentFile> {
        val destination = config.destination.first()
            ?: return BackupError.NotConfigured.left()

        val folder = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(destination.treeUri)) }
            .getOrNull()

        return when {
            folder == null || !folder.exists() -> BackupError.AccessDenied.left()
            !folder.canWrite() -> BackupError.AccessDenied.left()
            else -> folder.right()
        }
    }

    /** Null for anything in the folder that is not a snapshot this app can read. */
    private fun describe(file: DocumentFile): SnapshotRef? {
        val name = file.name ?: return null
        val parsed = SnapshotNaming.parse(name)
        val manifest = runCatching {
            context.contentResolver.openInputStream(file.uri)
                ?.use { SnapshotArchive.readManifest(name, it) }
        }.getOrNull()?.getOrNull()

        return if (parsed == null || manifest == null) {
            null
        } else {
            SnapshotRef(
                name = name,
                uri = file.uri.toString(),
                // The manifest is authoritative: a storage time is the copy's, not the data's.
                capturedAt = manifest.capturedAt,
                origin = parsed.origin,
                summary = manifest.summary,
                sizeBytes = file.length(),
            )
        }
    }

    private fun writeFailure(cause: Throwable): BackupError = when {
        cause is IOException && cause.message?.contains(OUT_OF_SPACE, ignoreCase = true) == true ->
            BackupError.OutOfSpace

        cause is SecurityException -> BackupError.AccessDenied
        else -> BackupError.WriteFailed(cause)
    }

    private fun gone(cause: Throwable): String = when (cause) {
        is FileNotFoundException -> "no longer present"
        is SecurityException -> "no longer readable"
        else -> cause.message ?: "could not be read"
    }

    private inline fun <A, B, C> Either<A, B>.flatMap(f: (B) -> Either<A, C>): Either<A, C> =
        when (this) {
            is Either.Left -> this
            is Either.Right -> f(value)
        }

    companion object {
        private const val MIME_ZIP = "application/zip"
        private const val OUT_OF_SPACE = "space"
    }
}
