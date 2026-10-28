package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

class AdminOverrideWorkdayDecisionUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(
        workDayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String
    ): AppResult<Unit> = repo.adminOverrideDecision(
        workDayId = workDayId,
        targetStatus = targetStatus,
        rejectReason = rejectReason,
        overrideReason = overrideReason
    )
}
