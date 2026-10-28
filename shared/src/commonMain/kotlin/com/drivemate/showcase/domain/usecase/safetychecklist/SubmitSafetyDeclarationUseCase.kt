package com.drivemate.showcase.domain.usecase.safetychecklist

import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.SafetyRepository

class SubmitSafetyDeclarationUseCase(
    private val repo: SafetyRepository
) {
    suspend operator fun invoke(payload: SafetyDeclaration): AppResult<Unit> =
        repo.submitSafetyDeclaration(payload)
}
