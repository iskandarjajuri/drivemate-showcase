package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.WorkDayRepository

class GetWorkdayDetailUseCase(
    private val repo: WorkDayRepository
) {
    suspend operator fun invoke(id: String): AppResult<WorkDay?> =
        repo.getWorkDayById(id)
}
