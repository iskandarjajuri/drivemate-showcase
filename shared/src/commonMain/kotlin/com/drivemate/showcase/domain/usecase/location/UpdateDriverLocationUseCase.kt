// shared/src/commonMain/.../domain/usecase/location/UpdateDriverLocationUseCase.kt
package com.drivemate.showcase.domain.usecase.location

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.LocationRepository

class UpdateDriverLocationUseCase(
    private val repo: LocationRepository
) {
    suspend operator fun invoke(loc: Location): AppResult<Unit> =
        repo.updateDriverLocation(loc)
}