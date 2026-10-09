package com.ivy.data.model.backup

/**
 * What restoring a snapshot would change, computed from two summaries only — never by parsing the
 * snapshot's data document.
 */
data class SnapshotComparison(
    val current: SnapshotSummary,
    val candidate: SnapshotSummary,
) {
    val transactionDelta: Int = candidate.transactionCount - current.transactionCount
    val accountDelta: Int = candidate.accountCount - current.accountCount
    val categoryDelta: Int = candidate.categoryCount - current.categoryCount
    val budgetDelta: Int = candidate.budgetCount - current.budgetCount
}
