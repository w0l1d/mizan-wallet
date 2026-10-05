package com.ivy.backup

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import arrow.core.Either
import com.ivy.base.time.TimeProvider
import com.ivy.data.backup.local.BackupDestinationConfig
import com.ivy.data.model.backup.BackupError
import com.ivy.data.model.backup.SnapshotOrigin
import com.ivy.data.model.backup.SnapshotRef
import com.ivy.data.model.backup.SnapshotSummary
import com.ivy.domain.usecase.backup.CaptureSnapshotUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class BackupWorkerTest {

    private val capture = mockk<CaptureSnapshotUseCase>()
    private val destinationConfig = mockk<BackupDestinationConfig>(relaxed = true)
    private val timeProvider = mockk<TimeProvider>()

    init {
        every { timeProvider.utcNow() } returns Instant.parse("2026-10-05T12:00:00Z")
    }

    private fun worker() = BackupWorker(
        appContext = mockk<Context>(relaxed = true),
        params = mockk<WorkerParameters>(relaxed = true),
        capture = capture,
        destinationConfig = destinationConfig,
        timeProvider = timeProvider,
    )

    private fun captureReturns(result: Either<BackupError, SnapshotRef>) {
        coEvery { capture(SnapshotOrigin.Scheduled) } returns result
    }

    @Test
    fun `a snapshot written is a successful run`() = runBlocking<Unit> {
        captureReturns(Either.Right(ref))

        worker().doWork() shouldBe ListenableWorker.Result.success()
    }

    @Test
    fun `a write that did not complete is worth another attempt`() = runBlocking<Unit> {
        captureReturns(Either.Left(BackupError.WriteFailed(null)))

        worker().doWork() shouldBe ListenableWorker.Result.retry()
    }

    @Test
    fun `no folder picked is not worth retrying`() = runBlocking<Unit> {
        captureReturns(Either.Left(BackupError.NotConfigured))

        worker().doWork() shouldBe ListenableWorker.Result.failure()
    }

    @Test
    fun `a folder the app can no longer reach is not worth retrying`() = runBlocking<Unit> {
        captureReturns(Either.Left(BackupError.AccessDenied))

        worker().doWork() shouldBe ListenableWorker.Result.failure()
    }

    @Test
    fun `a full destination is not worth retrying before the next run`() = runBlocking<Unit> {
        captureReturns(Either.Left(BackupError.OutOfSpace))

        worker().doWork() shouldBe ListenableWorker.Result.failure()
    }

    private val ref = SnapshotRef(
        name = "wallet-20260601T120000Z--pixel--sched.zip",
        uri = "fake://snapshot",
        capturedAt = Instant.parse("2026-06-01T12:00:00Z"),
        origin = SnapshotOrigin.Scheduled,
        summary = SnapshotSummary.Empty,
        sizeBytes = 1L,
    )
}
