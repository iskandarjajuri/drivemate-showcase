package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

private const val TAG = "DM/RejectWorkDayUC"

/**
 * Reject satu WorkDay dengan alasan.
 */
class RejectWorkDayUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(workDayId: String, note: String): AppResult<Unit> {
        DMLog.w(TAG, "▶ reject start workDay=$workDayId note=$note")
        return try {
            val res = repo.reject(workDayId, note)
            if (res is AppResult.Success)
                DMLog.w(TAG, "✅ success reject id=$workDayId")
            else
                DMLog.w(TAG, "⚠ non-success reject result=$res")
            res
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ reject failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}
