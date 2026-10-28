package com.drivemate.showcase.domain.usecase.profile

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.error.UserPhotoError
import com.drivemate.showcase.domain.profile.validateProfilePhotoSize
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.UserPhotoRepository

/**
 * Uid diambil dari sesi, **tidak pernah** dari parameter: yang bisa diganti hanyalah foto
 * milik penelepon sendiri. Rules menolak yang lain juga, tapi lapis pertama sebaiknya
 * tidak pernah menyusun permintaan itu.
 */
private suspend fun AuthRepository.currentUidOrFailure(): AppResult<String> =
    when (val session = currentUser()) {
        is AppResult.Failure -> AppResult.Failure(session.throwable)
        is AppResult.Success -> {
            val uid = session.data?.uid
            if (uid.isNullOrBlank()) AppResult.Failure(UserPhotoError.NotSignedIn)
            else AppResult.Success(uid)
        }
    }

/** Membaca foto profil pengguna yang sedang login; `null` bila belum ada. */
class GetProfilePhotoUseCase(
    private val authRepo: AuthRepository,
    private val repo: UserPhotoRepository,
) {
    suspend operator fun invoke(): AppResult<ByteArray?> =
        when (val uid = authRepo.currentUidOrFailure()) {
            is AppResult.Failure -> uid
            is AppResult.Success -> repo.get(uid.data)
        }
}

/** Menyimpan foto profil; menolak sebelum menyentuh jaringan bila ukuran tidak sah. */
class SaveProfilePhotoUseCase(
    private val authRepo: AuthRepository,
    private val repo: UserPhotoRepository,
) {
    suspend operator fun invoke(jpeg: ByteArray, nowMillis: Long): AppResult<Unit> {
        validateProfilePhotoSize(jpeg.size)?.let { return AppResult.Failure(it) }
        return when (val uid = authRepo.currentUidOrFailure()) {
            is AppResult.Failure -> uid
            is AppResult.Success -> repo.save(uid.data, jpeg, nowMillis)
        }
    }
}

class DeleteProfilePhotoUseCase(
    private val authRepo: AuthRepository,
    private val repo: UserPhotoRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> =
        when (val uid = authRepo.currentUidOrFailure()) {
            is AppResult.Failure -> uid
            is AppResult.Success -> repo.delete(uid.data)
        }
}
