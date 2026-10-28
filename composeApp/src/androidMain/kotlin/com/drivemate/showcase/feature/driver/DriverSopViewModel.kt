package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.user.AcceptDriverSopUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

private const val TAG = "DM/DriverSop"
private const val FIRESTORE_DATABASE_ID = "drivemate-database"

class DriverSopViewModel(
    private val authRepository: AuthRepository,
    private val getSopStatus: GetDriverSopStatusUseCase,
    private val acceptSop: AcceptDriverSopUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DriverSopUiState())
    val state: StateFlow<DriverSopUiState> = _state.asStateFlow()

    init {
        loadStatus()
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessageRes = null, errorMessageRes = null)
    }

    fun loadStatus() = viewModelScope.launch {
        if (_state.value.loading) return@launch
        _state.value = _state.value.copy(loading = true, errorMessageRes = null)

        try {
            val user = currentUser() ?: run {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessageRes = R.string.driver_error_user_not_logged_in
                )
                return@launch
            }

            val role = user.second ?: "-"
            val dateEpochDay = TimeUtil.todayEpochDay(TimeZone.of("Asia/Jakarta")).toInt()
            DMLog.d(
                TAG,
                "[DRIVER_SOP] status_load authUid=${user.first} role=$role " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )

            val sop = when (val res = getSopStatus(user.first)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }

            _state.value = _state.value.copy(
                loading = false,
                sopAccepted = sop?.sopAccepted,
                sopAcceptedAtMillis = sop?.sopAcceptedAtMillis
            )

            DMLog.i(
                TAG,
                "[DRIVER_SOP] status_result authUid=${user.first} role=$role " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(
                TAG,
                "[DRIVER_SOP] status_result authUid=unknown role=- dateEpochDay=- success=false " +
                    "errorClass=${t::class.simpleName ?: "Throwable"} reason=${t.message ?: "unknown"} " +
                    "dbId=$FIRESTORE_DATABASE_ID",
                t
            )
            _state.value = _state.value.copy(
                loading = false,
                errorMessageRes = R.string.driver_error_sop_status_failed
            )
        }
    }

    fun saveAcknowledgement() = viewModelScope.launch {
        if (_state.value.submitting) return@launch
        _state.value = _state.value.copy(submitting = true, errorMessageRes = null)

        try {
            val user = currentUser() ?: run {
                _state.value = _state.value.copy(
                    submitting = false,
                    errorMessageRes = R.string.driver_error_user_not_logged_in
                )
                return@launch
            }

            val now = TimeUtil.nowEpochMillis()
            val dateEpochDay = TimeUtil.todayEpochDay(TimeZone.of("Asia/Jakarta")).toInt()
            DMLog.d(
                TAG,
                "[DRIVER_SOP] save_start authUid=${user.first} role=${user.second ?: "-"} " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )

            when (val res = acceptSop(user.first, now)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> throw res.throwable
            }

            _state.value = _state.value.copy(
                submitting = false,
                successMessageRes = R.string.driver_success_sop_saved,
                sopAccepted = true,
                sopAcceptedAtMillis = now
            )

            DMLog.i(
                TAG,
                "[DRIVER_SOP] save_result authUid=${user.first} role=${user.second ?: "-"} " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(
                TAG,
                "[DRIVER_SOP] save_result authUid=unknown role=- dateEpochDay=- success=false " +
                    "errorClass=${t::class.simpleName ?: "Throwable"} reason=${t.message ?: "unknown"} " +
                    "dbId=$FIRESTORE_DATABASE_ID",
                t
            )
            _state.value = _state.value.copy(
                submitting = false,
                errorMessageRes = R.string.driver_error_sop_save_failed
            )
        }
    }

    private suspend fun currentUser(): Pair<String, String?>? = when (val res = authRepository.currentUser()) {
        is AppResult.Success -> res.data?.let { it.uid to it.role.toFirestore() }
        is AppResult.Failure -> null
    }
}

data class DriverSopUiState(
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val sopAccepted: Boolean? = null,
    val sopAcceptedAtMillis: Long? = null,
    @param:StringRes val errorMessageRes: Int? = null,
    @param:StringRes val successMessageRes: Int? = null
)
