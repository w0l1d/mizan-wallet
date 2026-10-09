package com.ivy.backup

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for one backup a day, and keeps asking for the same one.
 *
 * [ExistingPeriodicWorkPolicy.KEEP] is the whole point: app start is where this is called from,
 * and REPLACE would push the next backup a full day away every time the app was opened, so a
 * frequently used app would never back up at all.
 *
 * Periodic work does not backfill. A device that was off all day gets one backup when it comes
 * back, not a queue of missed ones.
 */
@Singleton
class BackupScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    // A backup is never urgent enough to be the reason a phone dies.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ivy-wallet-local-backup"
        private const val INTERVAL_HOURS = 24L
    }
}
