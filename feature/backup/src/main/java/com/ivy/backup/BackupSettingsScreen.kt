package com.ivy.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupSettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: BackupViewModel = screenScopedViewModel()
    val state = viewModel.uiState()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.onEvent(BackupEvent.FolderPicked(uri.toString()))
            }
        }
    }

    val nav = navigation()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Backup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { nav.back() }) {
                Text("Close")
            }
        }

        Spacer(Modifier.height(16.dp))

        when (state) {
            BackupViewState.NotConfigured -> NotConfiguredContent(
                pickFolder = {
                    folderPicker.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    )
                },
            )

            BackupViewState.Loading -> LoadingContent()

            is BackupViewState.Ready -> ReadyContent(
                state = state,
                onBackUpNow = { viewModel.onEvent(BackupEvent.BackupNow) },
                onSelectSnapshot = { viewModel.onEvent(BackupEvent.SnapshotSelected(it)) },
                pickFolder = {
                    folderPicker.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    )
                },
            )

            is BackupViewState.Failed -> FailedContent(
                message = state.message,
                onRetry = { viewModel.onEvent(BackupEvent.Refresh) },
                pickFolder = {
                    folderPicker.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    )
                },
            )
        }

        // Restore confirmation
        val candidate = viewModel.currentRestoreCandidate()
        val comparison = viewModel.currentComparison()
        if (candidate != null && comparison != null) {
            RestoreConfirmDialog(
                candidate = candidate,
                comparison = comparison,
                onConfirm = { viewModel.onEvent(BackupEvent.RestoreConfirmed(candidate)) },
                onDismiss = { viewModel.onEvent(BackupEvent.RestoreDismissed) },
            )
        }
    }
}

@Composable
private fun NotConfiguredContent(pickFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "No backup folder chosen",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick a folder and your wallet will be backed up daily.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = pickFolder) {
            Text("Choose folder")
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Working…")
    }
}

@Composable
private fun ReadyContent(
    state: BackupViewState.Ready,
    onBackUpNow: () -> Unit,
    onSelectSnapshot: (SnapshotRef) -> Unit,
    pickFolder: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Text(
                text = "Folder: ${state.folderName}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackUpNow) { Text("Back up now") }
                OutlinedButton(onClick = pickFolder) { Text("Change folder") }
            }
            if (state.unreadableCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${state.unreadableCount} file(s) in the folder could not be read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Snapshots",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (state.snapshots.isEmpty()) {
            item {
                Text(
                    text = "No snapshots yet. Tap \"Back up now\" to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.snapshots, key = { it.name }) { ref ->
            SnapshotItem(ref = ref, onClick = { onSelectSnapshot(ref) })
        }
    }
}

@Composable
private fun SnapshotItem(ref: SnapshotRef, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val dateTime = ref.capturedAt
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm"))

            val originLabel = when (ref.origin) {
                SnapshotOrigin.Scheduled -> "Scheduled"
                SnapshotOrigin.Manual -> "Manual"
                SnapshotOrigin.Safety -> "Safety (pre-restore)"
            }

            Text(text = dateTime, fontWeight = FontWeight.Medium)
            Text(
                text = "$originLabel · ${formatBytes(ref.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
            val summary = ref.summary
            Text(
                text = "${summary.transactionCount} transactions, " +
                    "${summary.accountCount} accounts, " +
                    "${summary.categoryCount} categories",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
    pickFolder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("Retry") }
            OutlinedButton(onClick = pickFolder) { Text("Pick another folder") }
        }
    }
}

@Suppress("MagicNumber")
private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}
