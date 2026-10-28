package com.drivemate.showcase.domain.usecase.admin

import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class GetDriverSopStatusesUseCase(
    private val repo: UserDirectoryRepository
) {
    suspend operator fun invoke(): AppResult<List<SopStatus>> =
        repo.getDriverSopStatuses()
}
