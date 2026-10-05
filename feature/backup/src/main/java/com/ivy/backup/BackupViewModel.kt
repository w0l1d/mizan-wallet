package com.ivy.backup

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.time.TimeProvider
import com.ivy.data.backup.local.BackupDestinationConfig
import com.ivy.data.backup.local.SafFolderStorage
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.domain.usecase.backup.BackupStatus
import com.ivy.domain.usecase.backup.BackupStatusUseCase
import com.ivy.domain.usecase.backup.CaptureSnapshotUseCase
import com.ivy.domain.usecase.backup.CompareSnapshotUseCase
import com.ivy.domain.usecase.backup.ListSnapshotsUseCase
import com.ivy.domain.usecase.backup.RestoreSnapshotUseCase
import com.ivy.data.model.backup.SnapshotComparison
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val destinationConfig: BackupDestinationConfig,
    private val safFolderStorage: SafFolderStorage,
    private val listSnapshots: ListSnapshotsUseCase,
    private val captureSnapshot: CaptureSnapshotUseCase,
    private val compareSnapshot: CompareSnapshotUseCase,
    private val restoreSnapshot: RestoreSnapshotUseCase,
    private val backupStatusUseCase: BackupStatusUseCase,
    private val backupScheduler: BackupScheduler,
    private val timeProvider: TimeProvider,
) : ComposeViewModel<BackupViewState, BackupEvent>() {

    private var state by mutableStateOf<BackupViewState>(BackupViewState.Loading)
    private var comparison by mutableStateOf<SnapshotComparison?>(null)
    private var restoreCandidate by mutableStateOf<SnapshotRef?>(null)

    @Composable
    override fun uiState(): BackupViewState {
        LaunchedEffect(Unit) { load() }
        return state
    }

    override fun onEvent(event: BackupEvent) {
        when (event) {
            is BackupEvent.FolderPicked -> pickFolder(event.treeUri)
            BackupEvent.BackupNow -> backupNow()
            is BackupEvent.SnapshotSelected -> selectSnapshot(event.ref)
            is BackupEvent.RestoreConfirmed -> confirmRestore(event.ref)
            BackupEvent.RestoreDismissed -> dismissRestore()
            BackupEvent.Refresh -> refresh()
        }
    }

    fun currentComparison(): SnapshotComparison? = comparison
    fun currentRestoreCandidate(): SnapshotRef? = restoreCandidate

    private fun pickFolder(treeUri: String) {
        viewModelScope.launch {
            state = BackupViewState.Loading
            safFolderStorage.remember(Uri.parse(treeUri), timeProvider.utcNow()).fold(
                ifLeft = { state = BackupViewState.Failed(it.userMessage()) },
                ifRight = {
                    backupScheduler.schedule()
                    load()
                },
            )
        }
    }

    private fun backupNow() {
        viewModelScope.launch {
            state = BackupViewState.Loading
            captureSnapshot(SnapshotOrigin.Manual).fold(
                ifLeft = { state = BackupViewState.Failed(it.userMessage()) },
                ifRight = { load() },
            )
        }
    }

    private fun selectSnapshot(ref: SnapshotRef) {
        viewModelScope.launch {
            restoreCandidate = ref
            comparison = compareSnapshot(ref)
        }
    }

    private fun confirmRestore(ref: SnapshotRef) {
        viewModelScope.launch {
            state = BackupViewState.Loading
            restoreCandidate = null
            comparison = null
            restoreSnapshot(ref).fold(
                ifLeft = { state = BackupViewState.Failed(it.userMessage()) },
                ifRight = { load() },
            )
        }
    }

    private fun dismissRestore() {
        restoreCandidate = null
        comparison = null
    }

    private fun refresh() {
        viewModelScope.launch { load() }
    }

    @Suppress("LongMethod")
    private suspend fun load() {
        val destination = destinationConfig.destination.first()
        if (destination == null) {
            state = BackupViewState.NotConfigured
            return
        }
        listSnapshots().fold(
            ifLeft = { state = BackupViewState.Failed(it.userMessage()) },
            ifRight = { listing ->
                val status = backupStatusUseCase()
                val (statusMessage, isWarning) = statusText(status)
                state = BackupViewState.Ready(
                    folderName = destination.treeUri.substringAfterLast('/'),
                    snapshots = listing.snapshots.toImmutableList(),
                    unreadableCount = listing.unreadable,
                    statusMessage = statusMessage,
                    statusIsWarning = isWarning,
                )
            },
        )
    }

    private fun statusText(status: BackupStatus): Pair<String?, Boolean> = when (status) {
        is BackupStatus.Healthy -> formatAge(status.lastSnapshotAt) to false
        is BackupStatus.Failing -> (status.lastAttemptError
            ?: "Last backup attempt failed.") to true
        BackupStatus.NeverBackedUp -> "No backup yet." to true
        BackupStatus.Unreachable -> "Folder unreachable." to true
        BackupStatus.NotConfigured -> null to false
    }

    @Suppress("MagicNumber")
    private fun formatAge(snapshotAt: java.time.Instant): String {
        val age = java.time.Duration.between(snapshotAt, timeProvider.utcNow())
        val hours = age.toHours()
        return when {
            hours < 1 -> "Last backup: less than an hour ago"
            hours < 24 -> "Last backup: ${hours}h ago"
            else -> "Last backup: ${hours / 24}d ago"
        }
    }
}
