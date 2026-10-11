package com.ivy.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ivy.backup.BackupScheduler
import com.ivy.base.legacy.appContext
import com.ivy.data.backup.local.BackupDestinationConfig
import com.ivy.domain.features.ExcludedTransferPrefsMigration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

/**
 * Created by iliyan on 24.02.18.
 */
@HiltAndroidApp
class IvyAndroidApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var backupScheduler: BackupScheduler

    @Inject
    lateinit var backupDestinationConfig: BackupDestinationConfig

    @Inject
    lateinit var excludedTransferPrefsMigration: ExcludedTransferPrefsMigration

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        // One-shot and self-clearing; see ExcludedTransferPrefsMigration.
        appScope.launch {
            excludedTransferPrefsMigration.migrate()
        }

        // Schedule the daily backup only when the user has picked a folder; otherwise the worker
        // would start, discover NotConfigured, and stop — wasting a wake-up every day.
        appScope.launch {
            val destination = backupDestinationConfig.destination.first()
            if (destination != null) {
                backupScheduler.schedule()
            }
        }
    }

}
