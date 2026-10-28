package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.BulkActionType
import com.drivemate.showcase.domain.repository.WorkDayRepository

private const val TAG = "DM/BulkApproveUC"

/**
 * Bulk approve kumpulan WorkDay oleh Expat.
 */
class BulkApproveWorkDaysUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(workDayIds: List<String>): BulkActionResult {
        DMLog.i(TAG, "▶ start approve count=${workDayIds.size}")
        return try {
            val res = repo.bulkApprove(workDayIds)
            DMLog.i(
                TAG,
                "✅ bulk approve done success=${res.successCount} skipped=${res.skippedCount} failed=${res.failedCount}"
            )
            res
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ exception bulk approve: ${t.message}", t)
            BulkActionResult(
                action = BulkActionType.APPROVE,
                attemptedCount = workDayIds.size,
                successCount = 0,
                skippedCount = 0,
                failedCount = workDayIds.size,
                items = emptyList()
            )
        }
    }
}
