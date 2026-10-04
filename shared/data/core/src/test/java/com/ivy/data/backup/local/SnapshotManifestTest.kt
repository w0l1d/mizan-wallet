package com.ivy.data.backup.local

import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.time.Instant

class SnapshotManifestTest {

    private val manifest = SnapshotManifest(
        capturedAt = Instant.parse("2026-10-03T04:12:00Z"),
        origin = SnapshotOrigin.Scheduled,
        appVersion = "2025.07.17",
        dataSchemaVersion = 0,
        dataEntry = "ivy-wallet-backup.json",
        dataSha256 = "abc123",
        summary = SnapshotSummary(
            transactionCount = 3,
            accountCount = 2,
            categoryCount = 1,
            budgetCount = 0,
            newestTransactionAt = Instant.parse("2026-10-02T10:00:00Z"),
        ),
    )

    @Test
    fun `round-trips`() {
        val json = SnapshotManifestCodec.encode(manifest)

        SnapshotManifestCodec.decode("any.zip", json).getOrNull() shouldBe manifest
    }

    @Test
    fun `round-trips an empty wallet with a null newestTransactionAt`() {
        val empty = manifest.copy(summary = SnapshotSummary.Empty)

        val decoded = SnapshotManifestCodec.decode("any.zip", SnapshotManifestCodec.encode(empty)).getOrNull()

        decoded shouldBe empty
        decoded?.summary?.newestTransactionAt shouldBe null
    }

    @Test
    fun `writes the documented formatVersion`() {
        SnapshotManifestCodec.encode(manifest) shouldContain "\"formatVersion\": 1"
    }

    @Test
    fun `ignores unknown fields`() {
        val withExtra = SnapshotManifestCodec.encode(manifest)
            .replaceFirst("{", """{"somethingFromTheFuture": {"nested": [1, 2]},""")

        SnapshotManifestCodec.decode("any.zip", withExtra).getOrNull() shouldBe manifest
    }

    @Test
    fun `rejects an unrecognised formatVersion as unreadable rather than guessing`() {
        val future = SnapshotManifestCodec.encode(manifest)
            .replaceFirst("\"formatVersion\": 1", "\"formatVersion\": 2")

        val error = SnapshotManifestCodec.decode("future.zip", future).leftOrNull()

        error.shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
        error.name shouldBe "future.zip"
        error.reason shouldContain "2"
    }

    @Test
    fun `rejects malformed json as unreadable`() {
        val error = SnapshotManifestCodec.decode("broken.zip", "not json at all").leftOrNull()

        error.shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
        error.name shouldBe "broken.zip"
    }

    @Test
    fun `rejects an unrecognised origin as unreadable`() {
        val odd = SnapshotManifestCodec.encode(manifest)
            .replaceFirst("\"sched\"", "\"telepathy\"")

        SnapshotManifestCodec.decode("odd.zip", odd).leftOrNull()
            .shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
    }
}
