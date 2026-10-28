package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

private const val TAG = "DM/GetWorkDaysByRangeUC"

/**
 * Ambil daftar WorkDay berdasarkan rentang epochDay.
 */
class GetWorkDaysByRangeUseCase(
    private val reportRepository: WorkDayRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): AppResult<List<WorkDay>> {
        DMLog.i(TAG, "▶ start: driver=$driverUid start=$startEpochDay end=$endEpochDay")
        return try {
            val list = reportRepository.getWorkDaysByRange(
                driverUid = driverUid,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay
            )
            DMLog.i(TAG, "✅ success: ${list.size} items")
            AppResult.success(list)
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}
