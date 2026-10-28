package com.drivemate.showcase.domain.usecase.user

import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class GetDriverSopStatusUseCase(
    private val repo: UserDirectoryRepository
) {
    suspend operator fun invoke(uid: String): AppResult<SopStatus?> =
        repo.getDriverSopStatus(uid)
}
