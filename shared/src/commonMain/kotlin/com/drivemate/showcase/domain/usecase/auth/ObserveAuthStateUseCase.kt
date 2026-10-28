package com.drivemate.showcase.domain.usecase.auth

import com.drivemate.showcase.domain.repository.AuthRepository

/**
 * Observasi user aktif. null berarti logout.
 */
class ObserveAuthStateUseCase(private val repo: AuthRepository) {
    operator fun invoke() = repo.authState()
}