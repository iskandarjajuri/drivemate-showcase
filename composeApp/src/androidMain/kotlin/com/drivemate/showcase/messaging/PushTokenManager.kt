package com.drivemate.showcase.messaging

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.sha256Hex
import com.drivemate.showcase.domain.repository.PushTokenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Sumber token perangkat (FirebaseMessaging) — diabstraksi agar urutan operasi teruji. */
interface PushTokenSource {
    suspend fun current(): String
    suspend fun delete()
}

/** Penyimpanan lokal: id dokumen token terakhir dan penanda rotasi paksa sekali. */
interface PushTokenStore {
    var lastDocId: String?
    var rotationDone: Boolean
    /** `deleteToken()` belum berhasil (mis. offline saat logout): diulang pada start berikutnya. */
    var pendingDeleteToken: Boolean
}

class FirebasePushTokenSource(private val messaging: FirebaseMessaging) : PushTokenSource {
    override suspend fun current(): String = messaging.token.await()
    override suspend fun delete() { messaging.deleteToken().await() }
}

class SharedPrefsPushTokenStore(private val context: Context) : PushTokenStore {
    private val prefs get() = context.getSharedPreferences("push_token", Context.MODE_PRIVATE)
    override var lastDocId: String?
        get() = prefs.getString("last_doc_id", null)
        set(value) = prefs.edit { if (value == null) remove("last_doc_id") else putString("last_doc_id", value) }
    override var rotationDone: Boolean
        get() = prefs.getBoolean("rotation_v1_done", false)
        set(value) = prefs.edit { putBoolean("rotation_v1_done", value) }
    override var pendingDeleteToken: Boolean
        get() = prefs.getBoolean("pending_delete_token", false)
        set(value) = prefs.edit { putBoolean("pending_delete_token", value) }
}

/**
 * Satu pintu siklus token FCM perangkat (tinjauan Opus E-0):
 * - login/sesi mulai: sekali per instalasi build ini token lama DIBATALKAN (`deleteToken`; token lama bisa sudah terbaca
 *   lewat dokumen `users` lama), lalu token baru didaftarkan; token yang berotasi → dokumen lama dihapus;
 * - logout: [beforeSignOut] hapus dokumen (masih terautentikasi) → sign-out oleh pemanggil → [afterSignOut] `deleteToken()`.
 *   `deleteToken` SETELAH sign-out supaya `onNewToken` tak mendaftarkan token baru atas nama akun yang keluar;
 * - semua langkah best-effort: kegagalan dicatat (tanpa token) dan tidak pernah menghalangi login/logout.
 */
class PushTokenManager(
    private val repo: PushTokenRepository,
    private val source: PushTokenSource,
    private val store: PushTokenStore,
    private val appVersion: String,
    /** Dibersihkan saat sign-out: notifikasi yang sudah tampil dan rute push tertunda milik akun yang keluar. */
    private val onSignedOut: () -> Unit = {}
) {
    suspend fun onSessionStarted(uid: String) {
        try {
            // Logout sebelumnya gagal mencabut token (offline): token yang sama masih tercatat atas nama akun lama dan rules
            // melarang akun ini mengambil dokumen itu, jadi token diganti dulu (tinjauan keamanan #1/#4).
            retryPendingRevocation()
            if (!store.rotationDone) {
                // Rotasi paksa sekali. Gagal (mis. offline) TIDAK boleh menghalangi pendaftaran; diulang di sesi berikutnya.
                try {
                    source.delete()
                    store.rotationDone = true
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    DMLog.w(TAG, "rotasi token gagal; diulang: ${t.javaClass.simpleName}")
                }
            }
            register(uid, source.current())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.w(TAG, "onSessionStarted gagal: ${t.javaClass.simpleName}")
        }
    }

    /** Token berotasi saat sesi berjalan (`onNewToken`). */
    suspend fun onNewToken(uid: String, token: String) = register(uid, token)

    suspend fun beforeSignOut() {
        val id = store.lastDocId ?: return
        when (repo.unregister(id)) {
            is AppResult.Success -> DMLog.i(TAG, "dokumen token dihapus sebelum logout")
            is AppResult.Failure -> DMLog.w(TAG, "hapus dokumen token ditolak/gagal (best-effort)")
        }
    }

    /** Aman dipanggil berulang dan untuk sign-out paksa (tanpa [beforeSignOut]). */
    suspend fun afterSignOut() {
        try {
            onSignedOut()
        } catch (t: Throwable) {
            DMLog.w(TAG, "pembersihan notifikasi gagal: ${t.javaClass.simpleName}")
        }
        // Ditandai SEBELUM mencoba: bila `deleteToken` gagal (offline) token lama tetap hidup, jadi harus diulang
        // (temuan review keamanan: tanpa ini pencabutan token saat logout gagal diam-diam).
        store.pendingDeleteToken = true
        try {
            source.delete()
            store.pendingDeleteToken = false
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.w(TAG, "deleteToken gagal; diulang pada start berikutnya: ${t.javaClass.simpleName}")
        }
        store.lastDocId = null
    }

    /** Dipanggil saat aplikasi mulai (tanpa melihat sesi): mengulang pencabutan token yang tertunda. */
    suspend fun retryPendingRevocation() = revocationLock.withLock {
        // Dikunci: dipanggil dari init SessionViewModel DAN onSessionStarted; tanpa kunci `deleteToken` bisa terpanggil dua
        // kali dan membatalkan token yang baru didaftarkan (tinjauan Opus E-1).
        if (!store.pendingDeleteToken) return@withLock
        try {
            source.delete()
            store.pendingDeleteToken = false
            DMLog.i(TAG, "pencabutan token tertunda berhasil")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.w(TAG, "pencabutan token tertunda masih gagal: ${t.javaClass.simpleName}")
        }
    }

    private val revocationLock = Mutex()

    private suspend fun register(uid: String, token: String) {
        val id = sha256Hex(token)
        if (repo.register(uid, token, appVersion) is AppResult.Success) {
            val old = store.lastDocId
            if (old != null && old != id) repo.unregister(old)
            store.lastDocId = id
            DMLog.i(TAG, "token terdaftar")
        } else {
            DMLog.w(TAG, "pendaftaran token gagal (best-effort)")
        }
    }

    private companion object {
        const val TAG = "DM/PushToken"
    }
}
