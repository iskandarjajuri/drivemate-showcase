package com.drivemate.showcase.domain.usecase.safetychecklist

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.ChecklistRepository

class GetChecklistStatusUseCase(
    private val repo: ChecklistRepository
) {
    suspend operator fun invoke(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Boolean> = repo.hasChecklist(driverUid, dateEpochDay)
}
