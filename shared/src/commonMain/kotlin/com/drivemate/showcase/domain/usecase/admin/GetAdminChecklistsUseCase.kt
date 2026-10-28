package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.ChecklistSummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminChecklistsUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<ChecklistSummary>> =
        repo.getChecklistsByRange(startEpochDay, endEpochDay, driverUid, limit)
}
