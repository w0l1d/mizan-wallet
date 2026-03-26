package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.ZakatPaymentEntity
import java.util.UUID

@Dao
interface WriteZakatPaymentDao {
    @Upsert
    suspend fun save(value: ZakatPaymentEntity)

    @Query("DELETE FROM zakat_payments WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM zakat_payments WHERE zakatConfigId = :configId")
    suspend fun deleteByConfigId(configId: UUID)

    @Query("DELETE FROM zakat_payments")
    suspend fun deleteAll()
}
