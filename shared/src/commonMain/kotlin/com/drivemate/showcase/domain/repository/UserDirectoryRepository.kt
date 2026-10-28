package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.DriverProfile
import com.drivemate.showcase.core.model.ChatContact
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.util.AppResult

interface UserDirectoryRepository {
    suspend fun getUserDisplayName(uid: String): AppResult<String?>
    suspend fun getAllDriverDisplayNames(): AppResult<Map<String, String>>
    suspend fun getDriverSopStatuses(): AppResult<List<SopStatus>>
    suspend fun getDriverSopStatus(uid: String): AppResult<SopStatus?>
    suspend fun acceptSop(uid: String, acceptedAtMillis: Long): AppResult<Unit>
    suspend fun getManagedDrivers(managerUid: String): AppResult<List<DriverProfile>>
    suspend fun getUsersByRoles(roles: Set<Role>): AppResult<List<ChatContact>>
}
