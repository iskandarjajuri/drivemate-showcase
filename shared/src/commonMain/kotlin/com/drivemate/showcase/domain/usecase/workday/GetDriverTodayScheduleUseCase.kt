package com.drivemate.showcase.domain.usecase.workday

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.DriverTodaySchedule
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository
import kotlinx.coroutines.CancellationException

private const val TAG = "DM/GetDriverTodaySchedule"

/**
 * Schedule preview for the driver home card, for a day that may not have a
 * `workdays` document yet (i.e. before the driver taps in).
 *
 * Priority: an existing `workdays` doc for the day (same as before) → an
 * explicit `workday_plans` entry, surfaced as [PlanSource.EXPLICIT] → empty,
 * same as today when neither is present.
 */
class GetDriverTodayScheduleUseCase(
    private val workDayRepository: WorkDayRepository
) {
    suspend operator fun invoke(driverUid: String, epochDay: Int): AppResult<DriverTodaySchedule> {
        return try {
            val existing = workDayRepository.getWorkDaysByRange(driverUid, epochDay, epochDay)
                .firstOrNull { it.dateEpochDay == epochDay }
            if (existing != null) {
                return AppResult.success(
                    DriverTodaySchedule(
                        plannedStartMillis = existing.plannedStartMillis,
                        plannedEndMillis = existing.plannedEndMillis,
                        planSource = existing.planSource
                    )
                )
            }

            val window = when (val res = workDayRepository.getExplicitPlan(driverUid, epochDay)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> {
                    DMLog.w(TAG, "getExplicitPlan fallback read failed: ${res.throwable.message}")
                    null
                }
            }
            AppResult.success(
                if (window != null) {
                    DriverTodaySchedule(
                        plannedStartMillis = window.plannedStartMillis,
                        plannedEndMillis = window.plannedEndMillis,
                        planSource = PlanSource.EXPLICIT
                    )
                } else {
                    DriverTodaySchedule(plannedStartMillis = null, plannedEndMillis = null, planSource = null)
                }
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "failed: ${t.message}", t)
            AppResult.failure(t)
        }
    }
}
