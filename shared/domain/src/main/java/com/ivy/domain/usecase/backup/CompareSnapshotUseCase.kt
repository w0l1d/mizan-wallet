package com.ivy.domain.usecase.backup

import com.ivy.data.model.backup.SnapshotComparison
import com.ivy.data.model.backup.SnapshotRef
import javax.inject.Inject

/**
 * What restoring a snapshot would change, shown before the user confirms.
 *
 * It holds no storage on purpose: the snapshot carries its own summary, so this question can be
 * answered for a snapshot that is slow to read, or no longer there at all.
 */
class CompareSnapshotUseCase @Inject constructor(
    private val computeSummary: ComputeWalletSummaryUseCase,
) {
    suspend operator fun invoke(candidate: SnapshotRef): SnapshotComparison = SnapshotComparison(
        current = computeSummary(),
        candidate = candidate.summary,
    )
}
