package com.drivemate.showcase.domain.usecase.auth

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.AuthError
import com.drivemate.showcase.core.util.AuthErrorCode
import com.drivemate.showcase.domain.repository.AuthRepository

/**
 * Menjalankan proses login berbasis email & password.
 * - Orkestrasi ke AuthRepository.
 * - Setelah login sukses, SessionViewModel (userSessionFlow) yang akan resolve role dan menavigasi.
 */
class LoginUseCase(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> {
        val e = email.trim().lowercase()
        val p = password.trim()
        if (e.isEmpty() || p.isEmpty()) {
            return AppResult.failure(AuthError(AuthErrorCode.InvalidInput))
        }

        return repo.login(e, p)
    }
}
