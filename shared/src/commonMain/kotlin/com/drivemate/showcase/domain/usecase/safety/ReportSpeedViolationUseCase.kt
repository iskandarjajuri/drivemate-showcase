package com.drivemate.showcase.domain.usecase.safety

import com.drivemate.showcase.core.model.SpeedViolation
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SpeedViolationRepository

class ReportSpeedViolationUseCase(
    private val repo: SpeedViolationRepository
) {
    suspend operator fun invoke(violation: SpeedViolation): AppResult<Unit> =
        repo.reportViolation(violation)
}
