package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.BulkActionType
import com.drivemate.showcase.domain.repository.WorkDayRepository

private const val TAG = "DM/BulkRejectUC"

/**
 * Bulk reject kumpulan WorkDay oleh Expat.
 */
class BulkRejectWorkDaysUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(workDayIds: List<String>, note: String): BulkActionResult {
        DMLog.i(TAG, "▶ start reject count=${workDayIds.size}")
        return try {
            val res = repo.bulkReject(workDayIds, note)
            DMLog.i(
                TAG,
                "✅ bulk reject done success=${res.successCount} skipped=${res.skippedCount} failed=${res.failedCount}"
            )
            res
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ exception bulk reject: ${t.message}", t)
            BulkActionResult(
                action = BulkActionType.REJECT,
                attemptedCount = workDayIds.size,
                successCount = 0,
                skippedCount = 0,
                failedCount = workDayIds.size,
                items = emptyList()
            )
        }
    }
}
