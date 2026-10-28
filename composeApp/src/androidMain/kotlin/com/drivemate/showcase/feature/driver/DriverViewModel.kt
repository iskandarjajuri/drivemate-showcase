package com.drivemate.showcase.feature.driver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Attendance
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.TrackingIssue
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.service.ReverseGeocodingService
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.error.AttendanceError
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.usecase.attendance.GetTodayAttendanceUseCase
import com.drivemate.showcase.domain.usecase.attendance.TapInUseCase
import com.drivemate.showcase.domain.usecase.attendance.TapOutUseCase
import com.drivemate.showcase.domain.usecase.location.UpdateDriverLocationUseCase
import com.drivemate.showcase.domain.usecase.location.ClearDriverLocationUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import com.drivemate.showcase.domain.usecase.workday.GetDriverTodayScheduleUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import com.drivemate.showcase.domain.usecase.safetychecklist.GetSafetyDeclarationStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import com.drivemate.showcase.domain.usecase.tracking.ObserveTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.tracking.UpdateTrackingStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.tracking.DriverTrackingController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone

class DriverViewModel(
    private val tapIn: TapInUseCase,
    private val tapOut: TapOutUseCase,
    private val updateLocation: UpdateDriverLocationUseCase,   // ⬅️ baru
    private val getTodayAttendance: GetTodayAttendanceUseCase, // ⬅️ baru
    private val getWorkDaysByRange: GetWorkDaysByRangeUseCase,
    private val getDriverTodaySchedule: GetDriverTodayScheduleUseCase,
    private val getChecklistStatus: GetChecklistStatusUseCase,
    private val getSafetyStatus: GetSafetyDeclarationStatusUseCase,
    private val getSopStatus: GetDriverSopStatusUseCase,
    private val getUserDisplayName: GetUserDisplayNameUseCase,
    private val authRepo: AuthRepository,                       // ⬅️ baru
    observeTrackingStatus: ObserveTrackingStatusUseCase,
    private val updateTrackingStatus: UpdateTrackingStatusUseCase,
    private val clearDriverLocation: ClearDriverLocationUseCase,
    private val reverseGeocoder: ReverseGeocodingService,
    private val appContext: Context
) : ViewModel() {

    private companion object {
        const val TAG_ATT = "DM/Attendance"
        const val TAG_LOC = "DM/LocationWrite"
        const val TAG = "DM/Driver"
        const val FIRESTORE_DATABASE_ID = "drivemate-database"
        const val LOADING_TIMEOUT_MS = FirestoreConstants.READ_TIMEOUT_MS

        /** Jumlah entri riwayat yang ditampilkan di pratinjau Home. */
        const val HOME_HISTORY_PREVIEW_COUNT = 4
    }

    private val fallbackLocation by lazy {
        appContext.getString(R.string.driver_location_unavailable)
    }

    private val _state = MutableStateFlow<DriverUiState>(DriverUiState.Loading)
    val state: StateFlow<DriverUiState> = _state
    private val _homeState = MutableStateFlow(DriverHomeUiState())
    val homeState: StateFlow<DriverHomeUiState> = _homeState
    private val trackingStatusFlow = observeTrackingStatus()

    val trackingState: StateFlow<DriverTrackingUiState> = trackingStatusFlow
        .map { status ->
            val warningMessageRes = when (status.issue) {
                TrackingIssue.PermissionMissing ->
                    R.string.driver_tracking_warning_permission_missing
                TrackingIssue.ProviderDisabled ->
                    R.string.driver_tracking_warning_provider_disabled
                null -> null
            }
            DriverTrackingUiState(
                warningType = status.issue,
                warningMessageRes = warningMessageRes,
                batteryOptimizationEnabled = status.batteryOptimizationEnabled,
                hideLocationWhenOffDuty = status.hideLocationWhenOffDuty,
                overSpeedLimit = status.overSpeedLimit,
                currentSpeedKph = status.currentSpeedKph,
                speedLimitKph = status.speedLimitKph
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DriverTrackingUiState()
        )

    /** Penjaga idempoten untuk pembersihan lokasi off-duty; lihat pemakaiannya. */
    private var offDutyLocationCleared = false
    private var inFlight = false
    private var refreshTodayJob: Job? = null

    init {
        DMLog.i(TAG_ATT, "init → refreshing today's attendance")
        refreshToday()
        refreshHomeState()
    }

    fun reconcileTracking(
        context: Context,
        clockedIn: Boolean,
        permissionGranted: Boolean,
        providerEnabled: Boolean
    ) {
        when {
            !clockedIn -> {
                DriverTrackingController.stop(context)
                // Pembersihan TANPA SYARAT begitu driver tidak lagi bertugas.
                // Gerbang `hideLocationWhenOffDuty` di sini sudah dibuang: sejak
                // kartu privasinya dihapus dari Home, tidak ada lagi cara
                // menyalakan flag itu di Android, sehingga gerbangnya berarti
                // lokasi TIDAK PERNAH dibersihkan sama sekali. Tap-out sendiri
                // tidak membersihkan apa pun (TapOutUseCase nol referensi ke
                // LocationRepository), jadi ini satu-satunya jaring pengaman.
                //
                // Field `hideLocationWhenOffDuty` sengaja TIDAK dihapus dari
                // model: iOS masih membacanya dan masih punya toggle-nya
                // sendiri. Android kini selalu membersihkan, iOS membersihkan
                // hanya kalau toggle-nya menyala.
                clearLocationSnapshotOnceForThisOffDutyPeriod()
            }
            !permissionGranted -> DriverTrackingController.stopForPermissionMissing(context)
            providerEnabled -> {
                // Kembali bertugas: izinkan pembersihan sekali lagi nanti saat
                // periode off-duty BERIKUTNYA.
                offDutyLocationCleared = false
                DriverTrackingController.start(context)
            }
        }
    }

    /**
     * Membersihkan snapshot lokasi sekali per periode off-duty.
     *
     * `reconcileTracking` dipanggil ulang dari `LaunchedEffect` Driver Home tiap
     * kali layar itu dikomposisi ulang -- termasuk setiap kali driver kembali ke
     * Home. Tanpa penjaga ini, satu kunjungan Home = satu delete Firestore,
     * padahal dokumennya sudah tidak ada; delete atas dokumen yang tidak ada pun
     * tetap ditagih dan tetap satu round-trip jaringan.
     *
     * Penjaga ini hidup selama ViewModel: kalau proses aplikasi mati lalu driver
     * membuka Home lagi, pembersihan terjadi sekali lagi. Itu tidak masalah --
     * tujuannya membunuh penulisan per-rekomposisi, bukan menjamin sekali-seumur-
     * hidup.
     */
    private fun clearLocationSnapshotOnceForThisOffDutyPeriod() {
        if (offDutyLocationCleared) return
        offDutyLocationCleared = true
        clearLocationSnapshot()
    }

    fun doTapIn() = doTapIn(seedLatLng = null)

    fun doTapIn(seedLatLng: Pair<Double, Double>?, permissionGranted: Boolean? = null) {
        if (inFlight) {
            DMLog.w(TAG, "TapIn ignored → already in flight")
            return
        }
        inFlight = true
        val existing = _state.value as? DriverUiState.Working
        val existingAttendance = existing?.todayAttendance ?: TodayAttendanceUi()
        _state.value = DriverUiState.Working(
            clockedIn = false,
            lastTapIn = formatHmOrNull(existingAttendance.clockInMillis),
            lastTapOut = formatHmOrNull(existingAttendance.clockOutMillis),
            todayAttendance = existingAttendance
        )
        viewModelScope.launch {
            withCurrentUser(
                onNull = {
                    DMLog.e(TAG, "TapIn failed → user not logged in")
                    _state.value = DriverUiState.Error(R.string.driver_error_user_not_logged_in)
                    inFlight = false
                }
            ) { userId, role ->
                val now = TimeUtil.nowEpochMillis()
                val zone = TimeZone.of("Asia/Jakarta")
                val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()
                val roleLabel = role ?: "-"
                DMLog.i(
                    TAG_ATT,
                    "[DRIVER_ATTENDANCE] tap_in_start authUid=$userId role=$roleLabel " +
                        "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                )
                if (seedLatLng == null) {
                    val reason = if (permissionGranted == false) "permission_denied" else "location_unavailable"
                    DMLog.w(
                        TAG_ATT,
                        "[DRIVER_ATTENDANCE] tap_in_location_unavailable authUid=$userId role=$roleLabel " +
                            "dateEpochDay=$dateEpochDay success=false errorClass=LocationUnavailable " +
                            "reason=$reason dbId=$FIRESTORE_DATABASE_ID"
                    )
                }
                val locationName = seedLatLng?.let { (lat, lon) ->
                    val resolved = try {
                        reverseGeocoder.resolve(lat, lon)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        DMLog.d(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] reverse_geocode authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=false " +
                                "errorClass=${t::class.simpleName ?: "Throwable"} " +
                                "reason=${t.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID"
                        )
                        null
                    }
                    if (resolved.isNullOrBlank()) {
                        DMLog.d(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] reverse_geocode authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=false errorClass=EmptyResult " +
                                "reason=empty dbId=$FIRESTORE_DATABASE_ID"
                        )
                        fallbackLocation
                    } else {
                        DMLog.d(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] reverse_geocode authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                        )
                        resolved
                    }
                } ?: fallbackLocation
                val att = Attendance(
                    driverUid = userId,
                    timestampMillis = now,
                    latitude = seedLatLng?.first,
                    longitude = seedLatLng?.second,
                    note = null,
                    locationName = locationName
                )

                when (val res = tapIn(att)) {
                    is AppResult.Success -> {
                        val todayAttendance = res.data.toUi()
                        val t = formatHmOrNull(todayAttendance.clockInMillis)
                        DMLog.i(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] tap_in_write authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                        )
                        _state.value = DriverUiState.Working(
                            clockedIn = true,
                            lastTapIn = t,
                            lastTapOut = formatHmOrNull(todayAttendance.clockOutMillis),
                            todayAttendance = todayAttendance,
                            messageRes = if (seedLatLng == null) {
                                R.string.driver_warning_location_unavailable_attendance_sent
                            } else {
                                null
                            }
                        )
                        if (seedLatLng != null) {
                            val (lat, lon) = seedLatLng
                            when (val locRes = updateLocation(
                                Location(
                                    driverUid = userId,
                                    latitude = lat,
                                    longitude = lon,
                                    updatedAtMillis = now,
                                    locationName = locationName
                                )
                            )) {
                                is AppResult.Success -> {
                                    DMLog.i(
                                        TAG_LOC,
                                        "[DRIVER_ATTENDANCE] location_update authUid=$userId role=$roleLabel " +
                                            "dateEpochDay=$dateEpochDay success=true errorClass=none " +
                                            "dbId=$FIRESTORE_DATABASE_ID"
                                    )
                                }
                                is AppResult.Failure -> {
                                    DMLog.e(
                                        TAG_LOC,
                                        "[DRIVER_ATTENDANCE] location_update authUid=$userId role=$roleLabel " +
                                            "dateEpochDay=$dateEpochDay success=false " +
                                            "errorClass=${locRes.throwable::class.simpleName ?: "Throwable"} " +
                                            "reason=${locRes.throwable.message ?: "unknown"} " +
                                            "dbId=$FIRESTORE_DATABASE_ID",
                                        locRes.throwable
                                    )
                                }
                            }
                        } else {
                            DMLog.w(
                                TAG_LOC,
                                "[DRIVER_ATTENDANCE] location_update authUid=$userId role=$roleLabel " +
                                    "dateEpochDay=$dateEpochDay success=false errorClass=LocationUnavailable " +
                                    "reason=location_unavailable dbId=$FIRESTORE_DATABASE_ID"
                            )
                        }
                        refreshToday()
                        refreshHomeState()
                    }
                    is AppResult.Failure -> {
                        DMLog.e(
                            TAG,
                            "[DRIVER_ATTENDANCE] tap_in_write authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=false " +
                                "errorClass=${res.throwable::class.simpleName ?: "Throwable"} " +
                                "reason=${res.throwable.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID",
                            res.throwable
                        )
                        when (val effectiveToday = loadEffectiveTodayAttendance(userId, dateEpochDay)) {
                            is AppResult.Success -> {
                                val attendance = effectiveToday.data
                                if (attendance != null) {
                                    _state.value = workingStateFromAttendance(
                                        attendance = attendance,
                                        messageRes = R.string.driver_error_attendance_tap_in_failed
                                    )
                                } else {
                                    _state.value = DriverUiState.Error(
                                        R.string.driver_error_attendance_tap_in_failed
                                    )
                                }
                            }
                            is AppResult.Failure -> {
                                _state.value = DriverUiState.Error(
                                    R.string.driver_error_attendance_tap_in_failed
                                )
                            }
                        }
                    }
                }
                inFlight = false
            }
        }
    }

    fun doTapOut() {
        doTapOut(seedLatLng = null)
    }

    fun doTapOut(seedLatLng: Pair<Double, Double>?, permissionGranted: Boolean? = null) {
        if (inFlight) {
            DMLog.w(TAG, "TapOut ignored → already in flight")
            return
        }
        inFlight = true
        val existing = _state.value as? DriverUiState.Working
        val existingAttendance = existing?.todayAttendance ?: TodayAttendanceUi()
        _state.value = DriverUiState.Working(
            clockedIn = true,
            lastTapIn = formatHmOrNull(existingAttendance.clockInMillis),
            lastTapOut = formatHmOrNull(existingAttendance.clockOutMillis),
            todayAttendance = existingAttendance
        )
        viewModelScope.launch {
            withCurrentUser(
                onNull = {
                    DMLog.e(TAG, "TapOut failed → user not logged in")
                    _state.value = DriverUiState.Error(R.string.driver_error_user_not_logged_in)
                    inFlight = false
                }
            ) { userId, role ->
                val now = TimeUtil.nowEpochMillis()
                val zone = TimeZone.of("Asia/Jakarta")
                val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()
                val roleLabel = role ?: "-"
                DMLog.i(
                    TAG_ATT,
                    "[DRIVER_ATTENDANCE] tap_out_start authUid=$userId role=$roleLabel " +
                        "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                )
                if (seedLatLng == null) {
                    val reason = if (permissionGranted == false) "permission_denied" else "location_unavailable"
                    DMLog.w(
                        TAG_ATT,
                        "[DRIVER_ATTENDANCE] tap_out_location_unavailable authUid=$userId role=$roleLabel " +
                            "dateEpochDay=$dateEpochDay success=false errorClass=LocationUnavailable " +
                            "reason=$reason dbId=$FIRESTORE_DATABASE_ID"
                    )
                }
                val locationName = seedLatLng?.let { (lat, lon) ->
                    val resolved = try {
                        reverseGeocoder.resolve(lat, lon)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        DMLog.d(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] reverse_geocode authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=false " +
                                "errorClass=${t::class.simpleName ?: "Throwable"} " +
                                "reason=${t.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID"
                        )
                        null
                    }
                    if (resolved.isNullOrBlank()) {
                        DMLog.d(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] reverse_geocode authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=false errorClass=EmptyResult " +
                                "reason=empty dbId=$FIRESTORE_DATABASE_ID"
                        )
                        fallbackLocation
                    } else {
                        DMLog.d(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] reverse_geocode authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                        )
                        resolved
                    }
                } ?: fallbackLocation

                when (val res = tapOut(
                    driverUid = userId,
                    locationName = locationName,
                    latitude = seedLatLng?.first,
                    longitude = seedLatLng?.second
                )) {
                    is AppResult.Success -> {
                        val todayAttendance = res.data.toUi()
                        val t = formatHmOrNull(todayAttendance.clockOutMillis)
                        DMLog.i(
                            TAG_ATT,
                            "[DRIVER_ATTENDANCE] tap_out_write authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                        )
                        _state.value = DriverUiState.Working(
                            clockedIn = false,
                            lastTapIn = formatHmOrNull(todayAttendance.clockInMillis),
                            lastTapOut = t,
                            todayAttendance = todayAttendance,
                            messageRes = if (seedLatLng == null) {
                                R.string.driver_warning_location_unavailable_attendance_sent
                            } else {
                                null
                            }
                        )
                        if (seedLatLng != null) {
                            val (lat, lon) = seedLatLng
                            when (val locRes = updateLocation(
                                Location(
                                    driverUid = userId,
                                    latitude = lat,
                                    longitude = lon,
                                    updatedAtMillis = now,
                                    locationName = locationName
                                )
                            )) {
                                is AppResult.Success -> {
                                    DMLog.i(
                                        TAG_LOC,
                                        "[DRIVER_ATTENDANCE] location_update authUid=$userId role=$roleLabel " +
                                            "dateEpochDay=$dateEpochDay success=true errorClass=none " +
                                            "dbId=$FIRESTORE_DATABASE_ID"
                                    )
                                }
                                is AppResult.Failure -> {
                                    DMLog.e(
                                        TAG_LOC,
                                        "[DRIVER_ATTENDANCE] location_update authUid=$userId role=$roleLabel " +
                                            "dateEpochDay=$dateEpochDay success=false " +
                                            "errorClass=${locRes.throwable::class.simpleName ?: "Throwable"} " +
                                            "reason=${locRes.throwable.message ?: "unknown"} " +
                                            "dbId=$FIRESTORE_DATABASE_ID",
                                        locRes.throwable
                                    )
                                }
                            }
                        } else {
                            DMLog.w(
                                TAG_LOC,
                                "[DRIVER_ATTENDANCE] location_update authUid=$userId role=$roleLabel " +
                                    "dateEpochDay=$dateEpochDay success=false errorClass=LocationUnavailable " +
                                    "reason=location_unavailable dbId=$FIRESTORE_DATABASE_ID"
                            )
                        }
                        // Tanpa syarat: tap-out sudah pasti mengakhiri tugas,
                        // dan ini titik sekali-jalan per tap-out sehingga tidak
                        // butuh penjaga idempoten seperti jalur reconcile.
                        clearLocationSnapshot()
                        refreshToday()
                        refreshHomeState()
                    }
                    is AppResult.Failure -> {
                        DMLog.e(
                            TAG,
                            "[DRIVER_ATTENDANCE] tap_out_write authUid=$userId role=$roleLabel " +
                                "dateEpochDay=$dateEpochDay success=false " +
                                "errorClass=${res.throwable::class.simpleName ?: "Throwable"} " +
                                "reason=${res.throwable.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID",
                            res.throwable
                        )
                        // The lock in DriverAttendanceScreen is UX-only; this is what
                        // actually enforces the rule. If the driver still reaches here
                        // with the checklist incomplete -- stale UI state, a race with
                        // another device, etc. -- say so specifically rather than
                        // falling back to the generic failure message every other
                        // AttendanceError case uses.
                        val tapOutFailedMessageRes = if (res.throwable is AttendanceError.ChecklistIncomplete) {
                            R.string.driver_error_attendance_tap_out_checklist_incomplete
                        } else {
                            R.string.driver_error_attendance_tap_out_failed
                        }
                        when (val effectiveToday = loadEffectiveTodayAttendance(userId, dateEpochDay)) {
                            is AppResult.Success -> {
                                val attendance = effectiveToday.data
                                if (attendance != null) {
                                    _state.value = workingStateFromAttendance(
                                        attendance = attendance,
                                        messageRes = tapOutFailedMessageRes
                                    )
                                } else {
                                    _state.value = DriverUiState.Error(tapOutFailedMessageRes)
                                }
                            }
                            is AppResult.Failure -> {
                                _state.value = DriverUiState.Error(tapOutFailedMessageRes)
                            }
                        }
                    }
                }
            }
            inFlight = false
        }
    }

    private fun refreshToday(force: Boolean = false) {
        DMLog.d(TAG, "refreshToday()")
        if (!force && refreshTodayJob?.isActive == true) {
            DMLog.d(TAG, "refreshToday() skipped; refresh already active")
            return
        }
        refreshTodayJob = viewModelScope.launch {
            try {
                withCurrentUser(
                    onNull = {
                        DMLog.e(TAG, "refreshToday() → user null")
                        _state.value = DriverUiState.Error(R.string.driver_error_user_not_logged_in)
                    }
                ) { userId, role ->
                    val roleLabel = role ?: "-"
                    val zone = TimeZone.of("Asia/Jakarta")
                    val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()
                    when (val res = loadEffectiveTodayAttendance(userId, dateEpochDay)) {
                        is AppResult.Success -> {
                            val att = res.data
                            if (att == null) {
                                DMLog.d(TAG, "No attendance record for today")
                                _state.value = DriverUiState.Idle
                            } else {
                                DMLog.d(TAG, "Attendance summary found → in=${att.clockInMillis} out=${att.clockOutMillis}")
                                val existingMessageRes = (_state.value as? DriverUiState.Working)?.messageRes
                                _state.value = workingStateFromAttendance(
                                    attendance = att,
                                    messageRes = existingMessageRes
                                )
                                DMLog.d(
                                    TAG_ATT,
                                    "[DRIVER_ATTENDANCE] refresh_today authUid=$userId role=$roleLabel " +
                                        "dateEpochDay=$dateEpochDay success=true errorClass=none dbId=$FIRESTORE_DATABASE_ID"
                                )
                            }
                        }
                        is AppResult.Failure -> {
                            DMLog.e(TAG, "refreshToday() failed: ${res.throwable.message}", res.throwable)
                            DMLog.e(
                                TAG_ATT,
                                "[DRIVER_ATTENDANCE] refresh_today authUid=$userId role=$roleLabel " +
                                    "dateEpochDay=$dateEpochDay success=false " +
                                    "errorClass=${res.throwable::class.simpleName ?: "Throwable"} " +
                                    "reason=${res.throwable.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID",
                                res.throwable
                            )
                            _state.value = DriverUiState.Error(
                                loadingErrorRes(res.throwable)
                            )
                        }
                    }
                }
            } catch (t: TimeoutCancellationException) {
                DMLog.e(TAG, "refreshToday() timed out after ${LOADING_TIMEOUT_MS}ms", t)
                DMLog.e(
                    TAG_ATT,
                    "[DRIVER_ATTENDANCE] refresh_today authUid=unknown role=- " +
                        "dateEpochDay=- success=false errorClass=TimeoutCancellationException " +
                        "reason=loading_timeout dbId=$FIRESTORE_DATABASE_ID",
                    t
                )
                _state.value = DriverUiState.Error(R.string.error_loading_timeout)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                DMLog.e(TAG, "refreshToday() crashed: ${t.message}", t)
                DMLog.e(
                    TAG_ATT,
                    "[DRIVER_ATTENDANCE] refresh_today authUid=unknown role=- " +
                        "dateEpochDay=- success=false errorClass=${t::class.simpleName ?: "Throwable"} " +
                        "reason=${t.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID",
                    t
                )
                _state.value = DriverUiState.Error(loadingErrorRes(t))
            }
        }
    }

    private suspend fun loadEffectiveTodayAttendance(
        userId: String,
        dateEpochDay: Int
    ): AppResult<TodayAttendance?> {
        return try {
            val attendanceResult = withTimeout(LOADING_TIMEOUT_MS) {
                getTodayAttendance(userId, dateEpochDay.toLong())
            }
            if (attendanceResult is AppResult.Failure) {
                return AppResult.failure(attendanceResult.throwable)
            }

            val attendanceDaily = (attendanceResult as AppResult.Success).data
            val attendanceIn = effectiveClockOrNull(attendanceDaily?.clockInMillis)
            val attendanceOut = effectiveClockOrNull(attendanceDaily?.clockOutMillis)
            val workdayResult = withTimeout(LOADING_TIMEOUT_MS) {
                getWorkDaysByRange(userId, dateEpochDay, dateEpochDay + 1)
            }
            val workday = when (workdayResult) {
                is AppResult.Success -> workdayResult.data.firstOrNull { it.dateEpochDay == dateEpochDay }
                is AppResult.Failure -> {
                    if (attendanceIn == null) {
                        return AppResult.failure(workdayResult.throwable)
                    }
                    DMLog.e(
                        TAG_ATT,
                        "[DRIVER_ATTENDANCE] workday_overlay_failed authUid=$userId role=- " +
                            "dateEpochDay=$dateEpochDay success=false " +
                            "errorClass=${workdayResult.throwable::class.simpleName ?: "Throwable"} " +
                            "reason=${workdayResult.throwable.message ?: "unknown"} dbId=$FIRESTORE_DATABASE_ID",
                        workdayResult.throwable
                    )
                    null
                }
            }

            val workdayIn = effectiveClockOrNull(workday?.clockInMillis)
            val workdayOut = effectiveClockOrNull(workday?.clockOutMillis)

            val effectiveIn = workdayIn ?: attendanceIn
            val outCandidate = workdayOut ?: attendanceOut
            val effectiveOut = if (effectiveIn != null && outCandidate != null && outCandidate > effectiveIn) {
                outCandidate
            } else {
                null
            }

            if (effectiveIn == null) {
                return AppResult.success(null)
            }

            val updatedAtMillis = attendanceDaily?.updatedAtMillis ?: 0L

            AppResult.success(
                TodayAttendance(
                    driverUid = userId,
                    epochDay = dateEpochDay.toLong(),
                    clockInMillis = effectiveIn,
                    clockOutMillis = effectiveOut,
                    updatedAtMillis = updatedAtMillis
                )
            )
        } catch (t: TimeoutCancellationException) {
            AppResult.failure(t)
        } catch (c: CancellationException) {
            throw c
        }
    }

    private fun clearLocationSnapshot() {
        viewModelScope.launch {
            when (val result = clearDriverLocation()) {
                is AppResult.Success -> {
                    DMLog.i(TAG_LOC, "clear off-duty location snapshot success")
                }
                is AppResult.Failure -> {
                    DMLog.e(
                        TAG_LOC,
                        "clear off-duty location snapshot failed: ${result.throwable.message}",
                        result.throwable
                    )
                }
            }
        }
    }

    private fun formatHmOrNull(epochMillis: Long?): String? {
        val zone = TimeZone.of("Asia/Jakarta")
        return effectiveClockOrNull(epochMillis)?.let { TimeUtil.formatHm(it, zone) }
    }

    private fun TodayAttendance.toUi(): TodayAttendanceUi = TodayAttendanceUi(
        clockInMillis = effectiveClockOrNull(clockInMillis),
        clockOutMillis = effectiveClockOrNull(clockOutMillis)
    )

    private fun effectiveClockOrNull(clockMillis: Long?): Long? =
        clockMillis?.takeIf { it > 0L }

    private fun workingStateFromAttendance(
        attendance: TodayAttendance,
        messageRes: Int? = null
    ): DriverUiState.Working {
        val todayAttendance = attendance.toUi()
        return DriverUiState.Working(
            clockedIn = todayAttendance.isWorking,
            lastTapIn = formatHmOrNull(todayAttendance.clockInMillis),
            lastTapOut = formatHmOrNull(todayAttendance.clockOutMillis),
            todayAttendance = todayAttendance,
            messageRes = messageRes
        )
    }

    fun manualRefresh() {
        DMLog.d(TAG, "manualRefresh() called")
        if (_state.value is DriverUiState.Loading && refreshTodayJob?.isActive == true) {
            DMLog.d(TAG, "manualRefresh() skipped; initial refresh already active")
            return
        }
        refreshToday()
        refreshHomeState()
    }

    fun refreshHomeState() {
        viewModelScope.launch {
            try {
                val user = authRepo.currentUser()
                val session = (user as? AppResult.Success)?.data ?: return@launch
                val uid = session.uid
                val zone = TimeZone.of("Asia/Jakarta")
                val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()

                val checklist = when (val res = withTimeout(LOADING_TIMEOUT_MS) {
                    getChecklistStatus(uid, dateEpochDay)
                }) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> null
                }
                val safety = when (val res = withTimeout(LOADING_TIMEOUT_MS) {
                    getSafetyStatus(uid, dateEpochDay)
                }) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> null
                }
                val sop = when (val res = withTimeout(LOADING_TIMEOUT_MS) { getSopStatus(uid) }) {
                    is AppResult.Success -> res.data?.sopAccepted
                    is AppResult.Failure -> null
                }
                val name = when (val res = withTimeout(LOADING_TIMEOUT_MS) {
                    getUserDisplayName(uid)
                }) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> null
                } ?: _homeState.value.driverName

                // WorkDay hari ini hanya ada kalau generateWorkdayFromAttendances sudah
                // pernah membuatnya (butuh pasangan IN/OUT nyata). Sebelum driver tap-in,
                // GetDriverTodayScheduleUseCase fallback ke workday_plans langsung supaya
                // jadwal yang sudah di-set expat tetap tampil, bukan cuma setelah tap-in.
                val schedule = when (val res = withTimeout(LOADING_TIMEOUT_MS) {
                    getDriverTodaySchedule(uid, dateEpochDay)
                }) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> null
                }

                // Pratinjau riwayat memakai use case dan jendela yang PERSIS
                // sama dengan tab "Riwayat kerja"
                // (DriverWorkHistoryViewModel): GetWorkDaysByRangeUseCase atas
                // rollingThreeMonthWindowEpochDays. Tidak ada query baru --
                // getWorkDaysByRange memang sudah di-inject ke ViewModel ini.
                // Kegagalan di sini tidak boleh menjatuhkan seluruh home state,
                // jadi jatuh ke daftar kosong dan bagian lain tetap tampil.
                val historyWindow = TimeUtil.rollingThreeMonthWindowEpochDays(zone)
                val recent = when (
                    val res = withTimeout(LOADING_TIMEOUT_MS) {
                        getWorkDaysByRange(uid, historyWindow.first, historyWindow.last)
                    }
                ) {
                    is AppResult.Success -> res.data
                        .sortedByDescending { it.dateEpochDay }
                        .take(HOME_HISTORY_PREVIEW_COUNT)
                    is AppResult.Failure -> emptyList()
                }

                _homeState.value = _homeState.value.copy(
                    loading = false,
                    driverName = name,
                    sopAccepted = sop,
                    checklistSubmitted = checklist,
                    safetySubmitted = safety,
                    recentWorkDays = recent,
                    plannedStartMillis = schedule?.plannedStartMillis,
                    plannedEndMillis = schedule?.plannedEndMillis,
                    planSource = schedule?.planSource
                )
            } catch (t: TimeoutCancellationException) {
                DMLog.e(TAG, "refreshHomeState() timed out after ${LOADING_TIMEOUT_MS}ms", t)
                _homeState.value = _homeState.value.copy(loading = false)
                _state.value = DriverUiState.Error(R.string.error_loading_timeout)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                DMLog.e(TAG, "refreshHomeState() failed: ${t.message}", t)
                _homeState.value = _homeState.value.copy(loading = false)
            }
        }
    }

    private fun loadingErrorRes(throwable: Throwable): Int =
        if (throwable.hasTimeoutCause()) {
            R.string.error_loading_timeout
        } else {
            R.string.driver_error_attendance_load_failed
        }

    private fun Throwable.hasTimeoutCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is TimeoutCancellationException) return true
            current = current.cause
        }
        return false
    }

    private suspend fun withCurrentUser(
        onNull: () -> Unit,
        block: suspend (uid: String, role: String?) -> Unit
    ) {
        when (val cu = authRepo.currentUser()) {
            is AppResult.Success -> {
                val user = cu.data
                val uid = user?.uid
                if (uid == null) {
                    DMLog.w(TAG, "Current user null")
                    onNull()
                } else {
                    block(uid, user.role.toFirestore())
                }
            }
            is AppResult.Failure -> {
                DMLog.e(TAG, "withCurrentUser() failed: ${cu.throwable.message}", cu.throwable)
                onNull()
            }
        }
    }
}
