package com.ivy.data.backup.local

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import arrow.core.Either
import com.ivy.base.TestDispatchersProvider
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Runs against a **real** folder the user granted, because the behaviours worth testing here —
 * a grant surviving process death, a de-duplicating create, a revoked grant — exist only in SAF.
 *
 * The folder cannot be picked from a test, so pass the tree uri in:
 *
 * ```
 * ./gradlew :shared:data:core:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.backupTreeUri="content://…/tree/…"
 * ```
 *
 * Obtain the uri by picking a folder in the app once and reading it back from the backup
 * destination setting. Without the argument every test here is skipped rather than silently
 * passing against nothing.
 */
@RunWith(AndroidJUnit4::class)
class SafFolderStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var storage: SafFolderStorage
    private lateinit var config: BackupDestinationConfig
    private lateinit var prefsFile: File
    private var treeUri: Uri? = null

    private val capturedAt: Instant = Instant.parse("2026-10-03T04:12:00Z")
    private val manifest = SnapshotManifest(
        capturedAt = capturedAt,
        origin = SnapshotOrigin.Manual,
        appVersion = "test",
        dataSchemaVersion = 1,
        dataEntry = "wallet-data.json",
        dataSha256 = "",
        summary = SnapshotSummary(1, 1, 1, 0, capturedAt),
    )
    private val data = "{}".toByteArray(Charsets.UTF_16)

    @Before
    fun setUp() {
        val argument = InstrumentationRegistry.getArguments().getString("backupTreeUri")
        assumeTrue("no backupTreeUri instrumentation argument", !argument.isNullOrBlank())
        treeUri = Uri.parse(argument)

        prefsFile = File(context.cacheDir, "saf-test-${System.nanoTime()}.preferences_pb")
        config = BackupDestinationConfig(PreferenceDataStoreFactory.create { prefsFile })
        storage = SafFolderStorage(context, config, TestDispatchersProvider)

        runBlocking { storage.remember(treeUri!!, capturedAt).shouldBeRight() }
    }

    @After
    fun tearDown() = runBlocking {
        if (::storage.isInitialized) {
            storage.list().getOrNull()?.snapshots
                ?.filter { it.name.contains("--$TEST_DEVICE--") } // never touch the user's own snapshots
                ?.forEach { storage.delete(it) }
        }
        if (::prefsFile.isInitialized) prefsFile.delete()
        Unit
    }

    @Test
    fun writes_lists_reads_and_deletes_a_snapshot() = runBlocking {
        val name = snapshotName(capturedAt)

        val ref = storage.write(name) { SnapshotArchive.write(it, manifest, data) }
            .shouldBeRight()

        ref.name shouldBe name
        ref.capturedAt shouldBe capturedAt
        ref.summary.transactionCount shouldBe 1

        storage.list().shouldBeRight().snapshots.map { it.name } shouldBe listOf(name)

        val read = storage.read(ref) { input ->
            SnapshotArchive.readManifest(name, input).shouldBeRight()
        }.shouldBeRight()
        read.summary shouldBe manifest.summary

        storage.delete(ref).shouldBeRight()
        storage.list().shouldBeRight().snapshots shouldBe emptyList()
    }

    @Test
    fun writing_a_name_that_is_taken_fails_instead_of_creating_a_duplicate() = runBlocking {
        val name = snapshotName(capturedAt)
        storage.write(name) { SnapshotArchive.write(it, manifest, data) }.shouldBeRight()

        val second = storage.write(name) { SnapshotArchive.write(it, manifest, data) }

        second.shouldBeLeft().shouldBeInstanceOf<BackupError.WriteFailed>()
        storage.list().shouldBeRight().snapshots.map { it.name } shouldBe listOf(name)
    }

    @Test
    fun an_object_that_is_not_a_snapshot_is_counted_unreadable_and_never_offered() = runBlocking {
        val name = snapshotName(capturedAt)
        storage.write(name) { SnapshotArchive.write(it, manifest, data) }.shouldBeRight()

        val folder = DocumentFile.fromTreeUri(context, treeUri!!)!!
        val stray = folder.createFile("text/plain", "not-a-snapshot.txt")!!
        context.contentResolver.openOutputStream(stray.uri)!!.use { it.write(byteArrayOf(1, 2)) }

        try {
            val listing = storage.list().shouldBeRight()
            listing.snapshots.map { it.name } shouldBe listOf(name)
            listing.unreadable shouldBe 1
        } finally {
            stray.delete()
        }
    }

    @Test
    fun a_snapshot_deleted_behind_our_back_reads_as_unreadable_not_as_a_crash() = runBlocking {
        val name = snapshotName(capturedAt)
        val ref = storage.write(name) { SnapshotArchive.write(it, manifest, data) }
            .shouldBeRight()

        DocumentFile.fromSingleUri(context, Uri.parse(ref.uri))!!.delete()

        storage.read(ref) { it.readBytes() }
            .shouldBeLeft().shouldBeInstanceOf<BackupError.SnapshotUnreadable>()
    }

    @Test
    fun deleting_a_snapshot_that_is_already_gone_succeeds() = runBlocking {
        val name = snapshotName(capturedAt)
        val ref = storage.write(name) { SnapshotArchive.write(it, manifest, data) }
            .shouldBeRight()
        storage.delete(ref).shouldBeRight()

        storage.delete(ref).shouldBeRight()
    }

    @Test
    fun the_grant_is_persisted_so_it_survives_process_death() = runBlocking {
        val held = context.contentResolver.persistedUriPermissions
            .any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }

        held shouldBe true
    }

    @Test
    fun a_folder_we_hold_no_grant_for_surfaces_as_access_denied() = runBlocking {
        config.set(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ANoSuchFolder",
            configuredAt = capturedAt,
        )

        storage.list().shouldBeLeft() shouldBe BackupError.AccessDenied
    }

    @Test
    fun no_destination_configured_is_its_own_error_not_a_failure() = runBlocking {
        config.clear()

        storage.list().shouldBeLeft() shouldBe BackupError.NotConfigured
    }

    private fun snapshotName(at: Instant): String =
        SnapshotNaming.build(at, TEST_DEVICE, SnapshotOrigin.Manual)

    private companion object {
        const val TEST_DEVICE = "androidtest"
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
