package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.sha256Hex
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.repository.PushTokenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Menulis `fcm_tokens/{sha256(token)}` lewat Firebase SDK langsung (butuh `serverTimestamp` yang divalidasi rules:
 * `updatedAt == request.time`). Hanya kode status galat yang dicatat; token TIDAK pernah dicatat.
 */
class FirebasePushTokenRepository(private val db: FirebaseFirestore) : PushTokenRepository {

    override suspend fun register(uid: String, token: String, appVersion: String): AppResult<Unit> = guarded("register") {
        val doc = mapOf(
            "uid" to uid,
            "token" to token,
            "platform" to "android",
            "appVersion" to appVersion.take(32),
            "source" to "client",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection(COLLECTION).document(sha256Hex(token)).set(doc).await()
    }

    override suspend fun unregister(docId: String): AppResult<Unit> = guarded("unregister") {
        db.collection(COLLECTION).document(docId).delete().await()
    }

    private suspend fun guarded(op: String, block: suspend () -> Unit): AppResult<Unit> = try {
        withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) { block() }
        AppResult.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        DMLog.w(TAG, "$op gagal: ${t.javaClass.simpleName}")
        AppResult.failure(t)
    }

    private companion object {
        const val COLLECTION = "fcm_tokens"
        const val TAG = "DM/PushToken"
    }
}
