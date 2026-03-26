package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.ZakatConfigEntity
import java.util.*

@Dao
interface ZakatConfigDao {
    @Query("SELECT * FROM zakat_configs WHERE isDeleted = 0 ORDER BY orderId ASC")
    suspend fun findAll(): List<ZakatConfigEntity>

    @Query("SELECT * FROM zakat_configs WHERE id = :id")
    suspend fun findById(id: UUID): ZakatConfigEntity?

    @Query("SELECT MAX(orderId) FROM zakat_configs")
    suspend fun findMaxOrderNum(): Double?
}
