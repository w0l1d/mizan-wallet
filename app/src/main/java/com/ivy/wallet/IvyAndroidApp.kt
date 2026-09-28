package com.ivy.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ivy.base.legacy.appContext
import com.ivy.domain.features.ExcludedTransferPrefsMigration
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.android.timber.SentryTimberIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    lateinit var excludedTransferPrefsMigration: ExcludedTransferPrefsMigration

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this

        initSentry()

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        // One-shot and self-clearing; see ExcludedTransferPrefsMigration.
        appScope.launch {
            excludedTransferPrefsMigration.migrate()
        }
    }

    @Suppress("MagicNumber")
    private fun initSentry() {
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN

            // "debug" / "demo" / "release" - the same names the CI workflows build.
            options.environment = BuildConfig.BUILD_TYPE
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

            // Traces every transaction for now; lower once there is real user volume.
            options.tracesSampleRate = 1.0

            // This app shows account balances and transaction amounts, so every replay
            // is recorded with text and images masked out.
            options.sessionReplay.sessionSampleRate = 0.1
            options.sessionReplay.onErrorSampleRate = 1.0
            options.sessionReplay.setMaskAllText(true)
            options.sessionReplay.setMaskAllImages(true)

            options.logs.isEnabled = true

            // Structure only - no rendered values, unlike a screenshot.
            options.isAttachViewHierarchy = true

            // Timber.e/w calls become Sentry events even in builds where no DebugTree
            // is planted.
            options.addIntegration(
                SentryTimberIntegration(
                    minEventLevel = SentryLevel.ERROR,
                    minBreadcrumbLevel = SentryLevel.INFO,
                )
            )

            options.isDebug = BuildConfig.DEBUG
        }
    }

    private companion object {
        const val SENTRY_DSN =
            "https://a091b42b231d27146e67e419a9c0fd28@o4512163904159744.ingest.de.sentry.io/4512163962290256"
    }
}
