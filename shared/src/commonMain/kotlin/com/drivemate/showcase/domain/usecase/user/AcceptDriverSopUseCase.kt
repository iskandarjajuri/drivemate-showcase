package com.drivemate.showcase.domain.usecase.user

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.UserDirectoryRepository

class AcceptDriverSopUseCase(
    private val repo: UserDirectoryRepository
) {
    suspend operator fun invoke(uid: String, acceptedAtMillis: Long): AppResult<Unit> =
        repo.acceptSop(uid, acceptedAtMillis)
}
