package com.ivy.data.repository

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataWriteEvent
import com.ivy.data.db.dao.read.ZakatConfigDao
import com.ivy.data.db.dao.write.WriteZakatConfigDao
import com.ivy.data.model.ZakatConfig
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.repository.mapper.ZakatConfigMapper
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZakatConfigRepository @Inject constructor(
    private val mapper: ZakatConfigMapper,
    private val zakatConfigDao: ZakatConfigDao,
    private val writeZakatConfigDao: WriteZakatConfigDao,
    private val dispatchersProvider: DispatchersProvider,
    memoFactory: RepositoryMemoFactory,
) {
    private val memo = memoFactory.createMemo(
        getDataWriteSaveEvent = DataWriteEvent::SaveZakatConfigs,
        getDateWriteDeleteEvent = DataWriteEvent::DeleteZakatConfigs
    )

    suspend fun findById(id: ZakatConfigId): ZakatConfig? = memo.findById(
        id = id,
        findByIdOperation = {
            zakatConfigDao.findById(id.value)?.let {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }
    )

    suspend fun findAll(): List<ZakatConfig> = memo.findAll(
        findAllOperation = {
            zakatConfigDao.findAll().mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        },
        sortMemo = { sortedBy(ZakatConfig::orderNum) }
    )

    suspend fun findMaxOrderNum(): Double = if (memo.findAllMemoized) {
        memo.items.maxOfOrNull { (_, config) -> config.orderNum } ?: 0.0
    } else {
        withContext(dispatchersProvider.io) {
            zakatConfigDao.findMaxOrderNum() ?: 0.0
        }
    }

    suspend fun save(value: ZakatConfig): Unit = memo.save(value) {
        writeZakatConfigDao.save(
            with(mapper) { it.toEntity() }
        )
    }

    suspend fun deleteById(id: ZakatConfigId): Unit = memo.deleteById(id) {
        writeZakatConfigDao.deleteById(id.value)
    }

    suspend fun deleteAll(): Unit = memo.deleteAll(
        deleteAllOperation = writeZakatConfigDao::deleteAll
    )
}
