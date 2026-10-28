package com.drivemate.showcase.feature.session

import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.usecase.auth.LogoutUseCase
import com.drivemate.showcase.domain.usecase.auth.ObserveUserSessionUseCase
import com.drivemate.showcase.messaging.PushTokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "DM/SessionVM"

/**
 * Session VM versi UID-first:
 * - Sumber kebenaran: userSession (uid + email + role[nullable sampai resolved])
 * - UI melakukan navigasi berdasar session.role saat non-null (lihat SessionGuard).
 *
 * Juga bertanggung jawab meregistrasi FCM token saat uid berubah jadi non-null —
 * ini mencakup login sukses maupun app start dengan sesi yang sudah persisted,
 * karena keduanya sama-sama muncul sebagai transisi `session.uid` di sini.
 * Berlaku untuk semua role (bukan cuma driver): R1 mengirim push ke expat, jadi
 * expat juga harus punya fcmToken ter-registrasi.
 */
class SessionViewModel(
    observeUserSession: ObserveUserSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val pushTokenManager: PushTokenManager,
    private val profilePhotoStore: com.drivemate.showcase.feature.settings.photo.ProfilePhotoStore,
    private val profilePhotoLocalStore: com.drivemate.showcase.feature.settings.photo.ProfilePhotoLocalStore
) : ViewModel() {

    private val upstream = observeUserSession() // Flow<UserSession?>
    private val upstreamState = upstream as? StateFlow<UserSession?>

    val session: StateFlow<UserSession?> =
        upstream
            .onStart { DMLog.i(TAG, "start observing userSession (UID-first)") }
            .distinctUntilChanged { old, new ->
                old?.uid == new?.uid && old?.role == new?.role
            }
            .catch { t ->
                DMLog.e(TAG, "userSession stream error: ${t.message}", t)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = upstreamState?.value // kalau null, tetap valid
            )

    /** Menandai kapan stream pertama kali emit (apa pun nilainya). */
    val isReady: StateFlow<Boolean> =
        session
            .map { true }
            .onStart { emit(false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )

    init {
        // Ulangi pencabutan token yang tertunda (mis. logout saat offline) sekali per start aplikasi, tanpa menunggu login.
        viewModelScope.launch { pushTokenManager.retryPendingRevocation() }
        var hadSession = false
        session
            .map { it?.uid }
            .distinctUntilChanged()
            .onEach { uid ->
                if (uid != null) {
                    hadSession = true
                    pushTokenManager.onSessionStarted(uid)
                } else if (hadSession) {
                    // Sesi berakhir tanpa logout dari UI (sesi tak sah / sign-out paksa): token perangkat tetap dibatalkan.
                    hadSession = false
                    pushTokenManager.afterSignOut()
                }
            }
            .launchIn(viewModelScope)
    }

    suspend fun logout(): AppResult<Unit> {
        DMLog.i(TAG, "logout() called")
        // Urutan WAJIB (tinjauan Opus): hapus dokumen token (masih terautentikasi) -> sign-out -> deleteToken().
        // Best-effort dengan batas 3 dtk; kegagalan tidak menghalangi logout.
        try {
            withTimeoutOrNull(3_000) { pushTokenManager.beforeSignOut() }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.w(TAG, "beforeSignOut gagal: ${t.javaClass.simpleName}")
        }
        // Foto profil (memori + salinan lokal) dibuang agar tidak bocor ke akun berikutnya.
        profilePhotoStore.clear()
        withContext(Dispatchers.IO) { profilePhotoLocalStore.clearAll() }
        val result = logoutUseCase()
        pushTokenManager.afterSignOut()
        return result
    }
}