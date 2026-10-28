package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.WorkdaySummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

private const val TAG = "DM/GetWorkdaySummaryUC"

class GetWorkdaySummaryUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): AppResult<WorkdaySummary> {
        DMLog.i(TAG, "▶ start: driver=$driverUid start=$startEpochDay end=$endEpochDay")
        return try {
            val summary = repo.getWorkdaySummary(driverUid, startEpochDay, endEpochDay)
            DMLog.i(TAG, "✅ success: total=${summary.totalDays} pending=${summary.pendingCount}")
            AppResult.success(summary)
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}
