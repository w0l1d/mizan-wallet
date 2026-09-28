package com.ivy.domain.features

import android.content.Context
import com.ivy.base.legacy.SharedPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the two excluded-transfer toggles from [SharedPrefs] over to the
 * DataStore-backed [BoolFeature]s they now live in.
 *
 * They used to be switches in Settings -> App Settings persisted as SharedPrefs
 * booleans; they are now entries on the Features screen. Without this, anyone who
 * had turned one on would silently find it off after updating.
 *
 * Self-clearing and therefore idempotent: the SharedPrefs key is removed once
 * copied, so a second run finds nothing to do. That also means a value written by
 * an old build is never able to shadow a later change made on the Features screen.
 */
@Singleton
class ExcludedTransferPrefsMigration @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sharedPrefs: SharedPrefs,
    private val features: Features,
) {
    suspend fun migrate() {
        migrateKey(
            key = SharedPrefs.TRANSFERS_TO_EXCLUDED_AS_EXPENSE,
            feature = features.transfersToExcludedAsExpense,
        )
        migrateKey(
            key = SharedPrefs.TRANSFERS_FROM_EXCLUDED_AS_INCOME,
            feature = features.transfersFromExcludedAsIncome,
        )
    }

    private suspend fun migrateKey(key: String, feature: BoolFeature) {
        if (!sharedPrefs.has(key)) return

        feature.set(appContext, sharedPrefs.getBoolean(key, false))
        sharedPrefs.remove(key)
    }
}
