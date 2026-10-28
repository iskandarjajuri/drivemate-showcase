package com.drivemate.showcase.domain.usecase.user

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class GetUserDisplayNameUseCase(
    private val repo: UserDirectoryRepository
) {
    suspend operator fun invoke(uid: String): AppResult<String?> =
        repo.getUserDisplayName(uid)
}
