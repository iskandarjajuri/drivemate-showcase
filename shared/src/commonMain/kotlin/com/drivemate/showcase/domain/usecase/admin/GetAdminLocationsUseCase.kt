package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminLocationsUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(limit: Int = 200): AppResult<List<Location>> =
        repo.getLocations(limit)
}
