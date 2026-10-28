package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.util.AppResult

/**
 * Foto profil driver (PR-E). Satu dokumen per uid; isinya JPEG yang sudah diolah klien.
 *
 * Kegagalan dibungkus [com.drivemate.showcase.domain.error.UserPhotoError]
 * di dalam `AppResult.Failure`, bukan dilempar.
 */
interface UserPhotoRepository {

    /** JPEG milik [uid], atau `null` bila belum ada foto. */
    suspend fun get(uid: String): AppResult<ByteArray?>

    /** Menimpa foto [uid]. [jpeg] sudah divalidasi ukuran oleh pemanggil. */
    suspend fun save(uid: String, jpeg: ByteArray, nowMillis: Long): AppResult<Unit>

    suspend fun delete(uid: String): AppResult<Unit>
}
