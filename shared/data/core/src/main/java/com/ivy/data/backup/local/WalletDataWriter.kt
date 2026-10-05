package com.ivy.data.backup.local

import com.ivy.data.backup.IvyWalletCompleteData
import com.ivy.data.db.dao.write.WriteAccountDao
import com.ivy.data.db.dao.write.WriteBudgetDao
import com.ivy.data.db.dao.write.WriteCategoryDao
import com.ivy.data.db.dao.write.WriteLoanDao
import com.ivy.data.db.dao.write.WriteLoanRecordDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WriteSettingsDao
import com.ivy.data.db.dao.write.WriteTagAssociationDao
import com.ivy.data.db.dao.write.WriteTagDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import javax.inject.Inject

/**
 * Writes a complete wallet export into the database, record by record.
 *
 * Every write is an upsert keyed on the record's own id, so restoring a snapshot replaces the
 * records it carries and leaves everything created since it was taken alone. It never deletes.
 *
 * Writes are sequential rather than concurrent: this runs inside one database transaction, and a
 * transaction belongs to the coroutine that opened it.
 *
 * It does not restore the export's `sharedPrefs`. Those are app preferences, not wallet records,
 * and the MVP's promise is about the wallet; restoring them needs a separate decision about
 * which of the current device's settings may be overwritten.
 */
class WalletDataWriter @Inject constructor(
    private val accountDao: WriteAccountDao,
    private val budgetDao: WriteBudgetDao,
    private val categoryDao: WriteCategoryDao,
    private val loanDao: WriteLoanDao,
    private val loanRecordDao: WriteLoanRecordDao,
    private val plannedPaymentRuleDao: WritePlannedPaymentRuleDao,
    private val settingsDao: WriteSettingsDao,
    private val transactionDao: WriteTransactionDao,
    private val tagDao: WriteTagDao,
    private val tagAssociationDao: WriteTagAssociationDao,
) {
    suspend fun write(data: IvyWalletCompleteData) {
        // Accounts and categories first: a transaction that names one is easier to reason about
        // when the thing it names is already there.
        accountDao.saveMany(data.accounts)
        categoryDao.saveMany(data.categories)
        settingsDao.saveMany(data.settings)
        budgetDao.saveMany(data.budgets)
        loanDao.saveMany(data.loans)
        loanRecordDao.saveMany(data.loanRecords)
        plannedPaymentRuleDao.saveMany(data.plannedPaymentRules)
        transactionDao.saveMany(data.transactions)
        tagDao.save(data.tags)
        tagAssociationDao.save(data.tagAssociations)
    }
}
