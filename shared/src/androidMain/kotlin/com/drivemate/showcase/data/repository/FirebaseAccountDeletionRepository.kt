package com.drivemate.showcase.data.repository

import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.domain.account.AccountDeletionFields
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.account.buildAccountDeletionRequestPayload
import com.drivemate.showcase.domain.account.mapAccountDeletionRequest
import com.drivemate.showcase.domain.repository.AccountDeletionRepository

/**
 * Implementasi Android untuk [AccountDeletionRepository] (Blok 5).
 *
 * Hanya BACA dan TULIS satu dokumen `deletion_requests/{uid}`. Tidak menghapus apa pun, dan
 * tidak memanggil `FirebaseAuth.deleteUser` — lihat KDoc interface-nya untuk alasannya.
 */
class FirebaseAccountDeletionRepository(
    private val fs: FirestoreService,
) : AccountDeletionRepository {

    private companion object {
        const val TAG = "DM/AccountDeletion"
    }

    override suspend fun getRequest(uid: String): AppResult<AccountDeletionRequest?> = try {
        val map = fs.getDocumentMap(AccountDeletionFields.COLLECTION, uid)
        AppResult.Success(mapAccountDeletionRequest(map))
    } catch (t: Throwable) {
        DMLog.e(TAG, "getRequest gagal: ${t.message}", t)
        AppResult.Failure(t)
    }

    override suspend fun submitRequest(
        uid: String,
        reason: String?,
        nowMillis: Long,
    ): AppResult<Unit> = try {
        val payload = buildAccountDeletionRequestPayload(uid, nowMillis, reason)
        // merge = false DISENGAJA. Permintaan ulang setelah yang lama ditangani harus
        // MENGGANTI dokumen, bukan menempel di atasnya: `merge = true` akan meninggalkan
        // `handledByUid`/`handledAtMillis` lama, sehingga permintaan baru lahir seolah sudah
        // ditangani. Rules menolak bentuk itu, jadi merge = true juga akan gagal — tapi gagal
        // karena izin, dengan pesan yang menyesatkan.
        fs.setDocument("${AccountDeletionFields.COLLECTION}/$uid", payload, merge = false)
        DMLog.i(TAG, "permintaan penghapusan akun tercatat (hasReason=${reason != null})")
        AppResult.Success(Unit)
    } catch (t: Throwable) {
        DMLog.e(TAG, "submitRequest gagal: ${t.message}", t)
        AppResult.Failure(t)
    }
}
