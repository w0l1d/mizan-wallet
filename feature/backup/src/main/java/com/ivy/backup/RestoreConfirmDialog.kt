package com.ivy.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotComparison
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RestoreConfirmDialog(
    candidate: SnapshotRef,
    comparison: SnapshotComparison,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateTime = candidate.capturedAt
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from $dateTime?") },
        text = {
            Column {
                Text(
                    text = "This will merge the snapshot's records into your wallet. " +
                        "Records in the snapshot win; records added since then stay.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(16.dp))

                ComparisonTable(comparison)

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "A safety snapshot will be taken first — you can undo this restore " +
                        "by restoring that snapshot.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Restore")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ComparisonTable(comparison: SnapshotComparison) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "Comparison",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        ComparisonRow("Transactions", comparison.current.transactionCount, comparison.candidate.transactionCount)
        ComparisonRow("Accounts", comparison.current.accountCount, comparison.candidate.accountCount)
        ComparisonRow("Categories", comparison.current.categoryCount, comparison.candidate.categoryCount)
        ComparisonRow("Budgets", comparison.current.budgetCount, comparison.candidate.budgetCount)
    }
}

@Composable
private fun ComparisonRow(label: String, current: Int, candidate: Int) {
    val delta = candidate - current
    val deltaText = when {
        delta > 0 -> "+$delta"
        delta < 0 -> "$delta"
        else -> "—"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "$current → $candidate ($deltaText)",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
