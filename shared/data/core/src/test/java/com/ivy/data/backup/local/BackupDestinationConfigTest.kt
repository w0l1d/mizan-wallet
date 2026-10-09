package com.ivy.data.backup.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.ivy.data.datastore.IvyDataStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class BackupDestinationConfigTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AWallet"
    private val configuredAt = Instant.parse("2026-10-03T04:12:00Z")

    private val dataStore: IvyDataStore by lazy {
        PreferenceDataStoreFactory.create { folder.newFile("backup.preferences_pb") }
    }

    private fun config(): BackupDestinationConfig = BackupDestinationConfig(dataStore)

    @Test
    fun `no destination is configured to begin with`() = runBlocking<Unit> {
        config().destination.first() shouldBe null
    }

    @Test
    fun `a chosen destination is remembered`() = runBlocking<Unit> {
        val config = config()

        config.set(treeUri = treeUri, configuredAt = configuredAt)

        config.destination.first() shouldBe BackupDestination(treeUri, configuredAt)
    }

    @Test
    fun `choosing a second destination replaces the first`() = runBlocking<Unit> {
        val config = config()
        config.set(treeUri = treeUri, configuredAt = configuredAt)

        val later = configuredAt.plusSeconds(3600)
        config.set(treeUri = "content://other/tree", configuredAt = later)

        config.destination.first() shouldBe BackupDestination("content://other/tree", later)
    }

    @Test
    fun `clearing forgets the destination`() = runBlocking<Unit> {
        val config = config()
        config.set(treeUri = treeUri, configuredAt = configuredAt)

        config.clear()

        config.destination.first() shouldBe null
    }

    @Test
    fun `a half-written destination reads as not configured rather than as a broken one`() =
        runBlocking<Unit> {
            val config = config()
            config.set(treeUri = treeUri, configuredAt = configuredAt)

            dataStore.edit { it.remove(BackupDestinationConfig.CONFIGURED_AT_EPOCH_SEC) }

            config.destination.first() shouldBe null
        }
}
