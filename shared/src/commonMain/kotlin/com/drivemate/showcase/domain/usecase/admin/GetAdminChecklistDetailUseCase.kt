package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.ChecklistDetail
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminChecklistDetailUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(id: String): AppResult<ChecklistDetail?> =
        repo.getChecklistDetail(id)
}
