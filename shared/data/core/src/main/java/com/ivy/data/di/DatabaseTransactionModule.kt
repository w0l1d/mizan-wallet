package com.ivy.data.di

import com.ivy.data.db.transaction.DatabaseTransaction
import com.ivy.data.db.transaction.RoomDatabaseTransaction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DatabaseTransactionModule {

    @Binds
    @Singleton
    fun databaseTransaction(impl: RoomDatabaseTransaction): DatabaseTransaction
}
