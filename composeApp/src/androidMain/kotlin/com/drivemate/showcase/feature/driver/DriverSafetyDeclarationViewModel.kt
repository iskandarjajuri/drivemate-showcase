package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.safetychecklist.GetSafetyDeclarationStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitSafetyDeclarationUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

private const val TAG = "DM/DriverSafety"
private const val FIRESTORE_DATABASE_ID = "drivemate-database"

class DriverSafetyDeclarationViewModel(
    private val submitSafetyDeclaration: SubmitSafetyDeclarationUseCase,
    private val getSafetyStatus: GetSafetyDeclarationStatusUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DriverSafetyUiState())
    val state: StateFlow<DriverSafetyUiState> = _state.asStateFlow()

    init {
        refreshStatus()
    }

    fun updateFitToWork(value: Boolean) = updateForm { it.copy(fitToWork = value) }
    fun updateSleepHours(value: String) = updateForm { it.copy(sleepHours = value) }
    fun updateNoAlcoholOrDrugs(value: Boolean) = updateForm { it.copy(noAlcoholOrDrugs = value) }
    fun updateNoSevereStress(value: Boolean) = updateForm { it.copy(noSevereStress = value) }
    fun updateWillFollowSpeedLimit(value: Boolean) = updateForm { it.copy(willFollowSpeedLimit = value) }
    fun updateWillFollowRestBreaks(value: Boolean) = updateForm { it.copy(willFollowRestBreaks = value) }
    fun updateWillNotUsePhone(value: Boolean) = updateForm { it.copy(willNotUsePhoneWhileDriving = value) }
    fun updateWillWearSeatbelt(value: Boolean) = updateForm { it.copy(willWearSeatbelt = value) }
    fun updateHazardAwareness(value: String) = updateForm { it.copy(hazardAwareness = value) }
    fun updateWeatherCondition(value: String) = updateForm { it.copy(weatherCondition = value) }
    fun updateRouteRiskNote(value: String) = updateForm { it.copy(routeRiskNote = value) }
    fun updateIncidentReport(value: String) = updateForm { it.copy(incidentReport = value) }
    fun updateConfirmed(value: Boolean) = updateForm { it.copy(confirmed = value) }

    fun updateCurrentStep(index: Int) {
        _state.value = _state.value.copy(currentStepIndex = index)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessageRes = null)
    }

    fun submit() = viewModelScope.launch {
        if (_state.value.submitting) return@launch

        _state.value = _state.value.copy(showValidationErrors = true, errorMessageRes = null)
        val form = _state.value.form
        if (!form.allPledgesAnswered()) {
            _state.value = _state.value.copy(
                errorMessageRes = R.string.driver_error_safety_required_fields
            )
            return@launch
        }
        val hazardBlank = form.hazardAwareness.isBlank()
        val weatherBlank = form.weatherCondition.isBlank()
        if (hazardBlank || weatherBlank) {
            _state.value = _state.value.copy(
                errorMessageRes = R.string.driver_error_safety_required_fields
            )
            return@launch
        }
        if (form.confirmed != true) {
            _state.value = _state.value.copy(
                errorMessageRes = R.string.driver_error_safety_confirm_required
            )
            return@launch
        }

        val sleep = form.sleepHours.trim().takeIf { it.isNotBlank() }
        val sleepValue = sleep?.toIntOrNull()
        if (sleep != null && sleepValue == null) {
            _state.value = _state.value.copy(
                errorMessageRes = R.string.driver_error_safety_sleep_hours_invalid
            )
            return@launch
        }

        _state.value = _state.value.copy(submitting = true, errorMessageRes = null)

        try {
            val (uid, role) = currentUser() ?: run {
                _state.value = _state.value.copy(
                    submitting = false,
                    errorMessageRes = R.string.driver_error_user_not_logged_in
                )
                return@launch
            }

            val zone = TimeZone.of("Asia/Jakarta")
            val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()

            DMLog.d(
                TAG,
                "[DRIVER_SAFETY] submit_start authUid=$uid role=${role ?: "-"} " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )

            if (_state.value.statusToday == true) {
                DMLog.w(
                    TAG,
                    "[DRIVER_SAFETY] submit_skip authUid=$uid role=${role ?: "-"} " +
                        "dateEpochDay=$dateEpochDay success=false errorClass=AlreadySubmittedException " +
                        "reason=AlreadySubmitted dbId=$FIRESTORE_DATABASE_ID"
                )
                _state.value = _state.value.copy(
                    submitting = false,
                    errorMessageRes = R.string.driver_error_already_submitted_today
                )
                return@launch
            }

            val payload = SafetyDeclaration(
                fitToWork = form.fitToWork == true,
                sleepHours = sleepValue,
                noAlcoholOrDrugs = form.noAlcoholOrDrugs == true,
                noSevereStress = form.noSevereStress == true,
                willFollowSpeedLimit = form.willFollowSpeedLimit == true,
                willFollowRestBreaks = form.willFollowRestBreaks == true,
                willNotUsePhoneWhileDriving = form.willNotUsePhoneWhileDriving == true,
                willWearSeatbelt = form.willWearSeatbelt == true,
                hazardAwareness = form.hazardAwareness.trim().takeIf { it.isNotBlank() },
                weatherCondition = form.weatherCondition.trim().takeIf { it.isNotBlank() },
                routeRiskNote = form.routeRiskNote.trim().takeIf { it.isNotBlank() },
                incidentReport = form.incidentReport.trim().takeIf { it.isNotBlank() },
                confirmed = form.confirmed == true
            )

            when (val result = submitSafetyDeclaration(payload)) {
                is AppResult.Success -> {
                    DMLog.i(
                        TAG,
                        "[DRIVER_SAFETY] submit_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                    )
                    _state.value = _state.value.copy(
                        submitting = false,
                        successMessageRes = R.string.driver_success_safety_submitted,
                        statusToday = true,
                        showValidationErrors = false
                    )
                    refreshStatus()
                }
                is AppResult.Failure -> {
                    val reason = errorReason(result.throwable)
                    DMLog.e(
                        TAG,
                        "[DRIVER_SAFETY] submit_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=false " +
                            "errorClass=${result.throwable::class.simpleName ?: "Throwable"} " +
                            "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                        result.throwable
                    )
                    _state.value = _state.value.copy(
                        submitting = false,
                        errorMessageRes = when (result.throwable) {
                            is AlreadySubmittedException -> R.string.driver_error_already_submitted_today
                            else -> R.string.driver_error_safety_submit_failed
                        },
                        statusToday = if (result.throwable is AlreadySubmittedException) true else _state.value.statusToday
                    )
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            val reason = errorReason(t)
            DMLog.e(
                TAG,
                "[DRIVER_SAFETY] submit_result authUid=unknown role=- dateEpochDay=- " +
                    "success=false errorClass=${t::class.simpleName ?: "Throwable"} " +
                    "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                t
            )
            _state.value = _state.value.copy(
                submitting = false,
                errorMessageRes = R.string.driver_error_safety_submit_failed
            )
        }
    }

    fun refreshStatus() = viewModelScope.launch {
        if (_state.value.statusLoading) return@launch
        _state.value = _state.value.copy(statusLoading = true, errorMessageRes = null)

        try {
            val (uid, role) = currentUser() ?: run {
                _state.value = _state.value.copy(
                    statusLoading = false,
                    errorMessageRes = R.string.driver_error_user_not_logged_in
                )
                return@launch
            }

            val zone = TimeZone.of("Asia/Jakarta")
            val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()

            DMLog.d(
                TAG,
                "[DRIVER_SAFETY] status_load authUid=$uid role=${role ?: "-"} " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )

            when (val result = getSafetyStatus(uid, dateEpochDay)) {
                is AppResult.Success -> {
                    DMLog.i(
                        TAG,
                        "[DRIVER_SAFETY] status_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                    )
                    _state.value = _state.value.copy(
                        statusLoading = false,
                        statusToday = result.data
                    )
                }
                is AppResult.Failure -> {
                    val reason = errorReason(result.throwable)
                    DMLog.e(
                        TAG,
                        "[DRIVER_SAFETY] status_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=false " +
                            "errorClass=${result.throwable::class.simpleName ?: "Throwable"} " +
                            "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                        result.throwable
                    )
                    _state.value = _state.value.copy(
                        statusLoading = false,
                        errorMessageRes = R.string.driver_error_safety_status_failed
                    )
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            val reason = errorReason(t)
            DMLog.e(
                TAG,
                "[DRIVER_SAFETY] status_result authUid=unknown role=- dateEpochDay=- " +
                    "success=false errorClass=${t::class.simpleName ?: "Throwable"} " +
                    "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                t
            )
            _state.value = _state.value.copy(
                statusLoading = false,
                errorMessageRes = R.string.driver_error_safety_status_failed
            )
        }
    }

    private fun updateForm(block: (DriverSafetyFormState) -> DriverSafetyFormState) {
        _state.value = _state.value.copy(form = block(_state.value.form), errorMessageRes = null)
    }

    private suspend fun currentUser(): Pair<String, String?>? = when (val res = authRepository.currentUser()) {
        is AppResult.Success -> res.data?.let { it.uid to it.role.toFirestore() }
        is AppResult.Failure -> null
    }

    private fun errorReason(t: Throwable): String = when {
        t is AlreadySubmittedException -> "AlreadySubmitted"
        t.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> "PermissionDenied"
        else -> "Unknown"
    }
}

data class DriverSafetyUiState(
    val submitting: Boolean = false,
    val statusLoading: Boolean = false,
    val statusToday: Boolean? = null,
    @param:StringRes val errorMessageRes: Int? = null,
    @param:StringRes val successMessageRes: Int? = null,
    val showValidationErrors: Boolean = false,
    val currentStepIndex: Int = 0,
    val form: DriverSafetyFormState = DriverSafetyFormState()
)

/**
 * The 7 pledge fields are nullable here (unlike the domain model's non-null
 * Boolean) so the wizard can distinguish "not yet answered" from "answered
 * Belum" -- both must render differently, and only the latter is a real
 * false. confirmed is nullable for the same reason: it is itself a pledge
 * step (the final one), not a plain checkbox.
 */
data class DriverSafetyFormState(
    val fitToWork: Boolean? = null,
    val sleepHours: String = "",
    val noAlcoholOrDrugs: Boolean? = null,
    val noSevereStress: Boolean? = null,
    val willFollowSpeedLimit: Boolean? = null,
    val willFollowRestBreaks: Boolean? = null,
    val willNotUsePhoneWhileDriving: Boolean? = null,
    val willWearSeatbelt: Boolean? = null,
    val hazardAwareness: String = "",
    val weatherCondition: String = "",
    val routeRiskNote: String = "",
    val incidentReport: String = "",
    val confirmed: Boolean? = null
) {
    /** The 7 boolean statement cards, in wizard order -- used for both progress and validation. */
    fun pledgeAnswers(): List<Boolean?> = listOf(
        fitToWork, noAlcoholOrDrugs, noSevereStress,
        willFollowSpeedLimit, willFollowRestBreaks, willNotUsePhoneWhileDriving, willWearSeatbelt
    )

    fun allPledgesAnswered(): Boolean = pledgeAnswers().all { it != null }

    fun answeredPledgeCount(): Int = pledgeAnswers().count { it != null }

    /** How many of the 7 pledges were answered "Belum" -- drives the pre-submit warning. */
    fun belumPledgeCount(): Int = pledgeAnswers().count { it == false }
}
