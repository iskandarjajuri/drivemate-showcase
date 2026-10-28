package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

private const val TAG = "DM/ApproveWorkDayUC"

/**
 * Approve satu WorkDay dengan catatan opsional.
 */
class ApproveWorkDayUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(workDayId: String, note: String?): AppResult<Unit> {
        DMLog.i(TAG, "▶ approve start workDay=$workDayId note=${note ?: "-"}")
        return try {
            val res = repo.approve(workDayId, note)
            if (res is AppResult.Success)
                DMLog.i(TAG, "✅ success approve id=$workDayId")
            else
                DMLog.w(TAG, "⚠ non-success approve result=$res")
            res
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ approve failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}
