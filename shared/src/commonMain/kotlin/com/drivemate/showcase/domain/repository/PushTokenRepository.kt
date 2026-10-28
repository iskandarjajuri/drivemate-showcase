package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.util.AppResult

/**
 * Token FCM privat per perangkat: dokumen `fcm_tokens/{sha256Hex(token)}` (rules: tak ada klien yang bisa membaca).
 * Dipakai Android (`FirebasePushTokenRepository`) dan iOS (`IOSPushTokenRepository`, penulisan dokumennya di Swift);
 * keduanya tidak lagi menulis field legacy `users.fcmToken`. Interface ini sengaja TIDAK mengubah
 * `UserDirectoryRepository`.
 */
interface PushTokenRepository {
    /** Daftarkan/perbarui token perangkat ini untuk [uid]. */
    suspend fun register(uid: String, token: String, appVersion: String): AppResult<Unit>

    /** Hapus dokumen token (id = hash token) milik pengguna yang sedang login. */
    suspend fun unregister(docId: String): AppResult<Unit>
}
