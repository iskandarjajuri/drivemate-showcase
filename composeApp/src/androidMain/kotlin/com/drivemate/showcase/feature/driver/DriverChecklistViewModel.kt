package com.drivemate.showcase.feature.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AlreadySubmittedException
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.SubmitChecklistUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

private const val TAG = "DM/DriverChecklist"
private const val FIRESTORE_DATABASE_ID = "drivemate-database"

/** How long the button shows its confirmed state before Home takes over. */
private const val SUBMIT_CONFIRMATION_DELAY_MS = 600L

/** One-shot navigation signal -- never state, so it can't replay on rotation. */
sealed interface DriverChecklistEvent {
    data class Submitted(val submittedAtMillis: Long) : DriverChecklistEvent
}

class DriverChecklistViewModel(
    private val submitChecklist: SubmitChecklistUseCase,
    private val getChecklistStatus: GetChecklistStatusUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DriverChecklistUiState())
    val state: StateFlow<DriverChecklistUiState> = _state.asStateFlow()

    private val _events = Channel<DriverChecklistEvent>(Channel.BUFFERED)
    val events: Flow<DriverChecklistEvent> = _events.receiveAsFlow()

    init {
        refreshStatus()
    }

    fun updateVehicleId(value: String) = updateForm { it.copy(vehicleId = value) }
    fun updateOdometer(value: String) = updateForm { it.copy(odometerKm = value) }
    fun updateFuelLevel(value: String) = updateForm { it.copy(fuelLevel = value) }
    fun updateTiresFrontLeftOk(value: Boolean?) = updateForm { it.copy(tiresFrontLeftOk = value) }
    fun updateTiresFrontRightOk(value: Boolean?) = updateForm { it.copy(tiresFrontRightOk = value) }
    fun updateTiresRearLeftOk(value: Boolean?) = updateForm { it.copy(tiresRearLeftOk = value) }
    fun updateTiresRearRightOk(value: Boolean?) = updateForm { it.copy(tiresRearRightOk = value) }
    fun updateTirePressureNote(value: String) = updateForm { it.copy(tirePressureNote = value) }
    fun updateBrakesOk(value: Boolean?) = updateForm { it.copy(brakesOk = value) }
    fun updateLightsOk(value: Boolean?) = updateForm { it.copy(lightsOk = value) }
    fun updateTurnSignalBrakeLightsOk(value: Boolean?) = updateForm { it.copy(turnSignalBrakeLightsOk = value) }
    fun updateHornOk(value: Boolean?) = updateForm { it.copy(hornOk = value) }
    fun updateWipersOk(value: Boolean?) = updateForm { it.copy(wipersOk = value) }
    fun updateMirrorsOk(value: Boolean?) = updateForm { it.copy(mirrorsOk = value) }
    fun updateSeatbeltOk(value: Boolean?) = updateForm { it.copy(seatbeltOk = value) }
    fun updateEngineOilOk(value: Boolean?) = updateForm { it.copy(engineOilOk = value) }
    fun updateCoolantOk(value: Boolean?) = updateForm { it.copy(coolantOk = value) }
    fun updateBatteryOk(value: Boolean?) = updateForm { it.copy(batteryOk = value) }
    fun updateWasherFluidOk(value: Boolean?) = updateForm { it.copy(washerFluidOk = value) }
    fun updateCleanlinessOk(value: Boolean?) = updateForm { it.copy(cleanlinessOk = value) }
    fun updateDocumentsOk(value: Boolean?) = updateForm { it.copy(documentsOk = value) }
    fun updateFireExtinguisherOk(value: Boolean?) = updateForm { it.copy(fireExtinguisherOk = value) }
    fun updateFirstAidOk(value: Boolean?) = updateForm { it.copy(firstAidOk = value) }
    fun updateWarningTriangleOk(value: Boolean?) = updateForm { it.copy(warningTriangleOk = value) }
    fun updateNotes(value: String) = updateForm { it.copy(notes = value) }

    fun submit() = viewModelScope.launch {
        if (_state.value.submitting) return@launch

        _state.value = _state.value.copy(showValidationErrors = true, errorMessageRes = null)
        val form = _state.value.form
        if (form.fuelLevel.isBlank()) {
            _state.value = _state.value.copy(errorMessageRes = R.string.driver_error_checklist_fuel_required)
            return@launch
        }
        if (!form.allInspectionItemsAnswered()) {
            _state.value = _state.value.copy(
                errorMessageRes = R.string.driver_error_checklist_all_items_required
            )
            return@launch
        }

        val odometer = form.odometerKm.trim().takeIf { it.isNotBlank() }
        val odometerValue = odometer?.toIntOrNull()
        if (odometer != null && odometerValue == null) {
            _state.value = _state.value.copy(
                errorMessageRes = R.string.driver_error_checklist_odometer_invalid
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
                "[DRIVER_CHECKLIST] submit_start authUid=$uid role=${role ?: "-"} " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )

            if (_state.value.statusToday == true) {
                DMLog.w(
                    TAG,
                    "[DRIVER_CHECKLIST] submit_skip authUid=$uid role=${role ?: "-"} " +
                        "dateEpochDay=$dateEpochDay success=false errorClass=AlreadySubmittedException " +
                        "reason=AlreadySubmitted dbId=$FIRESTORE_DATABASE_ID"
                )
                _state.value = _state.value.copy(
                    submitting = false,
                    errorMessageRes = R.string.driver_error_already_submitted_today
                )
                return@launch
            }

            val checklist = Checklist(
                vehicleId = form.vehicleId.trim().takeIf { it.isNotBlank() },
                odometerKm = odometerValue,
                fuelLevel = form.fuelLevel,
                tiresFrontLeftOk = form.tiresFrontLeftOk,
                tiresFrontRightOk = form.tiresFrontRightOk,
                tiresRearLeftOk = form.tiresRearLeftOk,
                tiresRearRightOk = form.tiresRearRightOk,
                tirePressureNote = form.tirePressureNote.trim().takeIf { it.isNotBlank() },
                brakesOk = form.brakesOk,
                lightsOk = form.lightsOk,
                turnSignalBrakeLightsOk = form.turnSignalBrakeLightsOk,
                hornOk = form.hornOk,
                wipersOk = form.wipersOk,
                mirrorsOk = form.mirrorsOk,
                seatbeltOk = form.seatbeltOk,
                engineOilOk = form.engineOilOk,
                coolantOk = form.coolantOk,
                batteryOk = form.batteryOk,
                washerFluidOk = form.washerFluidOk,
                cleanlinessOk = form.cleanlinessOk,
                documentsOk = form.documentsOk,
                fireExtinguisherOk = form.fireExtinguisherOk,
                firstAidOk = form.firstAidOk,
                warningTriangleOk = form.warningTriangleOk,
                notes = form.notes.trim().takeIf { it.isNotBlank() },
                hasIssue = form.hasIssue()
            )

            when (val result = submitChecklist(checklist)) {
                is AppResult.Success -> {
                    DMLog.i(
                        TAG,
                        "[DRIVER_CHECKLIST] submit_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                    )
                    _state.value = _state.value.copy(
                        submitting = false,
                        justSubmitted = true,
                        statusToday = true,
                        showValidationErrors = false
                    )
                    refreshStatus()
                    val submittedAtMillis = TimeUtil.nowEpochMillis()
                    viewModelScope.launch {
                        delay(SUBMIT_CONFIRMATION_DELAY_MS)
                        _events.send(DriverChecklistEvent.Submitted(submittedAtMillis))
                    }
                }
                is AppResult.Failure -> {
                    val reason = errorReason(result.throwable)
                    DMLog.e(
                        TAG,
                        "[DRIVER_CHECKLIST] submit_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=false " +
                            "errorClass=${result.throwable::class.simpleName ?: "Throwable"} " +
                            "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                        result.throwable
                    )
                    _state.value = _state.value.copy(
                        submitting = false,
                        errorMessageRes = when (result.throwable) {
                            is AlreadySubmittedException -> R.string.driver_error_already_submitted_today
                            else -> R.string.driver_error_checklist_submit_failed
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
                "[DRIVER_CHECKLIST] submit_result authUid=unknown role=- dateEpochDay=- " +
                    "success=false errorClass=${t::class.simpleName ?: "Throwable"} " +
                    "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                t
            )
            _state.value = _state.value.copy(
                submitting = false,
                errorMessageRes = R.string.driver_error_checklist_submit_failed
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
                "[DRIVER_CHECKLIST] status_load authUid=$uid role=${role ?: "-"} " +
                    "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
            )

            when (val result = getChecklistStatus(uid, dateEpochDay)) {
                is AppResult.Success -> {
                    DMLog.i(
                        TAG,
                        "[DRIVER_CHECKLIST] status_result authUid=$uid role=${role ?: "-"} " +
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
                        "[DRIVER_CHECKLIST] status_result authUid=$uid role=${role ?: "-"} " +
                            "dateEpochDay=$dateEpochDay success=false " +
                            "errorClass=${result.throwable::class.simpleName ?: "Throwable"} " +
                            "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                        result.throwable
                    )
                    _state.value = _state.value.copy(
                        statusLoading = false,
                        errorMessageRes = R.string.driver_error_checklist_status_failed
                    )
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            val reason = errorReason(t)
            DMLog.e(
                TAG,
                "[DRIVER_CHECKLIST] status_result authUid=unknown role=- dateEpochDay=- " +
                    "success=false errorClass=${t::class.simpleName ?: "Throwable"} " +
                    "reason=$reason dbId=$FIRESTORE_DATABASE_ID",
                t
            )
            _state.value = _state.value.copy(
                statusLoading = false,
                errorMessageRes = R.string.driver_error_checklist_status_failed
            )
        }
    }

    private fun updateForm(block: (DriverChecklistFormState) -> DriverChecklistFormState) {
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

data class DriverChecklistUiState(
    val submitting: Boolean = false,
    val statusLoading: Boolean = false,
    val statusToday: Boolean? = null,
    @param:StringRes val errorMessageRes: Int? = null,
    // True only for a submission that happened THIS session -- the ViewModel's
    // `form` still holds the actual answers in memory, so the screen can show
    // them read-only. A fresh load with statusToday==true has no such memory
    // (the repository deliberately doesn't support reading answers back, see
    // ChecklistRepository), so that case keeps showing the generic locked state.
    val justSubmitted: Boolean = false,
    val showValidationErrors: Boolean = false,
    val form: DriverChecklistFormState = DriverChecklistFormState()
)

data class DriverChecklistFormState(
    val vehicleId: String = "",
    val odometerKm: String = "",
    val fuelLevel: String = "",
    val tiresFrontLeftOk: Boolean? = null,
    val tiresFrontRightOk: Boolean? = null,
    val tiresRearLeftOk: Boolean? = null,
    val tiresRearRightOk: Boolean? = null,
    val tirePressureNote: String = "",
    val brakesOk: Boolean? = null,
    val lightsOk: Boolean? = null,
    val turnSignalBrakeLightsOk: Boolean? = null,
    val hornOk: Boolean? = null,
    val wipersOk: Boolean? = null,
    val mirrorsOk: Boolean? = null,
    val seatbeltOk: Boolean? = null,
    val engineOilOk: Boolean? = null,
    val coolantOk: Boolean? = null,
    val batteryOk: Boolean? = null,
    val washerFluidOk: Boolean? = null,
    val cleanlinessOk: Boolean? = null,
    val documentsOk: Boolean? = null,
    val fireExtinguisherOk: Boolean? = null,
    val firstAidOk: Boolean? = null,
    val warningTriangleOk: Boolean? = null,
    val notes: String = ""
) {
    private fun inspectionItems(): List<Boolean?> = listOf(
        tiresFrontLeftOk,
        tiresFrontRightOk,
        tiresRearLeftOk,
        tiresRearRightOk,
        brakesOk,
        lightsOk,
        turnSignalBrakeLightsOk,
        hornOk,
        wipersOk,
        mirrorsOk,
        seatbeltOk,
        engineOilOk,
        coolantOk,
        batteryOk,
        washerFluidOk,
        cleanlinessOk,
        documentsOk,
        fireExtinguisherOk,
        firstAidOk,
        warningTriangleOk
    )

    fun hasIssue(): Boolean = inspectionItems().any { it == false }

    fun allInspectionItemsAnswered(): Boolean = inspectionItems().all { it != null }

    fun answeredItemCount(): Int = inspectionItems().count { it != null }

    fun totalItemCount(): Int = inspectionItems().size
}
