package com.ivy.data.repository

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataWriteEvent
import com.ivy.data.db.dao.read.ZakatPaymentDao
import com.ivy.data.db.dao.write.WriteZakatPaymentDao
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatPayment
import com.ivy.data.model.ZakatPaymentId
import com.ivy.data.repository.mapper.ZakatPaymentMapper
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZakatPaymentRepository @Inject constructor(
    private val mapper: ZakatPaymentMapper,
    private val zakatPaymentDao: ZakatPaymentDao,
    private val writeZakatPaymentDao: WriteZakatPaymentDao,
    private val dispatchersProvider: DispatchersProvider,
    memoFactory: RepositoryMemoFactory,
) {
    private val memo = memoFactory.createMemo(
        getDataWriteSaveEvent = DataWriteEvent::SaveZakatPayments,
        getDateWriteDeleteEvent = DataWriteEvent::DeleteZakatPayments
    )

    suspend fun findAll(): List<ZakatPayment> = memo.findAll(
        findAllOperation = {
            zakatPaymentDao.findAll().mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        },
        sortMemo = { sortedBy(ZakatPayment::orderNum) }
    )

    suspend fun findByConfigId(configId: ZakatConfigId): List<ZakatPayment> {
        return withContext(dispatchersProvider.io) {
            zakatPaymentDao.findByConfigId(configId.value).mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }
    }

    suspend fun findById(id: ZakatPaymentId): ZakatPayment? = memo.findById(
        id = id,
        findByIdOperation = {
            zakatPaymentDao.findById(id.value)?.let {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }
    )

    suspend fun save(value: ZakatPayment): Unit = memo.save(value) {
        writeZakatPaymentDao.save(
            with(mapper) { it.toEntity() }
        )
    }

    suspend fun deleteById(id: ZakatPaymentId): Unit = memo.deleteById(id) {
        writeZakatPaymentDao.deleteById(id.value)
    }

    suspend fun deleteByConfigId(configId: ZakatConfigId) {
        withContext(dispatchersProvider.io) {
            writeZakatPaymentDao.deleteByConfigId(configId.value)
        }
    }

    suspend fun deleteAll(): Unit = memo.deleteAll(
        deleteAllOperation = writeZakatPaymentDao::deleteAll
    )
}
