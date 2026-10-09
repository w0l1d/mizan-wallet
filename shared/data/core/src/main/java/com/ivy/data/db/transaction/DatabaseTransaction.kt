package com.ivy.data.db.transaction

import androidx.room.withTransaction
import com.ivy.data.db.IvyRoomDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a block of writes as one unit: either every write lands or none does.
 *
 * `IvyRoomDatabase` exposes no such primitive on its own, and a restore that writes several tables
 * without one can leave a wallet half-replaced — a state the user cannot see and cannot undo.
 *
 * It is an interface so a unit test can prove the all-or-nothing contract without a device.
 */
interface DatabaseTransaction {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

@Singleton
class RoomDatabaseTransaction @Inject constructor(
    private val db: IvyRoomDatabase,
) : DatabaseTransaction {
    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        db.withTransaction { block() }
}
