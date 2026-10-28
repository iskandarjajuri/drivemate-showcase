package com.drivemate.showcase.feature.expat.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.AttendanceEntry
import com.drivemate.showcase.core.model.ChecklistSummary
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.SafetySummary
import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.admin.GetAdminChecklistsUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminLocationDetailUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminSafetyUseCase
import com.drivemate.showcase.domain.usecase.admin.GetAdminWorkdaysUseCase
import com.drivemate.showcase.domain.usecase.attendance.GetAttendanceByRangeUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.domain.usecase.workday.UpsertWorkdayPlanUseCase
import com.drivemate.showcase.util.UiErrorLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class ExpatDriverDetailViewModel(
    private val getAttendance: GetAttendanceByRangeUseCase,
    private val getChecklists: GetAdminChecklistsUseCase,
    private val getSafety: GetAdminSafetyUseCase,
    private val getWorkdays: GetAdminWorkdaysUseCase,
    private val getLocation: GetAdminLocationDetailUseCase,
    private val getSopStatus: GetDriverSopStatusUseCase,
    private val getUserDisplayName: GetUserDisplayNameUseCase,
    private val upsertWorkdayPlan: UpsertWorkdayPlanUseCase,
    private val authRepo: AuthRepository
) : ViewModel() {

    private companion object { const val TAG = "DM/ExpatDetailVM" }

    private val _attendance = MutableStateFlow<List<AttendanceEntry>>(emptyList())
    val attendance: StateFlow<List<AttendanceEntry>> = _attendance

    private val _checklists = MutableStateFlow<List<ChecklistSummary>>(emptyList())
    val checklists: StateFlow<List<ChecklistSummary>> = _checklists

    private val _safety = MutableStateFlow<List<SafetySummary>>(emptyList())
    val safety: StateFlow<List<SafetySummary>> = _safety

    private val _workdays = MutableStateFlow<List<WorkDay>>(emptyList())
    val workdays: StateFlow<List<WorkDay>> = _workdays

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    private val _sopStatus = MutableStateFlow<SopStatus?>(null)
    val sopStatus: StateFlow<SopStatus?> = _sopStatus

    private val _driverName = MutableStateFlow<String?>(null)
    val driverName: StateFlow<String?> = _driverName

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error

    private val zone = TimeZone.of("Asia/Jakarta")
    private var currentDriverUid: String? = null
    private var currentRangeDays: Int = 60

    fun saveWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long
    ) {
        viewModelScope.launch {
            when (val res = upsertWorkdayPlan(driverUid, epochDay, plannedStartMillis, plannedEndMillis, "expat")) {
                is AppResult.Success -> {
                    val targetUid = currentDriverUid ?: driverUid
                    load(targetUid, currentRangeDays)
                }
                is AppResult.Failure -> {
                    _error.value = R.string.expat_error_workdays_load_failed
                    UiErrorLogger.e(TAG, "workday plan upsert failed", res.throwable)
                }
            }
        }
    }

    fun load(driverUid: String, rangeDays: Int) {
        if (_loading.value) return
        _loading.value = true
        _error.value = null
        currentDriverUid = driverUid
        currentRangeDays = rangeDays

        viewModelScope.launch {
            val (_, roleLabel) = resolveAuthContext()
            val endEpochDay = TimeUtil.todayEpochDay(zone).toInt()
            val startEpochDay = (endEpochDay - (rangeDays - 1)).coerceAtLeast(0)

            logReview(
                event = "load_start",
                role = roleLabel,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                count = 0,
                success = true,
                errorClass = "none"
            )

            when (val res = getAttendance(startEpochDay, endEpochDay, driverUid, 200)) {
                is AppResult.Success -> {
                    _attendance.value = res.data
                    logReview("attendance_fetch", roleLabel, startEpochDay, endEpochDay, res.data.size, true, "none")
                }
                is AppResult.Failure -> {
                    _error.value = R.string.expat_error_attendance_load_failed
                    UiErrorLogger.e(TAG, "attendance fetch failed", res.throwable)
                    logReview("attendance_fetch", roleLabel, startEpochDay, endEpochDay, 0, false, res.throwable::class.simpleName ?: "Throwable")
                }
            }

            when (val res = getChecklists(startEpochDay, endEpochDay, driverUid, 200)) {
                is AppResult.Success -> {
                    _checklists.value = res.data
                    logReview("checklists_fetch", roleLabel, startEpochDay, endEpochDay, res.data.size, true, "none")
                }
                is AppResult.Failure -> {
                    _error.value = _error.value ?: R.string.expat_error_checklist_load_failed
                    UiErrorLogger.e(TAG, "checklists fetch failed", res.throwable)
                    logReview("checklists_fetch", roleLabel, startEpochDay, endEpochDay, 0, false, res.throwable::class.simpleName ?: "Throwable")
                }
            }

            when (val res = getSafety(startEpochDay, endEpochDay, driverUid, 200)) {
                is AppResult.Success -> {
                    _safety.value = res.data
                    logReview("safety_fetch", roleLabel, startEpochDay, endEpochDay, res.data.size, true, "none")
                }
                is AppResult.Failure -> {
                    _error.value = _error.value ?: R.string.expat_error_safety_load_failed
                    UiErrorLogger.e(TAG, "safety fetch failed", res.throwable)
                    logReview("safety_fetch", roleLabel, startEpochDay, endEpochDay, 0, false, res.throwable::class.simpleName ?: "Throwable")
                }
            }

            when (val res = getWorkdays(startEpochDay, endEpochDay, driverUid, 200)) {
                is AppResult.Success -> {
                    _workdays.value = res.data
                    logReview("workdays_fetch", roleLabel, startEpochDay, endEpochDay, res.data.size, true, "none")
                }
                is AppResult.Failure -> {
                    _error.value = _error.value ?: R.string.expat_error_workdays_load_failed
                    UiErrorLogger.e(TAG, "workdays fetch failed", res.throwable)
                    logReview("workdays_fetch", roleLabel, startEpochDay, endEpochDay, 0, false, res.throwable::class.simpleName ?: "Throwable")
                }
            }

            when (val res = getLocation(driverUid)) {
                is AppResult.Success -> _location.value = res.data
                is AppResult.Failure -> {
                    _location.value = null
                    UiErrorLogger.e(TAG, "location fetch failed", res.throwable)
                    logReview("location_fetch", roleLabel, startEpochDay, endEpochDay, 0, false, res.throwable::class.simpleName ?: "Throwable")
                }
            }

            when (val res = getSopStatus(driverUid)) {
                is AppResult.Success -> _sopStatus.value = res.data
                is AppResult.Failure -> {
                    _sopStatus.value = null
                    UiErrorLogger.e(TAG, "sop status fetch failed", res.throwable)
                    logReview("sop_fetch", roleLabel, startEpochDay, endEpochDay, 0, false, res.throwable::class.simpleName ?: "Throwable")
                }
            }

            resolveDriverName(driverUid)
            _loading.value = false
        }
    }

    private suspend fun resolveDriverName(uid: String) {
        val name = when (val res = getUserDisplayName(uid)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> null
        }
        _driverName.value = name
    }

    private suspend fun resolveAuthContext(): Pair<String, String> {
        return when (val res = authRepo.currentUser()) {
            is AppResult.Success -> {
                val user = res.data
                val uid = user?.uid ?: "-"
                val role = user?.role?.toFirestore() ?: "-"
                uid to role
            }
            is AppResult.Failure -> "-" to "-"
        }
    }

    private fun logReview(
        event: String,
        role: String,
        startEpochDay: Int,
        endEpochDay: Int,
        count: Int,
        success: Boolean,
        errorClass: String
    ) {
        val base =
            "[DRIVER_HISTORY] $event role=$role " +
                "rangeStartEpochDay=$startEpochDay rangeEndEpochDay=$endEpochDay " +
                "count=$count success=$success errorClass=$errorClass"
        if (success) {
            DMLog.i(TAG, base)
        } else {
            DMLog.e(TAG, base)
        }
    }
}
