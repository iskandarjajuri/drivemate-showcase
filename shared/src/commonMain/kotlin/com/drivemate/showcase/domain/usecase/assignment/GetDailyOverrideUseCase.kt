package com.drivemate.showcase.domain.usecase.assignment

import com.drivemate.showcase.core.model.DriverAssignmentOverride
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository

class GetDailyOverrideUseCase(
    private val repo: DriverAssignmentRepository
) {
    suspend operator fun invoke(driverUid: String, epochDay: Int): AppResult<DriverAssignmentOverride?> =
        repo.getDailyOverride(driverUid, epochDay)
}
