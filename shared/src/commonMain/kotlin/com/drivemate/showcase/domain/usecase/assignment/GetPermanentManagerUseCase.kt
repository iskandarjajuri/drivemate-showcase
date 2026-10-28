package com.drivemate.showcase.domain.usecase.assignment

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository

class GetPermanentManagerUseCase(
    private val repo: DriverAssignmentRepository
) {
    suspend operator fun invoke(driverUid: String): AppResult<String?> =
        repo.getPermanentManager(driverUid)
}
