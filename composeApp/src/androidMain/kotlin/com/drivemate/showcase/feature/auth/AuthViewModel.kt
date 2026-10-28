package com.drivemate.showcase.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.AuthError
import com.drivemate.showcase.core.util.AuthErrorCode
import com.drivemate.showcase.domain.usecase.auth.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AuthViewModel
 * - 🚫 Tidak melakukan navigasi. Hanya expose AuthUiState ke UI (AuthScreen/AppNavGraph).
 * - ✅ Menjaga single-source-of-truth untuk state login.
 * - ✅ Tambah util: clearError() dan resetToIdle() untuk UX yang bersih.
 */
class AuthViewModel(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private companion object { const val TAG = "DM/AuthVM" }

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state

    fun login(email: String, password: String) {
        val identifier = email.trim()
        val e = if (identifier.contains("@")) identifier.lowercase() else identifier
        val p = password.trim()
        validateInputs(e, p)?.let { error ->
            DMLog.w(TAG, "Login rejected → invalid input (${error.reason})")
            _state.value = AuthUiState.Error(error)
            return
        }

        // Hanya update state; navigasi dilakukan oleh AppNavGraph yang observe Success(User)
        DMLog.i(TAG, "Login attempt → $e")
        _state.value = AuthUiState.Loading

        viewModelScope.launch {
            when (val res = loginUseCase(e, p)) {
                is AppResult.Success -> {
                    // Login sukses, jangan ambil payload (memang Unit).
                    // Navigasi akan terjadi karena SessionVM / userSessionFlow mendeteksi user aktif.
                    DMLog.i(TAG, "Login success")
                    _state.value = AuthUiState.Idle   // atau AuthUiState.SuccessSimple jika kamu punya varian tanpa payload
                }
                is AppResult.Failure -> {
                    DMLog.e(TAG, "Login failed: ${res.throwable.message}", res.throwable)
                    _state.value = AuthUiState.Error(mapAuthError(res.throwable))
                }
            }
        }
    }

    /** Hilangkan error agar UI bisa reset input tanpa men-trigger navigasi dari VM. */
    fun clearError() {
        if (_state.value is AuthUiState.Error) {
            DMLog.d(TAG, "clearError()")
            _state.value = AuthUiState.Idle
        }
    }

    /** Reset ke Idle secara eksplisit (opsional, dipanggil saat screen disposed atau retry). */
    fun resetToIdle() {
        DMLog.d(TAG, "resetToIdle()")
        _state.value = AuthUiState.Idle
    }

    private fun validateInputs(identifier: String, password: String): AuthErrorState? {
        if (identifier.isBlank()) {
            return AuthErrorState(
                reason = AuthErrorReason.EmailEmpty,
                messageRes = R.string.auth_error_email_required,
                emailErrorRes = R.string.auth_error_email_required
            )
        }
        if (password.isBlank()) {
            return AuthErrorState(
                reason = AuthErrorReason.PasswordEmpty,
                messageRes = R.string.auth_error_password_required,
                passwordErrorRes = R.string.auth_error_password_required
            )
        }
        return null
    }

}

/**
 * Classifies a login failure for the UI. Kept as a top-level function so it can be unit-tested.
 * Known limitation: it inspects the exception *message*; see docs/milestones.md (technical debt).
 */
internal fun mapAuthError(t: Throwable?): AuthErrorState {
    if (t is AuthError) {
        return when (t.code) {
            AuthErrorCode.InvalidInput -> AuthErrorState(
                reason = AuthErrorReason.Unknown,
                messageRes = R.string.auth_error_unknown
            )
        }
    }
    val lower = t?.message?.lowercase().orEmpty()
    return when {
        lower.contains("network") || lower.contains("offline") ||
            lower.contains("unreachable") || lower.contains("timeout") ||
            lower.contains("unavailable") ->
            AuthErrorState(
                reason = AuthErrorReason.Offline,
                messageRes = R.string.auth_error_offline
            )
        lower.contains("invalid credential") || lower.contains("invalid credentials") ||
            lower.contains("invalid-credential") || lower.contains("wrong password") ||
            lower.contains("invalid password") || lower.contains("user not found") ||
            // Messages produced by the Firebase SDK / Auth emulator and by production respectively.
            // Matching on message text is brittle; typed errors from the data layer are on the roadmap.
            lower.contains("password is invalid") || lower.contains("auth credential is incorrect") ->
            AuthErrorState(
                reason = AuthErrorReason.InvalidCredentials,
                messageRes = R.string.auth_error_invalid_credentials
            )
        lower.contains("not found") || lower.contains("no user") ||
            lower.contains("not registered") || lower.contains("missing `type`") ->
            AuthErrorState(
                reason = AuthErrorReason.NotRegistered,
                messageRes = R.string.auth_error_not_registered
            )
        lower.contains("invalid `type`") || lower.contains("unknown role") ||
            lower.contains("role user tidak valid") ->
            AuthErrorState(
                reason = AuthErrorReason.InvalidRole,
                messageRes = R.string.auth_error_invalid_role
            )
        else -> AuthErrorState(
            reason = AuthErrorReason.Unknown,
            messageRes = R.string.auth_error_unknown
        )
    }
}
