package com.drivemate.showcase.domain.usecase.user

import com.drivemate.showcase.core.model.DriverProfile
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class GetManagedDriversUseCase(
    private val repo: UserDirectoryRepository
) {
    suspend operator fun invoke(managerUid: String): AppResult<List<DriverProfile>> =
        repo.getManagedDrivers(managerUid)
}
