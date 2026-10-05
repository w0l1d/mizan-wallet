package com.ivy.wallet.di

import android.os.Build
import com.ivy.domain.usecase.backup.BackupEnvironment
import com.ivy.wallet.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupEnvironmentModule {
    private const val DATA_SCHEMA_VERSION = 130

    @Provides
    @Singleton
    fun backupEnvironment(): BackupEnvironment = object : BackupEnvironment {
        override val deviceName: String get() = Build.MODEL
        override val appVersion: String get() = BuildConfig.VERSION_NAME
        override val dataSchemaVersion: Int = DATA_SCHEMA_VERSION
    }
}
