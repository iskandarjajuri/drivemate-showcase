package com.drivemate.showcase.domain.usecase.auth

import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class ObserveUserSessionUseCase(
    private val repo: AuthRepository
) {
    operator fun invoke(): Flow<UserSession?> = repo.userSessionFlow
}