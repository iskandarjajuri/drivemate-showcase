package com.drivemate.showcase.domain.usecase.location

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.LocationRepository

class ClearDriverLocationUseCase(
    private val repo: LocationRepository
) {
    suspend operator fun invoke(): AppResult<Unit> = repo.clearDriverLocation()
}
