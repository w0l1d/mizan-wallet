package com.ivy.data.di

import com.ivy.data.backup.local.BackupStorage
import com.ivy.data.backup.local.SafFolderStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BackupStorageModule {
    @Binds
    @Singleton
    fun backupStorage(impl: SafFolderStorage): BackupStorage
}
