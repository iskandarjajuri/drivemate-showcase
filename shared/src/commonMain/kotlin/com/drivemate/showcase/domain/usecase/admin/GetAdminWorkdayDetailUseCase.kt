package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AdminDataRepository

class GetAdminWorkdayDetailUseCase(
    private val repo: AdminDataRepository
) {
    suspend operator fun invoke(id: String): AppResult<WorkDay?> =
        repo.getWorkdayById(id)
}
