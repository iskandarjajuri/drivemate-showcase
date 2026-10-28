package com.drivemate.showcase.domain.usecase.safetychecklist

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SafetyRepository

class GetSafetyDeclarationStatusUseCase(
    private val repo: SafetyRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Boolean> = repo.hasSafetyDeclaration(driverUid, dateEpochDay)
}
