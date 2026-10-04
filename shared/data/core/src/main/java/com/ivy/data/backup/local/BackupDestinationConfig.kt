package com.ivy.data.backup.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivy.data.datastore.IvyDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The folder the user chose, and when they chose it. */
data class BackupDestination(
    val treeUri: String,
    val configuredAt: Instant,
)

/**
 * Remembers which folder backups go to.
 *
 * Only the user's choice is stored. Whether the folder is still reachable — the grant still held,
 * the SD card still inserted, the folder not deleted from a file manager — is **not** stored,
 * because a remembered "reachable" is a claim that goes stale silently. Reachability is discovered
 * by using the folder and reported from that attempt.
 */
@Singleton
class BackupDestinationConfig @Inject constructor(
    private val dataStore: IvyDataStore,
) {
    val destination: Flow<BackupDestination?> = dataStore.data.map { preferences ->
        val treeUri = preferences[TREE_URI]
        val configuredAt = preferences[CONFIGURED_AT_EPOCH_SEC]

        // Half a destination is no destination: asking the user again beats guessing a date.
        if (treeUri == null || configuredAt == null) {
            null
        } else {
            BackupDestination(treeUri, Instant.ofEpochSecond(configuredAt))
        }
    }

    suspend fun set(treeUri: String, configuredAt: Instant) {
        dataStore.edit { preferences ->
            preferences[TREE_URI] = treeUri
            preferences[CONFIGURED_AT_EPOCH_SEC] = configuredAt.epochSecond
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(TREE_URI)
            preferences.remove(CONFIGURED_AT_EPOCH_SEC)
        }
    }

    companion object {
        val TREE_URI = stringPreferencesKey("backup_destination_tree_uri")
        val CONFIGURED_AT_EPOCH_SEC = longPreferencesKey("backup_destination_configured_at_sec")
    }
}
