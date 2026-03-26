package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.data.db.entity.ZakatPaymentEntity
import com.ivy.data.model.ZakatConfigId
import com.ivy.data.model.ZakatPayment
import com.ivy.data.model.ZakatPaymentId
import javax.inject.Inject

class ZakatPaymentMapper @Inject constructor() {
    fun ZakatPaymentEntity.toDomain(): Either<String, ZakatPayment> = either {
        ensure(!isDeleted) { "ZakatPayment is deleted" }

        ZakatPayment(
            id = ZakatPaymentId(id),
            zakatConfigId = ZakatConfigId(zakatConfigId),
            amount = amount,
            dateTime = dateTime,
            transactionId = transactionId,
            note = note,
            orderNum = orderId,
        )
    }

    fun ZakatPayment.toEntity(): ZakatPaymentEntity {
        return ZakatPaymentEntity(
            zakatConfigId = zakatConfigId.value,
            amount = amount,
            dateTime = dateTime,
            transactionId = transactionId,
            note = note,
            orderId = orderNum,
            id = id.value,
            isSynced = true,
        )
    }
}
