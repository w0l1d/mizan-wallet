package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.ZakatConfigEntity
import java.util.UUID

@Dao
interface WriteZakatConfigDao {
    @Upsert
    suspend fun save(value: ZakatConfigEntity)

    @Upsert
    suspend fun saveMany(value: List<ZakatConfigEntity>)

    @Query("DELETE FROM zakat_configs WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM zakat_configs")
    suspend fun deleteAll()
}
