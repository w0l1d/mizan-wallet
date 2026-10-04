package com.ivy.data.backup.local.fake

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * An in-memory [BackupStorage] that reproduces the **awkward** behaviours of the real one:
 *
 * - creating an object whose name is taken does not fail, it silently stores a de-duplicated
 *   name — so [write] detects the mismatch, discards the object and fails;
 * - deleting an object that is not there succeeds;
 * - an object can disappear between [list] and [read], because the folder is user-visible.
 *
 * A fake that is nicer than reality tests nothing worth testing.
 *
 * [describe] turns a stored object into a ref; returning null makes it one of the listing's
 * unreadable objects. The default reads the name only, which is enough for every test that does
 * not care about a snapshot's contents.
 */
class FakeBackupStorage(
    private val describe: (name: String, bytes: ByteArray) -> SnapshotRef? = ::describeByName,
) : BackupStorage {

    private val objects = LinkedHashMap<String, ByteArray>()

    /** Set to fail the next call of any operation, then cleared. */
    var failNext: BackupError? = null

    /** Names that [read] reports as gone, as if deleted from a file manager since listing. */
    private val vanished = mutableSetOf<String>()

    /** Names whose bytes [read] truncates, as if the write that produced them was interrupted. */
    private val truncated = mutableSetOf<String>()

    val names: List<String> get() = objects.keys.toList()

    fun put(name: String, bytes: ByteArray) {
        objects[name] = bytes
    }

    /** Makes [name] unreadable without removing it from a listing taken before this call. */
    fun vanish(name: String) {
        vanished += name
    }

    fun truncate(name: String) {
        truncated += name
    }

    override suspend fun list(): Either<BackupError, BackupStorage.Listing> {
        takeFailure()?.let { return it.left() }
        val described = objects.entries.map { (name, bytes) -> describe(name, bytes) }
        return BackupStorage.Listing(
            snapshots = described.filterNotNull().sortedByDescending { it.name },
            unreadable = described.count { it == null },
        ).right()
    }

    override suspend fun write(
        name: String,
        content: suspend (OutputStream) -> Unit,
    ): Either<BackupError, SnapshotRef> {
        takeFailure()?.let { return it.left() }

        val buffer = ByteArrayOutputStream()
        content(buffer)
        val bytes = buffer.toByteArray()

        // The real storage renames rather than refusing; mirror that, then catch it.
        val createdName = deDuplicate(name)
        objects[createdName] = bytes

        return if (createdName != name) {
            objects.remove(createdName) // a failed write leaves no partial object
            BackupError.WriteFailed(
                IllegalStateException("storage created '$createdName' when asked for '$name'"),
            ).left()
        } else {
            describe(name, bytes)?.right()
                ?: BackupError.SnapshotUnreadable(name, "just-written snapshot is unreadable")
                    .left()
        }
    }

    override suspend fun <T> read(
        ref: SnapshotRef,
        consume: suspend (InputStream) -> T,
    ): Either<BackupError, T> {
        takeFailure()?.let { return it.left() }

        return if (ref.name in vanished || ref.name !in objects) {
            BackupError.SnapshotUnreadable(ref.name, "no longer present").left()
        } else {
            val stored = objects.getValue(ref.name)
            val bytes = if (ref.name in truncated) stored.copyOf(stored.size / 2) else stored
            consume(ByteArrayInputStream(bytes)).right()
        }
    }

    override suspend fun delete(ref: SnapshotRef): Either<BackupError, Unit> {
        takeFailure()?.let { return it.left() }
        objects.remove(ref.name) // removing something already gone is a success
        return Unit.right()
    }

    private fun takeFailure(): BackupError? = failNext?.also { failNext = null }

    private fun deDuplicate(name: String): String {
        if (name !in objects) return name
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var n = 1
        while ("$stem ($n)$suffix" in objects) n++
        return "$stem ($n)$suffix"
    }
}

private fun describeByName(name: String, bytes: ByteArray): SnapshotRef? =
    SnapshotNaming.parse(name)?.let { parsed ->
        SnapshotRef(
            name = name,
            uri = "fake://$name",
            capturedAt = parsed.capturedAt,
            origin = parsed.origin,
            summary = SnapshotSummary.Empty,
            sizeBytes = bytes.size.toLong(),
        )
    }
