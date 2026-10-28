package com.drivemate.showcase.domain.usecase.assignment

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository

class SetPermanentManagerUseCase(
    private val repo: DriverAssignmentRepository
) {
    suspend operator fun invoke(driverUid: String, expatUid: String): AppResult<Unit> =
        repo.setPermanentManager(driverUid, expatUid)
}
