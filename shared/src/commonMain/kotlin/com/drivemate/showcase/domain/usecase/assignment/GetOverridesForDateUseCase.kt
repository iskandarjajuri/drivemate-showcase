package com.drivemate.showcase.domain.usecase.assignment

import com.drivemate.showcase.core.model.DriverAssignmentOverride
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository

class GetOverridesForDateUseCase(
    private val repo: DriverAssignmentRepository
) {
    suspend operator fun invoke(epochDay: Int): AppResult<List<DriverAssignmentOverride>> =
        repo.getOverridesForDate(epochDay)
}
