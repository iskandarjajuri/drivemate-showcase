package com.drivemate.showcase.domain.usecase.workday

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

class UpsertWorkdayPlanUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String
    ): AppResult<Unit> {
        return try {
            repo.upsertWorkdayPlan(driverUid, epochDay, plannedStartMillis, plannedEndMillis, source)
        } catch (t: Throwable) {
            AppResult.failure(t)
        }
    }
}
