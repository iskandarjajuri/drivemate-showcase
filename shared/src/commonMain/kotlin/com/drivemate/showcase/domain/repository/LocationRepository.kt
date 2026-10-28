// shared/commonMain/.../domain/repository/LocationRepository.kt
package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    suspend fun updateDriverLocation(loc: Location): AppResult<Unit>
    suspend fun clearDriverLocation(): AppResult<Unit>
    fun observeAllDrivers(): Flow<AppResult<List<Location>>>
}
