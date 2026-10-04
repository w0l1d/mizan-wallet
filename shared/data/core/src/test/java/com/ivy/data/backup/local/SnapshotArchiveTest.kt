package com.ivy.data.backup.local

import arrow.core.Either
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipOutputStream

class SnapshotArchiveTest {

    private val manifest = SnapshotManifest(
        capturedAt = Instant.parse("2026-10-03T04:12:00Z"),
        origin = SnapshotOrigin.Scheduled,
        appVersion = "1.2.3",
        dataSchemaVersion = 7,
        dataEntry = "wallet-data.json",
        dataSha256 = "",
        summary = SnapshotSummary(
            transactionCount = 12,
            accountCount = 3,
            categoryCount = 4,
            budgetCount = 1,
            newestTransactionAt = Instant.parse("2026-10-02T19:00:00Z"),
        ),
    )

    private val data = "{\"transactions\":[]}".toByteArray(Charsets.UTF_16)

    private fun archive(): ByteArray = ByteArrayOutputStream().also {
        SnapshotArchive.write(it, manifest, data)
    }.toByteArray()

    @Test
    fun `an archive carries the manifest and the data entry under its declared name`() {
        val names = entryNames(archive())

        names shouldBe listOf(SnapshotManifest.ENTRY_NAME, "wallet-data.json")
    }

    @Test
    fun `the manifest round-trips, digest included`() {
        val bytes = archive()

        val read = SnapshotArchive.readManifest("snap.zip", ByteArrayInputStream(bytes))
            .shouldBeRight()

        read shouldBe manifest.copy(dataSha256 = read.dataSha256)
        read.dataSha256 shouldBe SnapshotArchive.sha256(data)
        read.dataSha256.length shouldBe 64
    }

    @Test
    fun `the data entry round-trips byte for byte`() {
        val bytes = archive()
        val read = SnapshotArchive.readManifest("snap.zip", ByteArrayInputStream(bytes))
            .shouldBeRight()

        val entry = SnapshotArchive.readDataEntry("snap.zip", ByteArrayInputStream(bytes), read)
            .shouldBeRight()

        entry.toList() shouldBe data.toList()
        String(entry, Charsets.UTF_16) shouldBe "{\"transactions\":[]}"
    }

    @Test
    fun `an archive with no manifest is unreadable rather than a crash`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("wallet-data.json"))
                zip.write(data)
                zip.closeEntry()
            }
        }.toByteArray()

        SnapshotArchive.readManifest("snap.zip", ByteArrayInputStream(bytes))
            .shouldBeLeft().shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
    }

    @Test
    fun `a truncated archive is unreadable rather than a crash`() {
        val bytes = archive().let { it.copyOf(it.size / 2) }

        SnapshotArchive.readManifest("snap.zip", ByteArrayInputStream(bytes))
            .shouldBeLeft().shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
    }

    @Test
    fun `a manifest naming a data entry the archive does not contain is unreadable`() {
        val bytes = archive()
        val read = SnapshotArchive.readManifest("snap.zip", ByteArrayInputStream(bytes))
            .shouldBeRight()

        SnapshotArchive.readDataEntry(
            name = "snap.zip",
            input = ByteArrayInputStream(bytes),
            manifest = read.copy(dataEntry = "absent.json"),
        ).shouldBeLeft().shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
    }

    @Test
    fun `an entry that escapes the archive root is refused`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("../evil.json"))
                zip.write(data)
                zip.closeEntry()
            }
        }.toByteArray()

        SnapshotArchive.readManifest("snap.zip", ByteArrayInputStream(bytes))
            .shouldBeLeft().shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
    }

    private fun entryNames(bytes: ByteArray): List<String> = buildList {
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                add(entry.name)
                entry = zip.nextEntry
            }
        }
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
