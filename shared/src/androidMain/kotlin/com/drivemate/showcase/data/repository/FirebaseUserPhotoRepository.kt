package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestoreException
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.error.UserPhotoError
import com.drivemate.showcase.domain.profile.UserPhotoFields
import com.drivemate.showcase.domain.repository.UserPhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

/**
 * Implementasi Android untuk [UserPhotoRepository] (PR-E).
 *
 * Membaca/menulis/menghapus SATU dokumen `user_photos/{uid}`. Firestore Android tidak
 * menerima `ByteArray` mentah, jadi dibungkus [Blob] di sini — lapis domain tetap
 * memakai `ByteArray` biasa.
 */
class FirebaseUserPhotoRepository(
    private val fs: FirestoreService,
) : UserPhotoRepository {

    private companion object {
        const val TAG = "DM/UserPhoto"
    }

    override suspend fun get(uid: String): AppResult<ByteArray?> = try {
        val map = fs.getDocumentMap(UserPhotoFields.COLLECTION, uid)
        AppResult.Success((map?.get(UserPhotoFields.JPEG) as? Blob)?.toBytes())
    } catch (t: Throwable) {
        failure("get", t)
    }

    override suspend fun save(uid: String, jpeg: ByteArray, nowMillis: Long): AppResult<Unit> = try {
        val payload = mapOf(
            UserPhotoFields.JPEG to Blob.fromBytes(jpeg),
            UserPhotoFields.UPDATED_AT_MILLIS to nowMillis,
        )
        // merge = false: dokumen diganti utuh, sehingga rules menilai bentuk akhirnya.
        fs.setDocument("${UserPhotoFields.COLLECTION}/$uid", payload, merge = false)
        DMLog.i(TAG, "foto profil tersimpan (${jpeg.size} byte)")
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        failure("save", t)
    }

    override suspend fun delete(uid: String): AppResult<Unit> = try {
        fs.deleteDocument("${UserPhotoFields.COLLECTION}/$uid")
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        failure("delete", t)
    }

    private fun failure(op: String, t: Throwable): AppResult.Failure {
        // Pembatalan coroutine bukan galat; teruskan agar scope pemanggil ikut berhenti.
        if (t is CancellationException && t !is TimeoutCancellationException) throw t
        DMLog.e(TAG, "$op gagal: ${t.message}", t)
        return AppResult.Failure(mapUserPhotoFailure(t))
    }
}

/** Memetakan galat SDK/jaringan ke [UserPhotoError]. Murni, diuji lewat unit test. */
internal fun mapUserPhotoFailure(t: Throwable): UserPhotoError = when {
    t is UserPhotoError -> t
    t is FirebaseFirestoreException &&
        t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> UserPhotoError.PermissionDenied
    t is FirebaseFirestoreException && t.code in NETWORK_CODES -> UserPhotoError.Network
    t is TimeoutCancellationException || t is IOException -> UserPhotoError.Network
    else -> UserPhotoError.Unknown(t)
}

private val NETWORK_CODES = setOf(
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
    FirebaseFirestoreException.Code.CANCELLED,
)
