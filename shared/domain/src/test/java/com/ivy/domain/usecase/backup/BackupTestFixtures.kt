package com.ivy.domain.usecase.backup

import com.ivy.base.time.TimeProvider
import com.ivy.data.backup.local.SnapshotArchive
import com.ivy.data.backup.local.SnapshotNaming
import com.ivy.data.model.backup.SnapshotRef
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A clock the test moves by hand; capture must never reach for the real one. */
internal class FixedTimeProvider(var now: Instant) : TimeProvider {
    override fun getZoneId(): ZoneId = ZoneId.of("UTC")
    override fun utcNow(): Instant = now
    override fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now, getZoneId())
    override fun localDateNow(): LocalDate = localNow().toLocalDate()
    override fun localTimeNow(): LocalTime = localNow().toLocalTime()
}

internal class TestBackupEnvironment(
    override val deviceName: String = "pixel",
    override val appVersion: String = "1.2.3",
    override val dataSchemaVersion: Int = 7,
) : BackupEnvironment

/**
 * Describes a stored object the way the real storage does — by reading the manifest inside it —
 * so a test sees the summary the snapshot actually carries, not one derived from its name.
 */
internal fun describeByManifest(name: String, bytes: ByteArray): SnapshotRef? {
    val parsed = SnapshotNaming.parse(name)
    val manifest = parsed?.let {
        SnapshotArchive.readManifest(name, ByteArrayInputStream(bytes)).getOrNull()
    }
    return manifest?.let {
        SnapshotRef(
            name = name,
            uri = "fake://$name",
            capturedAt = it.capturedAt,
            origin = parsed.origin,
            summary = it.summary,
            sizeBytes = bytes.size.toLong(),
        )
    }
}
