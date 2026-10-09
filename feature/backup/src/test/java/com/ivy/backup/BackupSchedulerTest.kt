package com.ivy.backup

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackupSchedulerTest {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private val scheduler = BackupScheduler(workManager)

    @Test
    fun `the daily backup is scheduled once and not reset on every app start`() {
        val policy = slot<ExistingPeriodicWorkPolicy>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), capture(policy), any())
        } returns mockk(relaxed = true)

        scheduler.schedule()

        // KEEP is what stops a restart from pushing the next backup another day away.
        policy.captured shouldBe ExistingPeriodicWorkPolicy.KEEP
    }

    @Test
    fun `it asks for one backup a day and not while the battery is low`() {
        val request = slot<PeriodicWorkRequest>()
        every {
            workManager.enqueueUniquePeriodicWork(any(), any(), capture(request))
        } returns mockk(relaxed = true)

        scheduler.schedule()

        val spec = request.captured.workSpec
        spec.intervalDuration shouldBe TimeUnit.HOURS.toMillis(24)
        spec.constraints.requiresBatteryNotLow() shouldBe true
        spec.workerClassName shouldBe BackupWorker::class.java.name
    }

    @Test
    fun `clearing the destination stops the schedule`() {
        scheduler.cancel()

        verify { workManager.cancelUniqueWork(BackupScheduler.UNIQUE_WORK_NAME) }
    }
}
