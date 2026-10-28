package com.drivemate.showcase.feature.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.account.AccountDeletionError
import com.drivemate.showcase.domain.account.AccountDeletionRequest
import com.drivemate.showcase.domain.usecase.account.GetAccountDeletionRequestUseCase
import com.drivemate.showcase.domain.usecase.account.RequestAccountDeletionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountSettingsUiState(
    val loading: Boolean = true,
    val request: AccountDeletionRequest? = null,
    val submitting: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
)

/**
 * Umpan balik sekali-pakai, TERPISAH dari [AccountSettingsUiState].
 *
 * Digabung ke state utama, ia akan tersapu tiap kali `load()` menimpa state — bug persis itu
 * pernah membuat dialog jejak keputusan di layar admin tidak pernah menutup meski tulisannya
 * berhasil (Blok 4.2). Dipisah supaya pemuatan ulang tidak menghapus pesan yang belum sempat
 * dibaca.
 */
data class AccountSettingsFeedback(
    @param:StringRes val successRes: Int? = null,
    @param:StringRes val errorRes: Int? = null,
)

/**
 * Layar akun — memuat jalur penghapusan akun yang dituntut toko aplikasi (Blok 5, PRD §20).
 *
 * ViewModel ini **tidak bisa** menghapus apa pun; ia hanya mencatat permintaan. Itu bukan
 * keterbatasan sementara melainkan keputusan yang ditulis di [RequestAccountDeletionUseCase]
 * dan di rules: akun dimiliki perusahaan, dan catatan yang menempel padanya adalah bukti upah.
 */
class AccountSettingsViewModel(
    private val getRequest: GetAccountDeletionRequestUseCase,
    private val requestDeletion: RequestAccountDeletionUseCase,
    private val nowMillis: () -> Long = { TimeUtil.nowEpochMillis() },
) : ViewModel() {

    private companion object {
        const val TAG = "DM/AccountSettings"
    }

    private val _state = MutableStateFlow(AccountSettingsUiState())
    val state: StateFlow<AccountSettingsUiState> = _state.asStateFlow()

    private val _feedback = MutableStateFlow(AccountSettingsFeedback())
    val feedback: StateFlow<AccountSettingsFeedback> = _feedback.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errorMessageRes = null)
            when (val res = getRequest()) {
                is AppResult.Success -> _state.value = _state.value.copy(
                    loading = false,
                    request = res.data,
                )
                is AppResult.Failure -> {
                    DMLog.e(TAG, "load gagal: ${res.throwable.message}", res.throwable)
                    _state.value = _state.value.copy(
                        loading = false,
                        errorMessageRes = R.string.account_deletion_error_load,
                    )
                }
            }
        }
    }

    fun submit(reason: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            when (val outcome = requestDeletion(reason, nowMillis())) {
                is RequestAccountDeletionUseCase.Outcome.Submitted -> {
                    _feedback.value = AccountSettingsFeedback(
                        successRes = R.string.account_deletion_submitted,
                    )
                    _state.value = _state.value.copy(submitting = false)
                    // Memuat ulang supaya kartu status menampilkan permintaan yang baru saja
                    // dibuat; `feedback` tidak ikut tersapu karena alirannya terpisah.
                    load()
                }
                is RequestAccountDeletionUseCase.Outcome.Invalid -> {
                    _state.value = _state.value.copy(submitting = false)
                    _feedback.value = AccountSettingsFeedback(errorRes = errorRes(outcome.error))
                }
                is RequestAccountDeletionUseCase.Outcome.Failed -> {
                    DMLog.e(TAG, "submit gagal: ${outcome.throwable.message}", outcome.throwable)
                    _state.value = _state.value.copy(submitting = false)
                    _feedback.value = AccountSettingsFeedback(
                        errorRes = R.string.account_deletion_error_submit,
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _feedback.value = AccountSettingsFeedback()
    }

    @StringRes
    private fun errorRes(error: AccountDeletionError): Int = when (error) {
        AccountDeletionError.NOT_SIGNED_IN -> R.string.account_deletion_error_signed_out
        AccountDeletionError.ALREADY_PENDING -> R.string.account_deletion_error_already_pending
        AccountDeletionError.REASON_TOO_LONG -> R.string.account_deletion_error_reason_too_long
    }
}
