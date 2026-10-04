package com.ivy.data.db.transaction.fake

import com.ivy.data.db.transaction.DatabaseTransaction

/**
 * A [DatabaseTransaction] that records whether the block completed and re-throws what the block
 * threw, so a test can assert that a caller treats a thrown block as "nothing was written".
 *
 * Rolling back is the fake's caller's job: the fakes it writes through are the ones that must be
 * restored, which keeps this fake honest about what it does and does not guarantee.
 */
class FakeDatabaseTransaction(
    private val onRollback: suspend () -> Unit = {},
) : DatabaseTransaction {

    var started: Int = 0
        private set
    var committed: Int = 0
        private set
    var rolledBack: Int = 0
        private set

    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        started++
        return try {
            block().also { committed++ }
        } catch (e: Throwable) {
            rolledBack++
            onRollback()
            throw e
        }
    }
}
