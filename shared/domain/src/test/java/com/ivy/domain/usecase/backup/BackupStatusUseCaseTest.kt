package com.ivy.domain.usecase.backup

import arrow.core.Either
import com.ivy.data.backup.local.BackupAttempt
import com.ivy.data.backup.local.BackupDestinationConfig
import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant

class BackupStatusUseCaseTest {

    private val listSnapshots = mockk<ListSnapshotsUseCase>()
    private val destinationConfig = mockk<BackupDestinationConfig>()

    private lateinit var useCase: BackupStatusUseCase

    @Before
    fun setUp() {
        useCase = BackupStatusUseCase(
            listSnapshots = listSnapshots,
            destinationConfig = destinationConfig,
        )
    }

    @Test
    fun `not configured when no destination set`() = runBlocking<Unit> {
        every { destinationConfig.destination } returns flowOf(null)
        every { destinationConfig.lastAttempt } returns flowOf(null)

        val status = useCase()

        status.shouldBeInstanceOf<BackupStatus.NotConfigured>()
    }

    @Test
    fun `never backed up when destination set but no snapshots exist`() = runBlocking<Unit> {
        configured()
        every { destinationConfig.lastAttempt } returns flowOf(null)
        coEvery { listSnapshots() } returns Either.Right(
            BackupStorage.Listing(snapshots = emptyList(), unreadable = 0),
        )

        val status = useCase()

        status.shouldBeInstanceOf<BackupStatus.NeverBackedUp>()
    }

    @Test
    fun `healthy derives age from newest snapshot capturedAt`() = runBlocking<Unit> {
        configured()
        val capturedAt = Instant.parse("2026-10-04T10:00:00Z")
        every { destinationConfig.lastAttempt } returns flowOf(
            BackupAttempt(at = capturedAt, success = true, errorMessage = null),
        )
        coEvery { listSnapshots() } returns Either.Right(
            BackupStorage.Listing(
                snapshots = listOf(snapshotAt(capturedAt)),
                unreadable = 0,
            ),
        )

        val status = useCase()

        val healthy = status.shouldBeInstanceOf<BackupStatus.Healthy>()
        healthy.lastSnapshotAt shouldBe capturedAt
        healthy.snapshotCount shouldBe 1
    }

    @Test
    fun `failing when last attempt failed`() = runBlocking<Unit> {
        configured()
        val capturedAt = Instant.parse("2026-10-03T10:00:00Z")
        val attemptedAt = Instant.parse("2026-10-04T02:00:00Z")
        every { destinationConfig.lastAttempt } returns flowOf(
            BackupAttempt(at = attemptedAt, success = false, errorMessage = "Disk full"),
        )
        coEvery { listSnapshots() } returns Either.Right(
            BackupStorage.Listing(
                snapshots = listOf(snapshotAt(capturedAt)),
                unreadable = 0,
            ),
        )

        val status = useCase()

        val failing = status.shouldBeInstanceOf<BackupStatus.Failing>()
        failing.lastSnapshotAt shouldBe capturedAt
        failing.lastAttemptError shouldBe "Disk full"
    }

    @Test
    fun `unreachable when listing fails with access denied`() = runBlocking<Unit> {
        configured()
        every { destinationConfig.lastAttempt } returns flowOf(null)
        coEvery { listSnapshots() } returns Either.Left(BackupError.AccessDenied)

        val status = useCase()

        status.shouldBeInstanceOf<BackupStatus.Unreachable>()
    }

    private fun configured() {
        every { destinationConfig.destination } returns flowOf(
            com.ivy.data.backup.local.BackupDestination(
                treeUri = "content://folder",
                configuredAt = Instant.parse("2026-10-01T00:00:00Z"),
            ),
        )
    }

    private fun snapshotAt(capturedAt: Instant) = SnapshotRef(
        name = "test.zip",
        uri = "fake://1",
        capturedAt = capturedAt,
        origin = SnapshotOrigin.Scheduled,
        summary = SnapshotSummary(
            transactionCount = 10,
            accountCount = 2,
            categoryCount = 3,
            budgetCount = 0,
            newestTransactionAt = capturedAt,
        ),
        sizeBytes = 1000L,
    )
}
