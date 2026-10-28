package com.drivemate.showcase.domain.usecase.safety

import com.drivemate.showcase.core.model.SpeedLimitConfig
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SafetyConfigRepository

class GetSpeedLimitConfigUseCase(
    private val repo: SafetyConfigRepository
) {
    suspend operator fun invoke(): AppResult<SpeedLimitConfig?> = repo.getSpeedLimitConfig()
}
