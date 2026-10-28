package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.SafetySummary
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminSafetyUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(
        startEpochDay: Int,
        endEpochDay: Int,
        driverUid: String? = null,
        limit: Int = 200
    ): AppResult<List<SafetySummary>> =
        repo.getSafetyByRange(startEpochDay, endEpochDay, driverUid, limit)
}
