package com.ivy.data.backup.local

import arrow.core.Either
import com.ivy.data.backup.local.fake.FakeBackupStorage
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class FakeBackupStorageTest {

    private val capturedAt: Instant = Instant.parse("2026-10-03T04:12:00Z")
    private val name = SnapshotNaming.build(capturedAt, "a3f2", SnapshotOrigin.Scheduled)

    @Test
    fun `an empty destination lists successfully`() = runBlocking<Unit> {
        val listing = FakeBackupStorage().list().shouldBeRight()

        listing.snapshots shouldBe emptyList()
        listing.unreadable shouldBe 0
    }

    @Test
    fun `a written snapshot comes back in the listing`() = runBlocking<Unit> {
        val storage = FakeBackupStorage()

        val ref = storage.write(name) { it.write("payload".toByteArray()) }.shouldBeRight()

        ref.name shouldBe name
        ref.capturedAt shouldBe capturedAt
        ref.origin shouldBe SnapshotOrigin.Scheduled
        ref.sizeBytes shouldBe 7L
        storage.list().shouldBeRight().snapshots shouldBe listOf(ref)
    }

    @Test
    fun `listings are newest first`() = runBlocking<Unit> {
        val storage = FakeBackupStorage()
        val older = SnapshotNaming.build(capturedAt, "a3f2", SnapshotOrigin.Scheduled)
        val newer = SnapshotNaming.build(capturedAt.plusSeconds(60), "a3f2", SnapshotOrigin.Manual)
        storage.write(older) {}
        storage.write(newer) {}

        storage.list().shouldBeRight().snapshots.map { it.name } shouldBe listOf(newer, older)
    }

    @Test
    fun `an object that is not a snapshot is counted unreadable and never offered`() =
        runBlocking<Unit> {
            val storage = FakeBackupStorage()
            storage.write(name) {}
            storage.put("holiday-photo.jpg", ByteArray(4))

            val listing = storage.list().shouldBeRight()

            listing.snapshots.map { it.name } shouldBe listOf(name)
            listing.unreadable shouldBe 1
        }

    @Test
    fun `writing a name that is taken fails and leaves no second object`() = runBlocking<Unit> {
        val storage = FakeBackupStorage()
        storage.write(name) { it.write("first".toByteArray()) }.shouldBeRight()

        val second = storage.write(name) { it.write("second".toByteArray()) }

        second.shouldBeLeft().shouldBeInstanceOf<BackupError.WriteFailed>()
        storage.names shouldBe listOf(name)
    }

    @Test
    fun `read returns the bytes that were written`() = runBlocking<Unit> {
        val storage = FakeBackupStorage()
        val ref = storage.write(name) { it.write("payload".toByteArray()) }.shouldBeRight()

        val read = storage.read(ref) { String(it.readBytes()) }.shouldBeRight()

        read shouldBe "payload"
    }

    @Test
    fun `a snapshot that vanished between list and read is unreadable, not a crash`() =
        runBlocking<Unit> {
            val storage = FakeBackupStorage()
            val ref = storage.write(name) { it.write("payload".toByteArray()) }.shouldBeRight()
            storage.vanish(name)

            storage.read(ref) { it.readBytes() }
                .shouldBeLeft().shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
        }

    @Test
    fun `a truncated snapshot still reads, so the caller must validate its contents`() =
        runBlocking<Unit> {
                val storage = FakeBackupStorage()
                val ref = storage.write(name) { it.write("payload!".toByteArray()) }.shouldBeRight()
                storage.truncate(name)

                storage.read(ref) { String(it.readBytes()) }.shouldBeRight() shouldBe "payl"
            }

        @Test
        fun `deleting a snapshot that is already gone succeeds`() = runBlocking<Unit> {
            val storage = FakeBackupStorage()
            val ref = storage.write(name) {}.shouldBeRight()
            storage.delete(ref).shouldBeRight()

            storage.delete(ref).shouldBeRight()

            storage.names shouldBe emptyList()
        }

    @Test
    fun `a forced failure surfaces as a typed error and is not sticky`() = runBlocking<Unit> {
        val storage = FakeBackupStorage()
        storage.failNext = BackupError.AccessDenied

        storage.list().shouldBeLeft() shouldBe BackupError.AccessDenied
        storage.list().shouldBeRight().snapshots shouldBe emptyList()
    }
}

private fun <A, B> Either<A, B>.shouldBeRight(): B = when (this) {
    is Either.Right -> value
    is Either.Left -> error("expected Right, was Left($value)")
}

private fun <A, B> Either<A, B>.shouldBeLeft(): A = when (this) {
    is Either.Left -> value
    is Either.Right -> error("expected Left, was Right($value)")
}
