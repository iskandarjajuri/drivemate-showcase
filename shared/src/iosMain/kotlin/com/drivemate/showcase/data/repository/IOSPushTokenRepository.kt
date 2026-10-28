package com.drivemate.showcase.data.repository

import com.drivemate.showcase.bridge.IOSPlatformBridgeRegistry
import com.drivemate.showcase.bridge.IOSPushTokenPlatformBridge
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.PushTokenRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implementasi iOS untuk [PushTokenRepository] — padanan `FirebasePushTokenRepository` Android.
 * Menulis `fcm_tokens/{sha256Hex(token)}` lewat [IOSPushTokenPlatformBridge] (Swift), BUKAN
 * field legacy `users.fcmToken`.
 *
 * Bridge yang belum terpasang menghasilkan `Failure`, bukan exception: pendaftaran token
 * non-fatal by design (lihat KDoc `IOSPushTokenBridge`), jadi kekurangan sisi Swift tidak boleh
 * menjatuhkan resolusi sesi. Token TIDAK pernah dicatat; hanya pesan galat dari Swift.
 */
class IOSPushTokenRepository : PushTokenRepository {

    override suspend fun register(uid: String, token: String, appVersion: String): AppResult<Unit> {
        if (uid.isBlank()) {
            return AppResult.failure(IllegalArgumentException("register called with blank uid"))
        }
        if (token.isBlank()) {
            return AppResult.failure(IllegalArgumentException("register called with blank token"))
        }
        val bridge = IOSPlatformBridgeRegistry.pushTokenBridge ?: return notInstalled("register")
        return suspendCancellableCoroutine { cont ->
            bridge.registerPushToken(uid, token, appVersion.take(MAX_APP_VERSION)) { error ->
                if (!cont.isCompleted) cont.resume(toResult("register", error))
            }
        }
    }

    override suspend fun unregister(docId: String): AppResult<Unit> {
        if (docId.isBlank()) {
            return AppResult.failure(IllegalArgumentException("unregister called with blank docId"))
        }
        val bridge = IOSPlatformBridgeRegistry.pushTokenBridge ?: return notInstalled("unregister")
        return suspendCancellableCoroutine { cont ->
            bridge.unregisterPushToken(docId) { error ->
                if (!cont.isCompleted) cont.resume(toResult("unregister", error))
            }
        }
    }

    private fun toResult(op: String, error: String?): AppResult<Unit> =
        if (error != null) {
            DMLog.w(TAG, "$op gagal: $error")
            AppResult.failure(IllegalStateException(error))
        } else {
            AppResult.success(Unit)
        }

    private fun notInstalled(op: String): AppResult<Unit> {
        DMLog.w(TAG, "$op gagal: IOSPushTokenPlatformBridge belum terpasang")
        return AppResult.failure(IllegalStateException("IOSPushTokenPlatformBridge is not installed"))
    }

    private companion object {
        // Rules `validFcmTokenDoc`: appVersion ≤ 32 karakter (sama dengan Android).
        const val MAX_APP_VERSION = 32
        const val TAG = "DM/PushToken"
    }
}
