package com.drivemate.showcase.domain.usecase.workday

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

class GenerateWorkdayFromAttendancesUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(driverUid: String, dateEpochDay: Int): AppResult<Unit> =
        repo.generateWorkdayFromAttendances(driverUid, dateEpochDay)
}
