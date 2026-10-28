package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository
import kotlinx.datetime.LocalDate

private const val TAG = "DM/GetWorkDaysByMonthUC"

/**
 * Ambil daftar WorkDay per bulan untuk review/approval.
 */
class GetWorkDaysByMonthUseCase(
    private val reportRepository: WorkDayRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        year: Int,
        month: Int
    ): AppResult<List<WorkDay>> {
        DMLog.i(TAG, "▶ start: driver=$driverUid year=$year month=$month")
        return try {
            val (startEpoch, endEpoch) = monthRangeEpochDays(year, month)

            val list = reportRepository.getWorkDaysByRange(
                driverUid = driverUid,
                startEpochDay = startEpoch,
                endEpochDay = endEpoch
            )

            DMLog.i(TAG, "✅ success: ${list.size} items (${startEpoch}–${endEpoch})")
            AppResult.success(list)
        } catch (t: Throwable) {
            DMLog.e(TAG, "❌ failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}

internal fun monthRangeEpochDays(year: Int, month: Int): Pair<Int, Int> {
    val startDate = LocalDate(year, month, 1)
    val nextMonthFirstDay = if (month == 12) {
        LocalDate(year + 1, 1, 1)
    } else {
        LocalDate(year, month + 1, 1)
    }
    val startEpoch = startDate.toEpochDays().toInt()
    // Repository query uses <= endEpochDay, so month range must be inclusive.
    val endEpoch = nextMonthFirstDay.toEpochDays().toInt() - 1
    return startEpoch to endEpoch
}
