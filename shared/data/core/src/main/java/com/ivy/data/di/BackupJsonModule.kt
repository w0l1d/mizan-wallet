package com.ivy.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupJsonModule {

    const val BACKUP_JSON = "backup_json"

    @Provides
    @Singleton
    @Named(BACKUP_JSON)
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
