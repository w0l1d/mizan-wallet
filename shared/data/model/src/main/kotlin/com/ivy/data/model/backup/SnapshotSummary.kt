package com.ivy.data.model.backup

import java.time.Instant

/**
 * The cheap description of a snapshot's contents, written into the manifest at capture time so
 * that listing and comparison never open the data document.
 *
 * [newestTransactionAt] is nullable rather than epoch-zero: "no transactions" and "a transaction
 * dated 1970" are different facts and the comparison screen must not conflate them.
 */
data class SnapshotSummary(
    val transactionCount: Int,
    val accountCount: Int,
    val categoryCount: Int,
    val budgetCount: Int,
    val newestTransactionAt: Instant?,
) {
    init {
        require(transactionCount >= 0) { "transactionCount must be non-negative" }
        require(accountCount >= 0) { "accountCount must be non-negative" }
        require(categoryCount >= 0) { "categoryCount must be non-negative" }
        require(budgetCount >= 0) { "budgetCount must be non-negative" }
    }

    companion object {
        val Empty = SnapshotSummary(
            transactionCount = 0,
            accountCount = 0,
            categoryCount = 0,
            budgetCount = 0,
            newestTransactionAt = null,
        )
    }
}
