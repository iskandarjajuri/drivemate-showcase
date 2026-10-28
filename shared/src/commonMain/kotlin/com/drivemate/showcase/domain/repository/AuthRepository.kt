package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.model.User
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraksi untuk autentikasi lintas platform (Android/iOS).
 *
 * Semua operasi network/file disarankan membungkus hasilnya dalam [AppResult]
 * agar aman terhadap exception dan mudah diobservasi di UI layer.
 */
interface AuthRepository {
    val userSessionFlow: Flow<UserSession?>   // <— baru

    /**
     * Login user dengan email & password.
     * @return [AppResult.Success] tanpa payload jika berhasil,
     *         atau [AppResult.Failure] dengan throwable jika gagal.
     */
    suspend fun login(email: String, password: String): AppResult<Unit>

    /**
     * Logout user aktif.
     * @return [AppResult.Success] bila logout sukses, [AppResult.Failure] bila gagal.
     */
    suspend fun logout(): AppResult<Unit>

    /**
     * Mengirim email reset password.
     * @return [AppResult.Success] bila email terkirim, [AppResult.Failure] bila gagal.
     */
    suspend fun sendReset(email: String): AppResult<Unit>

    /**
     * Stream status autentikasi user saat ini.
     * @return Flow yang mengemisi [User] bila login, atau `null` bila logout.
     */
    fun authState(): Flow<User?>

    /**
     * Ambil user aktif (sinkron) bila sudah login.
     * @return [AppResult.Success] dengan [User] atau `null` jika belum login.
     */
    suspend fun currentUser(): AppResult<User?>
}