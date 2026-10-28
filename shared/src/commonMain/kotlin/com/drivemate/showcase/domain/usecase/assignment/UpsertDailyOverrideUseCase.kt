package com.drivemate.showcase.domain.usecase.assignment

import com.drivemate.showcase.core.model.AssignmentReason
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.DriverAssignmentRepository

class UpsertDailyOverrideUseCase(
    private val repo: DriverAssignmentRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        expatUid: String,
        assignedBy: String,
        reason: AssignmentReason,
        epochDay: Int
    ): AppResult<Unit> {
        if (driverUid == expatUid) {
            return AppResult.failure(IllegalArgumentException("driverUid dan expatUid tidak boleh sama"))
        }
        return repo.upsertDailyOverride(driverUid, expatUid, assignedBy, reason, epochDay)
    }
}
