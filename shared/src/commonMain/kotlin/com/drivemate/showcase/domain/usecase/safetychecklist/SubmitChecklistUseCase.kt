package com.drivemate.showcase.domain.usecase.safetychecklist

import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.ChecklistRepository

class SubmitChecklistUseCase(
    private val repo: ChecklistRepository
) {
    suspend operator fun invoke(checklist: Checklist): AppResult<Unit> =
        repo.submitChecklist(checklist)
}
