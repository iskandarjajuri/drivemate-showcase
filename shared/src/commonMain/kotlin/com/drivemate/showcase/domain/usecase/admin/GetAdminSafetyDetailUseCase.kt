package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.SafetyDetail
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminSafetyDetailUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(id: String): AppResult<SafetyDetail?> =
        repo.getSafetyDetail(id)
}
