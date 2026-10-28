package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle

/**
 * Pendaftaran dan pencabutan token FCM iOS lewat [PushTokenRepository] (`fcm_tokens`), padanan
 * `PushTokenManager` Android. Menggantikan `IOSUserDirectoryBridge.registerFcmToken` yang menulis
 * field legacy `users.fcmToken`.
 *
 * **Sengaja non-fatal dan tidak memblokir**, mengikuti `SessionViewModel.registerFcmToken`
 * Android yang hanya `DMLog.w` saat gagal: kegagalan pendaftaran tidak boleh menjadi langkah
 * pemblokir di resolusi sesi. Swift (`IOSAppState.flushPendingFcmToken`) menyimpan token dan
 * mencoba ulang selama `registered == false`.
 *
 * Rotasi paksa sekali per instalasi, state `lastDocId`/`rotationDone`/`pendingDeleteToken`, dan
 * hash id dokumen ada di Swift (T5/T6), bukan di sini.
 */
internal class IOSPushTokenBridge(
    private val scope: CoroutineScope,
    private val pushTokenRepository: PushTokenRepository,
    private val currentSession: () -> UserSession?,
    private val appVersion: () -> String = ::bundleShortVersion
) {
    fun registerFcmToken(token: String, completion: (Boolean, String?) -> Unit) {
        val uid = currentSession()?.uid
        if (uid.isNullOrBlank()) {
            // Belum login: bukan error. Token akan didaftarkan lagi setelah sesi terselesaikan.
            completion(false, null)
            return
        }
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            completion(false, "Empty FCM token")
            return
        }
        scope.launch {
            when (val result = pushTokenRepository.register(uid, trimmed, appVersion())) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    /**
     * Hapus dokumen `fcm_tokens/{docId}`. [docId] adalah sha256 hex token yang dihitung Swift
     * (`lastDocId`), bukan token mentah. Harus dipanggil SEBELUM `signOut` — rules hanya
     * mengizinkan pemilik dokumen yang masih login menghapusnya.
     */
    fun unregisterFcmToken(docId: String, completion: (Boolean, String?) -> Unit) {
        val trimmed = docId.trim()
        if (trimmed.isEmpty()) {
            completion(false, "Empty token document id")
            return
        }
        scope.launch {
            when (val result = pushTokenRepository.unregister(trimmed)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }
}

/** `CFBundleShortVersionString` aplikasi; string kosong bila tidak terbaca (rules mengizinkan). */
private fun bundleShortVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
