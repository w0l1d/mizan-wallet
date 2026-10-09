package com.ivy.domain.usecase.backup

import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.backup.local.fake.FakeBackupStorage
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class ApplyRetentionUseCaseTest {

    private val storage = FakeBackupStorage()
    private val useCase = ApplyRetentionUseCase(storage)

    @Test
    fun `keep and delete together are exactly the input, nothing invented or dropped`() =
        runBlocking<Unit> {
            val listing = scheduled(count = ApplyRetentionUseCase.KEEP_COUNT + 5)
            listing.forEach { storage.put(it.name, "x".toByteArray()) }

            val decision = useCase(justWritten = listing.first(), listing = listing)
                .shouldBeRight()

            (decision.keep + decision.delete).map { it.name }
                .sorted() shouldBe listing.map { it.name }.sorted()
        }

    @Test
    fun `the oldest snapshots beyond the floor are the ones that go`() = runBlocking<Unit> {
        val listing = scheduled(count = ApplyRetentionUseCase.KEEP_COUNT + 3)
        listing.forEach { storage.put(it.name, "x".toByteArray()) }

        val decision = useCase(justWritten = listing.first(), listing = listing).shouldBeRight()

        decision.keep.size shouldBe ApplyRetentionUseCase.KEEP_COUNT
        decision.delete.map { it.name } shouldContainExactly
            listing.takeLast(3).map { it.name }
        storage.names.sorted() shouldBe decision.keep.map { it.name }.sorted()
    }

    @Test
    fun `a wallet with few snapshots loses none of them`() = runBlocking<Unit> {
        val listing = scheduled(count = 3)
        listing.forEach { storage.put(it.name, "x".toByteArray()) }

        val decision = useCase(justWritten = listing.first(), listing = listing).shouldBeRight()

        decision.delete shouldBe emptyList()
        decision.keep.shouldNotBeEmpty()
        storage.names.size shouldBe 3
    }

    @Test
    fun `a safety snapshot is never deleted, however old it gets`() = runBlocking<Unit> {
        val safety = ref(
            at = Instant.parse("2020-01-01T00:00:00Z"),
            origin = SnapshotOrigin.Safety,
        )
        val listing = scheduled(count = ApplyRetentionUseCase.KEEP_COUNT + 5) + safety
        listing.forEach { storage.put(it.name, "x".toByteArray()) }

        val decision = useCase(justWritten = listing.first(), listing = listing).shouldBeRight()

        decision.delete.none { it.origin == SnapshotOrigin.Safety } shouldBe true
        decision.keep.any { it.name == safety.name } shouldBe true
        storage.names.contains(safety.name) shouldBe true
    }

    @Test
    fun `nothing is deleted when the newer snapshot is not actually in the folder`() =
        runBlocking<Unit> {
            val listing = scheduled(count = ApplyRetentionUseCase.KEEP_COUNT + 5)
            listing.forEach { storage.put(it.name, "x".toByteArray()) }
            // The write reported a name the listing does not contain: the new copy is not there,
            // so the old ones are all we have.
            val phantom = ref(at = Instant.parse("2030-01-01T00:00:00Z"))

            val decision = useCase(justWritten = phantom, listing = listing).shouldBeRight()

            decision.delete shouldBe emptyList()
            decision.keep.map { it.name } shouldBe listing.map { it.name }
            storage.names.size shouldBe listing.size
        }

    @Test
    fun `a snapshot we failed to delete is reported as kept, because it is still there`() =
        runBlocking<Unit> {
            val listing = scheduled(count = ApplyRetentionUseCase.KEEP_COUNT + 1)
            listing.forEach { storage.put(it.name, "x".toByteArray()) }
            storage.failNext = BackupError.AccessDenied

            val decision = useCase(justWritten = listing.first(), listing = listing)
                .shouldBeRight()

            decision.delete shouldBe emptyList()
            decision.keep.size shouldBe listing.size
            storage.names.size shouldBe listing.size
        }

    private fun scheduled(count: Int): List<SnapshotRef> = (0 until count).map { index ->
        // Newest first, one day apart.
        ref(at = Instant.parse("2026-01-01T00:00:00Z").minusSeconds(index * 86_400L))
    }

    private fun ref(
        at: Instant,
        origin: SnapshotOrigin = SnapshotOrigin.Scheduled,
    ): SnapshotRef {
        val name = SnapshotNaming.build(capturedAt = at, device = "pixel", origin = origin)
        return SnapshotRef(
            name = name,
            uri = "fake://$name",
            capturedAt = at,
            origin = origin,
            summary = SnapshotSummary.Empty,
            sizeBytes = 1L,
        )
    }
}
