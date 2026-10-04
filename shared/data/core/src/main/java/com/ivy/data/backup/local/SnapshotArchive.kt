package com.ivy.data.backup.local

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.ivy.data.model.backup.BackupError
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The on-disk shape of a snapshot: a zip holding `manifest.json` and one data document.
 *
 * The data document is written byte for byte as it was produced, so a snapshot is still a file the
 * existing manual import accepts, and a restore compares the bytes it reads against the digest the
 * manifest recorded.
 */
object SnapshotArchive {

    private const val SHA256 = "SHA-256"

    fun write(out: OutputStream, manifest: SnapshotManifest, data: ByteArray) {
        val withDigest = manifest.copy(dataSha256 = sha256(data))
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SnapshotManifest.ENTRY_NAME))
            zip.write(SnapshotManifestCodec.encode(withDigest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(withDigest.dataEntry))
            zip.write(data)
            zip.closeEntry()
        }
    }

    fun readManifest(
        name: String,
        input: InputStream,
    ): Either<BackupError.SnapshotUnreadable, SnapshotManifest> =
        extract(name, input, SnapshotManifest.ENTRY_NAME).flatMap { bytes ->
            SnapshotManifestCodec.decode(name, String(bytes, Charsets.UTF_8))
        }

    fun readDataEntry(
        name: String,
        input: InputStream,
        manifest: SnapshotManifest,
    ): Either<BackupError.SnapshotUnreadable, ByteArray> = extract(name, input, manifest.dataEntry)

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance(SHA256)
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it) }

    private fun extract(
        name: String,
        input: InputStream,
        entryName: String,
    ): Either<BackupError.SnapshotUnreadable, ByteArray> = runCatching {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var bytes: ByteArray? = null
            while (entry != null) {
                // A zip can name an entry outside its own root; such an archive is not ours.
                require(!escapesRoot(entry.name)) { "entry '${entry?.name}' escapes the archive" }
                if (entry.name == entryName) bytes = zip.readBytes()
                entry = zip.nextEntry
            }
            bytes
        }
    }.fold(
        onSuccess = { bytes ->
            bytes?.right() ?: unreadable(name, "the archive has no '$entryName' entry")
        },
        onFailure = { unreadable(name, "the archive could not be read: ${it.message}") },
    )

    private fun escapesRoot(entryName: String): Boolean =
        entryName.startsWith("/") ||
            entryName.split('/', '\\').any { it == ".." }

    private fun unreadable(
        name: String,
        reason: String,
    ): Either<BackupError.SnapshotUnreadable, Nothing> =
        BackupError.SnapshotUnreadable(name, reason).left()
}
