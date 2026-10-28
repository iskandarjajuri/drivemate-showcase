package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminWorkdaysUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<WorkDay>> =
        repo.getWorkdaysByRange(startEpochDay, endEpochDay, driverUid, limit)
}
