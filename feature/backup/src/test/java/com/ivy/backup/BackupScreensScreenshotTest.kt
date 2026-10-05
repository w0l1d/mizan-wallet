package com.ivy.backup

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import com.ivy.ui.testing.PaparazziScreenshotTest
import com.ivy.ui.testing.PaparazziTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(TestParameterInjector::class)
class BackupScreensScreenshotTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {

    @Test
    fun `snapshot not configured state`() {
        snapshot(theme) {
            BackupSettingsContent(state = BackupViewState.NotConfigured)
        }
    }

    @Test
    fun `snapshot ready with snapshots`() {
        snapshot(theme) {
            BackupSettingsContent(
                state = BackupViewState.Ready(
                    folderName = "IvyBackups",
                    snapshots = persistentListOf(manualRef, scheduledRef, safetyRef),
                    unreadableCount = 1,
                ),
            )
        }
    }

    @Test
    fun `snapshot failed state`() {
        snapshot(theme) {
            BackupSettingsContent(
                state = BackupViewState.Failed(
                    message = "Cannot reach the backup folder. It may have been moved.",
                ),
            )
        }
    }

    @Test
    fun `snapshot loading state`() {
        snapshot(theme) {
            BackupSettingsContent(state = BackupViewState.Loading)
        }
    }

    private val manualRef = SnapshotRef(
        name = "wallet-20261004T100000Z--pixel--manual.zip",
        uri = "fake://1",
        capturedAt = Instant.parse("2026-10-04T10:00:00Z"),
        origin = SnapshotOrigin.Manual,
        summary = SnapshotSummary(
            transactionCount = 42,
            accountCount = 3,
            categoryCount = 5,
            budgetCount = 1,
            newestTransactionAt = Instant.parse("2026-10-04T09:30:00Z"),
        ),
        sizeBytes = 24_576L,
    )

    private val scheduledRef = SnapshotRef(
        name = "wallet-20261003T020000Z--pixel--sched.zip",
        uri = "fake://2",
        capturedAt = Instant.parse("2026-10-03T02:00:00Z"),
        origin = SnapshotOrigin.Scheduled,
        summary = SnapshotSummary(
            transactionCount = 40,
            accountCount = 3,
            categoryCount = 5,
            budgetCount = 1,
            newestTransactionAt = Instant.parse("2026-10-02T18:00:00Z"),
        ),
        sizeBytes = 23_800L,
    )

    private val safetyRef = SnapshotRef(
        name = "wallet-20261001T120000Z--pixel--safety.zip",
        uri = "fake://3",
        capturedAt = Instant.parse("2026-10-01T12:00:00Z"),
        origin = SnapshotOrigin.Safety,
        summary = SnapshotSummary(
            transactionCount = 38,
            accountCount = 3,
            categoryCount = 4,
            budgetCount = 1,
            newestTransactionAt = Instant.parse("2026-10-01T08:00:00Z"),
        ),
        sizeBytes = 22_000L,
    )
}
