package com.ivy.data.backup.local

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The metadata entry of a snapshot archive, stored as `manifest.json`.
 *
 * It is small on purpose: it is read once per snapshot on every listing, and the embedded
 * [summary] is what lets listing and comparison avoid opening the data document at all.
 */
data class SnapshotManifest(
    val capturedAt: Instant,
    val origin: SnapshotOrigin,
    val appVersion: String,
    val dataSchemaVersion: Int,
    val dataEntry: String,
    val dataSha256: String,
    val summary: SnapshotSummary,
) {
    companion object {
        const val ENTRY_NAME = "manifest.json"

        /**
         * Bumped only for a change that genuinely breaks compatibility. Adding a field does not
         * qualify — unknown fields are ignored on read.
         */
        const val FORMAT_VERSION = 1
    }
}

/** Reads and writes [SnapshotManifest] in the form fixed by `contracts/snapshot-format.md`. */
object SnapshotManifestCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(manifest: SnapshotManifest): String = json.encodeToString(
        ManifestJson.serializer(),
        ManifestJson(
            formatVersion = SnapshotManifest.FORMAT_VERSION,
            capturedAt = manifest.capturedAt.toString(),
            origin = SnapshotNaming.codeOf(manifest.origin),
            appVersion = manifest.appVersion,
            dataSchemaVersion = manifest.dataSchemaVersion,
            dataEntry = manifest.dataEntry,
            dataSha256 = manifest.dataSha256,
            summary = SummaryJson(
                transactionCount = manifest.summary.transactionCount,
                accountCount = manifest.summary.accountCount,
                categoryCount = manifest.summary.categoryCount,
                budgetCount = manifest.summary.budgetCount,
                newestTransactionAt = manifest.summary.newestTransactionAt?.toString(),
            ),
        ),
    )

    /**
     * [name] is the snapshot's storage name, carried only so a failure can say which object
     * could not be read.
     */
    fun decode(name: String, raw: String): Either<BackupError.SnapshotUnreadable, SnapshotManifest> {
        val parsed = runCatching { json.decodeFromString(ManifestJson.serializer(), raw) }
            .getOrElse { return unreadable(name, "manifest.json could not be parsed: ${it.message}") }

        if (parsed.formatVersion != SnapshotManifest.FORMAT_VERSION) {
            return unreadable(
                name,
                "snapshot format version ${parsed.formatVersion} is not recognised by this version " +
                    "of the app (it reads version ${SnapshotManifest.FORMAT_VERSION})",
            )
        }

        val origin = ORIGINS[parsed.origin]
            ?: return unreadable(name, "unrecognised snapshot origin '${parsed.origin}'")

        val capturedAt = parsed.capturedAt.toInstantOrNull()
            ?: return unreadable(name, "capturedAt '${parsed.capturedAt}' is not a valid instant")

        val newestTransactionAt = parsed.summary.newestTransactionAt?.let {
            it.toInstantOrNull() ?: return unreadable(name, "newestTransactionAt '$it' is not a valid instant")
        }

        val summary = runCatching {
            SnapshotSummary(
                transactionCount = parsed.summary.transactionCount,
                accountCount = parsed.summary.accountCount,
                categoryCount = parsed.summary.categoryCount,
                budgetCount = parsed.summary.budgetCount,
                newestTransactionAt = newestTransactionAt,
            )
        }.getOrElse { return unreadable(name, "summary is not valid: ${it.message}") }

        return SnapshotManifest(
            capturedAt = capturedAt,
            origin = origin,
            appVersion = parsed.appVersion,
            dataSchemaVersion = parsed.dataSchemaVersion,
            dataEntry = parsed.dataEntry,
            dataSha256 = parsed.dataSha256,
            summary = summary,
        ).right()
    }

    private val ORIGINS = mapOf(
        "sched" to SnapshotOrigin.Scheduled,
        "manual" to SnapshotOrigin.Manual,
        "safety" to SnapshotOrigin.Safety,
    )

    private fun unreadable(name: String, reason: String) =
        BackupError.SnapshotUnreadable(name, reason).left()

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    @Serializable
    private data class ManifestJson(
        @SerialName("formatVersion") val formatVersion: Int,
        @SerialName("capturedAt") val capturedAt: String,
        @SerialName("origin") val origin: String,
        @SerialName("appVersion") val appVersion: String = "",
        @SerialName("dataSchemaVersion") val dataSchemaVersion: Int = 0,
        @SerialName("dataEntry") val dataEntry: String,
        @SerialName("dataSha256") val dataSha256: String = "",
        @SerialName("summary") val summary: SummaryJson = SummaryJson(),
    )

    @Serializable
    private data class SummaryJson(
        @SerialName("transactionCount") val transactionCount: Int = 0,
        @SerialName("accountCount") val accountCount: Int = 0,
        @SerialName("categoryCount") val categoryCount: Int = 0,
        @SerialName("budgetCount") val budgetCount: Int = 0,
        @SerialName("newestTransactionAt") val newestTransactionAt: String? = null,
    )
}
