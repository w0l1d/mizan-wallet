package com.ivy.data.backup.local

import com.ivy.data.model.backup.SnapshotOrigin
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The portable name of a snapshot object:
 *
 * ```
 * wallet-<yyyyMMdd>T<HHmmss>Z[-<nn>]--<device>--<origin>.zip
 * ```
 *
 * The timestamp is UTC so names sort chronologically as plain strings regardless of where the
 * device was. The character set is `A-Z a-z 0-9 - .` only — FAT32 and exFAT reject colons, and
 * removable media reached through the Storage Access Framework is in scope.
 *
 * `capturedAt` recovered from a name is a sorting aid only; the manifest is the authority.
 */
object SnapshotNaming {

    private const val PREFIX = "wallet-"
    private const val EXTENSION = ".zip"
    private const val SEPARATOR = "--"
    private const val MAX_SEQUENCE = 99

    private const val GROUP_TIMESTAMP = 1
    private const val GROUP_DEVICE = 3
    private const val GROUP_ORIGIN = 4

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private val PATTERN = Regex(
        """^wallet-(\d{8}T\d{6}Z)(?:-(\d{2}))?--([A-Za-z0-9]+)--(sched|manual|safety)\.zip$"""
    )

    data class Parsed(
        val capturedAt: Instant,
        val device: String,
        val origin: SnapshotOrigin,
    )

    fun build(capturedAt: Instant, device: String, origin: SnapshotOrigin): String =
        build(capturedAt, device, origin, sequence = 0)

    /**
     * A name that is not already present in [taken]. Two captures in the same second are
     * disambiguated here rather than by relying on the storage to reject a collision — SAF's
     * create silently renames a colliding file instead of failing.
     */
    fun buildUnique(
        capturedAt: Instant,
        device: String,
        origin: SnapshotOrigin,
        taken: Set<String>,
    ): String {
        var instant = capturedAt
        while (true) {
            for (sequence in 0..MAX_SEQUENCE) {
                val candidate = build(instant, device, origin, sequence)
                if (candidate !in taken) return candidate
            }
            instant = instant.plusSeconds(1)
        }
    }

    fun parse(name: String): Parsed? {
        val groups = PATTERN.matchEntire(name)?.groupValues ?: return null
        val capturedAt = runCatching {
            Instant.from(TIMESTAMP.parse(groups[GROUP_TIMESTAMP]))
        }.getOrNull()
        val origin = originOf(groups[GROUP_ORIGIN])

        return if (capturedAt == null || origin == null) {
            null
        } else {
            Parsed(capturedAt = capturedAt, device = groups[GROUP_DEVICE], origin = origin)
        }
    }

    fun codeOf(origin: SnapshotOrigin): String = when (origin) {
        SnapshotOrigin.Scheduled -> "sched"
        SnapshotOrigin.Manual -> "manual"
        SnapshotOrigin.Safety -> "safety"
    }

    private fun originOf(code: String): SnapshotOrigin? = when (code) {
        "sched" -> SnapshotOrigin.Scheduled
        "manual" -> SnapshotOrigin.Manual
        "safety" -> SnapshotOrigin.Safety
        else -> null
    }

    private fun build(
        capturedAt: Instant,
        device: String,
        origin: SnapshotOrigin,
        sequence: Int,
    ): String {
        // A sequence suffix sorts after the unsuffixed name because '-' precedes every digit.
        val suffix = if (sequence == 0) "" else "-%02d".format(sequence)
        return PREFIX + TIMESTAMP.format(capturedAt) + suffix +
            SEPARATOR + sanitize(device) + SEPARATOR + codeOf(origin) + EXTENSION
    }

    private fun sanitize(device: String): String =
        device.filter { it.isLetterOrDigit() }.ifEmpty { "unknown" }
}
