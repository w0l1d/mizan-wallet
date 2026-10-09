package com.ivy.domain.usecase.backup

import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.backup.local.fake.FakeBackupStorage
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class ListSnapshotsUseCaseTest {

    private val storage = FakeBackupStorage()
    private val useCase = ListSnapshotsUseCase(storage)

    @Test
    fun `an empty folder is a success, not a failure`() = runBlocking<Unit> {
        val listing = useCase().shouldBeRight()

        listing.snapshots shouldBe emptyList()
        listing.unreadable shouldBe 0
    }

    @Test
    fun `snapshots come back newest first`() = runBlocking<Unit> {
        val older = name(Instant.parse("2026-01-01T09:00:00Z"))
        val newer = name(Instant.parse("2026-03-01T09:00:00Z"))
        storage.put(older, "x".toByteArray())
        storage.put(newer, "x".toByteArray())

        val listing = useCase().shouldBeRight()

        listing.snapshots.map { it.name } shouldBe listOf(newer, older)
    }

    @Test
    fun `a file that is not a snapshot is counted, never offered for restore`() = runBlocking<Unit> {
        storage.put(name(Instant.parse("2026-01-01T09:00:00Z")), "x".toByteArray())
        storage.put("holiday-photos.zip", "x".toByteArray())
        storage.put("wallet-notes.txt", "x".toByteArray())

        val listing = useCase().shouldBeRight()

        listing.snapshots.size shouldBe 1
        listing.unreadable shouldBe 2
    }

    @Test
    fun `a folder we can no longer reach is reported as such`() = runBlocking<Unit> {
        storage.failNext = BackupError.AccessDenied

        useCase().shouldBeLeft() shouldBe BackupError.AccessDenied
    }

    private fun name(at: Instant) = SnapshotNaming.build(
        capturedAt = at,
        device = "pixel",
        origin = SnapshotOrigin.Scheduled,
    )
}
