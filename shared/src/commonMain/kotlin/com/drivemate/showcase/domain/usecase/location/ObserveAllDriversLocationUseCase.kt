package com.drivemate.showcase.domain.usecase.location

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllDriversLocationUseCase(
    private val repo: LocationRepository
) {
    // <-- penting: bikin callable seperti fungsi
    operator fun invoke(): Flow<AppResult<List<Location>>> = repo.observeAllDrivers()
}
