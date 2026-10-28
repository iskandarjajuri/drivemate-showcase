package com.drivemate.showcase.domain.usecase.auth

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository

/**
 * Use case untuk melakukan logout user yang sedang aktif.
 *
 * Layer: DOMAIN (business logic).
 * Mengembalikan [AppResult] agar UI bisa menangani status sukses/gagal tanpa crash.
 *
 * @property repo Repository autentikasi yang diimplementasikan di layer data.
 */
class LogoutUseCase(
    private val repo: AuthRepository
) {
    /**
     * Menjalankan proses logout.
     *
     * @return [AppResult.Success] jika logout berhasil.
     *         [AppResult.Failure] jika terjadi error saat logout.
     */
    suspend operator fun invoke(): AppResult<Unit> = repo.logout()
}