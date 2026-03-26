package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.ZakatPaymentEntity
import java.util.*

@Dao
interface ZakatPaymentDao {
    @Query("SELECT * FROM zakat_payments WHERE isDeleted = 0 ORDER BY orderId ASC")
    suspend fun findAll(): List<ZakatPaymentEntity>

    @Query("SELECT * FROM zakat_payments WHERE zakatConfigId = :configId AND isDeleted = 0 ORDER BY orderId ASC")
    suspend fun findByConfigId(configId: UUID): List<ZakatPaymentEntity>

    @Query("SELECT * FROM zakat_payments WHERE id = :id")
    suspend fun findById(id: UUID): ZakatPaymentEntity?
}
