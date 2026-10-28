package com.drivemate.showcase.domain.usecase.user

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class GetAllDriverDisplayNamesUseCase(
    private val repo: UserDirectoryRepository
) {
    suspend operator fun invoke(): AppResult<Map<String, String>> =
        repo.getAllDriverDisplayNames()
}
