package com.ivy.domain.usecase.backup

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.BudgetDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.model.backup.SnapshotSummary
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Describes the wallet in the handful of numbers a person needs to recognise a backup: how much
 * is in it, and how recent it is.
 *
 * It reads the DAOs rather than the repositories on purpose. A count that came from mapped domain
 * models would silently shrink whenever a legacy row failed to map, and a backup's summary has to
 * describe what is actually being written, not what the app can currently display.
 */
class ComputeWalletSummaryUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val dispatchers: DispatchersProvider,
) {
    suspend operator fun invoke(): SnapshotSummary = withContext(dispatchers.io) {
        val transactions = transactionDao.findAll()
        SnapshotSummary(
            transactionCount = transactions.size,
            accountCount = accountDao.findAll(deleted = false).size,
            categoryCount = categoryDao.findAll(deleted = false).size,
            budgetCount = budgetDao.findAll().size,
            // A planned payment has a due date but no dateTime: it is something that has not
            // happened yet, so it is not what the wallet most recently recorded.
            newestTransactionAt = transactions.mapNotNull { it.dateTime }.maxOrNull(),
        )
    }
}
