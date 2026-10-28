package com.drivemate.showcase.feature.expat.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.model.BulkActionOutcome
import com.drivemate.showcase.core.model.BulkActionResult
import com.drivemate.showcase.core.model.ReasonCode
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_CALENDAR_TIMEZONE_ID
import com.drivemate.showcase.domain.calendar.OverrideKind
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.kind
import com.drivemate.showcase.domain.calendar.scheduledNetMinutes
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.usecase.calendar.ResolveWorkSpecForDateUseCase
import com.drivemate.showcase.domain.usecase.report.BulkApproveWorkDaysUseCase
import com.drivemate.showcase.domain.usecase.report.BulkRejectWorkDaysUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import com.drivemate.showcase.domain.usecase.report.RejectWorkDayUseCase
import com.drivemate.showcase.domain.usecase.user.GetManagedDriversUseCase
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.domain.usecase.workday.UpsertWorkdayPlanUseCase
import com.drivemate.showcase.util.UiErrorLogger
import com.drivemate.showcase.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.java.KoinJavaComponent.getKoin

private const val TAG = "DM/ExpatVM"
private const val FIRESTORE_DATABASE_ID = "drivemate-database"

private enum class BulkVerb { Approved, Rejected }

data class ExpatMonthlyCalendarSummary(
    val scheduleAvailable: Boolean = true,
    val scheduledMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
)

data class ExpatDayCalendarMeta(
    val overrideKind: OverrideKind? = null,
)

sealed interface ExpatWorkdayAuditLineUi {
    data object None : ExpatWorkdayAuditLineUi
    data class Approved(
        val approverName: String?,
        val approvedAtMillis: Long?
    ) : ExpatWorkdayAuditLineUi
    data class Rejected(
        val reason: String?,
        val rejectedAtMillis: Long?
    ) : ExpatWorkdayAuditLineUi
}

class ExpatMonthlyReviewViewModel(
    private val getRange: GetWorkDaysByRangeUseCase,
    private val bulkApprove: BulkApproveWorkDaysUseCase,
    private val bulkReject: BulkRejectWorkDaysUseCase,
    private val rejectOne: RejectWorkDayUseCase,
    private val upsertWorkdayPlan: UpsertWorkdayPlanUseCase,
    private val authRepo: AuthRepository,
    private val getManagedDrivers: GetManagedDriversUseCase,
    private val getUserDisplayName: GetUserDisplayNameUseCase
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    private val _error   = MutableStateFlow<Int?>(null)
    private val _allList = MutableStateFlow<List<WorkDay>>(emptyList())
    private val _list    = MutableStateFlow<List<WorkDay>>(emptyList())
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    private val _showPendingOnly = MutableStateFlow(false)
    private val _driverName = MutableStateFlow<String?>(null)
    private val _sessionReady = MutableStateFlow(false)
    private val _bulkMessage = MutableStateFlow<UiText?>(null)
    private val _bulkWarning = MutableStateFlow<UiText?>(null)
    private val _bulkDetails = MutableStateFlow<String?>(null)
    private val _showCopyDetails = MutableStateFlow(false)
    private val _bulkRunning = MutableStateFlow(false)
    private val _monthlyCalendarSummary = MutableStateFlow(ExpatMonthlyCalendarSummary())
    private val _dayCalendarMeta = MutableStateFlow<Map<Int, ExpatDayCalendarMeta>>(emptyMap())
    private val _auditLineByWorkdayId = MutableStateFlow<Map<String, ExpatWorkdayAuditLineUi>>(emptyMap())
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val error: StateFlow<Int?>   = _error.asStateFlow()
    val list: StateFlow<List<WorkDay>> = _list.asStateFlow()
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()
    val showPendingOnly: StateFlow<Boolean> = _showPendingOnly.asStateFlow()
    val driverName: StateFlow<String?> = _driverName.asStateFlow()
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()
    val bulkMessage: StateFlow<UiText?> = _bulkMessage.asStateFlow()
    val bulkWarning: StateFlow<UiText?> = _bulkWarning.asStateFlow()
    val bulkDetails: StateFlow<String?> = _bulkDetails.asStateFlow()
    val showCopyDetails: StateFlow<Boolean> = _showCopyDetails.asStateFlow()
    val bulkRunning: StateFlow<Boolean> = _bulkRunning.asStateFlow()
    val monthlyCalendarSummary: StateFlow<ExpatMonthlyCalendarSummary> = _monthlyCalendarSummary.asStateFlow()
    val dayCalendarMeta: StateFlow<Map<Int, ExpatDayCalendarMeta>> = _dayCalendarMeta.asStateFlow()
    val auditLineByWorkdayId: StateFlow<Map<String, ExpatWorkdayAuditLineUi>> = _auditLineByWorkdayId.asStateFlow()

    // konteks filter aktif (untuk reload setelah aksi)
    private var currentDriverUid: String? = null
    private val nameCache = mutableMapOf<String, String>()
    private val calendarCache = mutableMapOf<Int, WorkCalendarYear>()
    private val workCalendarRepository: WorkCalendarRepository by lazy { getKoin().get() }
    private val resolveWorkSpecForDate = ResolveWorkSpecForDateUseCase()
    private val jakartaZone = TimeZone.of(DEFAULT_WORK_CALENDAR_TIMEZONE_ID)
    private var lastStartEpochDay: Int = 0
    private var lastEndEpochDay: Int = 0
    private var currentAuthUid: String = "-"
    private var currentRole: String = "-"
    private var readyDriverUid: String? = null

    fun saveWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long
    ) = viewModelScope.launch {
        when (val res = upsertWorkdayPlan(driverUid, epochDay, plannedStartMillis, plannedEndMillis, "expat")) {
            is AppResult.Success -> {
                val targetUid = currentDriverUid ?: driverUid
                load(targetUid)
            }
            is AppResult.Failure -> {
                _error.value = R.string.expat_error_review_load_failed
                UiErrorLogger.e(TAG, "workday plan upsert failed", res.throwable)
            }
        }
    }

    fun onResumeFromDetail(driverUid: String) {
        if (_loading.value || _bulkRunning.value) return
        if (!_sessionReady.value) return
        if (currentDriverUid != driverUid) return
        load(driverUid)
    }

    fun togglePendingFilter() {
        _showPendingOnly.value = !_showPendingOnly.value
        _selected.value = emptySet()
        applyPendingFilter(_allList.value)
    }

    /**
     * [keepBulkFeedback] true hanya untuk muat-ulang setelah aksi massal: ringkasan hasil dan
     * peringatan "dilewati/gagal" dibuat tepat sebelum muat-ulang, jadi tidak boleh ikut terhapus.
     */
    fun load(driverUid: String, keepBulkFeedback: Boolean = false) = viewModelScope.launch {
        val zone = TimeZone.of("Asia/Jakarta")
        val window = TimeUtil.rollingThreeMonthWindowEpochDays(zone)
        val startEpochDay = window.first
        val endEpochDay = window.last
        lastStartEpochDay = startEpochDay
        lastEndEpochDay = endEpochDay

        if (!isProbablyUid(driverUid)) {
            _sessionReady.value = false
            readyDriverUid = null
            _selected.value = emptySet()
            _allList.value = emptyList()
            _list.value = emptyList()
            _error.value = R.string.expat_error_invalid_driver_uid
            DMLog.w(TAG, "Reject load: driver id is not a UID (length=${driverUid.length})")
            logReview(
                event = "load_rejected",
                role = "-",
                dateEpochDay = endEpochDay,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                count = 0,
                success = false,
                reason = "invalid_driver_uid",
                errorClass = "InvalidInput"
            )
            return@launch
        }
        if (_loading.value) {
            DMLog.d(TAG, "Skip load: still loading…")
            return@launch
        }
        _loading.value = true
        _error.value = null
        _sessionReady.value = false
        readyDriverUid = null
        currentDriverUid = driverUid
        _allList.value = emptyList()
        _list.value = emptyList()
        _selected.value = emptySet()
        _driverName.value = nameCache[driverUid]
        _monthlyCalendarSummary.value = ExpatMonthlyCalendarSummary()
        _dayCalendarMeta.value = emptyMap()
        _auditLineByWorkdayId.value = emptyMap()
        if (!keepBulkFeedback) clearBulkMessage()

        try {
            DMLog.i(TAG, "Review load requested")
            if (currentDriverUid != null && currentDriverUid != driverUid) {
                DMLog.w(
                    TAG,
                    "Review driver changed while a load was requested"
                )
            }
            val currentUser = when (val res = authRepo.currentUser()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
            val authUid = currentUser?.uid
            if (authUid.isNullOrBlank()) {
                _error.value = R.string.expat_error_user_not_logged_in
                logReview(
                    event = "load_rejected",
                    role = "-",
                    dateEpochDay = endEpochDay,
                    startEpochDay = startEpochDay,
                    endEpochDay = endEpochDay,
                    count = 0,
                    success = false,
                    reason = "auth_missing",
                    errorClass = "NoSession"
                )
                return@launch
            }

            val roleLower = currentUser.role.toFirestore()
            if (roleLower.isBlank()) {
                _error.value = R.string.expat_error_role_invalid
                logReview(
                    event = "load_rejected",
                    role = "-",
                    dateEpochDay = endEpochDay,
                    startEpochDay = startEpochDay,
                    endEpochDay = endEpochDay,
                    count = 0,
                    success = false,
                    reason = "role_unresolved",
                    errorClass = "RoleUnresolved"
                )
                return@launch
            }
            // Persist auth context for approve/reject actions.
            currentAuthUid = authUid
            currentRole = roleLower
            DMLog.i(TAG, "[DRIVER_HISTORY] auth_cached screen=expat_review role=$roleLower")

            DMLog.i(
                TAG,
                "Query guard -> role=$roleLower dbId=$FIRESTORE_DATABASE_ID"
            )

            if (roleLower != "admin") {
                if (roleLower != "expat") {
                    _error.value = R.string.expat_error_not_allowed
                    logReview(
                        event = "load_rejected",
                        role = roleLower,
                        dateEpochDay = endEpochDay,
                        startEpochDay = startEpochDay,
                        endEpochDay = endEpochDay,
                        count = 0,
                        success = false,
                        reason = "role_not_allowed",
                        errorClass = "Authorization"
                    )
                    return@launch
                }

                val managed = when (val res = getManagedDrivers(authUid)) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> {
                        _error.value = if (isPermissionDeniedError(res.throwable)) {
                            R.string.expat_error_access_denied_check_filters
                        } else {
                            R.string.expat_error_managed_drivers_load_failed
                        }
                        UiErrorLogger.e(TAG, "managed drivers load failed", res.throwable)
                        logReview(
                            event = "managed_fetch_failed",
                            role = roleLower,
                            dateEpochDay = endEpochDay,
                            startEpochDay = startEpochDay,
                            endEpochDay = endEpochDay,
                            count = 0,
                            success = false,
                            reason = failureReason(res.throwable),
                            errorClass = res.throwable::class.simpleName ?: "Throwable"
                        )
                        return@launch
                    }
                }
                if (managed.isEmpty()) {
                    _error.value = R.string.expat_error_no_managed_drivers
                    logReview(
                        event = "managed_empty",
                        role = roleLower,
                        dateEpochDay = endEpochDay,
                        startEpochDay = startEpochDay,
                        endEpochDay = endEpochDay,
                        count = 0,
                        success = false,
                        reason = "managed_empty",
                        errorClass = "NoManagedDrivers"
                    )
                    return@launch
                }
                if (managed.none { it.uid == driverUid }) {
                    _error.value = R.string.expat_error_not_allowed
                    logReview(
                        event = "load_rejected",
                        role = roleLower,
                        dateEpochDay = endEpochDay,
                        startEpochDay = startEpochDay,
                        endEpochDay = endEpochDay,
                        count = 0,
                        success = false,
                        reason = "manager_mismatch",
                        errorClass = "Authorization"
                    )
                    return@launch
                }
            }

            logReview(
                event = "load_start",
                role = roleLower,
                dateEpochDay = endEpochDay,
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                count = 0,
                success = true,
                reason = null,
                errorClass = "none"
            )
            DMLog.i(
                TAG,
                "Range query -> role=$roleLower " +
                    "startEpochDay=$startEpochDay endEpochDay=$endEpochDay"
            )

            val res = getRange(driverUid, startEpochDay, endEpochDay)
            if (res is AppResult.Success) {
                _allList.value = res.data
                applyPendingFilter(res.data)
                _auditLineByWorkdayId.value = buildAuditLines(res.data)
                computeCalendarUiState(
                    workdays = res.data,
                    endEpochDay = endEpochDay
                )
                _sessionReady.value = roleLower == "expat"
                readyDriverUid = if (_sessionReady.value) driverUid else null
                val hint = res.data.firstOrNull { !it.driverName.isNullOrBlank() }?.driverName?.trim()
                if (!hint.isNullOrBlank()) {
                    nameCache[driverUid] = hint
                    _driverName.value = hint
                } else {
                    resolveDriverName(driverUid)
                }
                logReview(
                    event = "range_fetch",
                    role = roleLower,
                    dateEpochDay = endEpochDay,
                    startEpochDay = startEpochDay,
                    endEpochDay = endEpochDay,
                    count = res.data.size,
                    success = true,
                    reason = null,
                    errorClass = "none"
                )
                DMLog.i(
                    TAG,
                    "Range query result -> role=$roleLower " +
                        "startEpochDay=$startEpochDay endEpochDay=$endEpochDay count=${res.data.size}"
                )
            } else if (res is AppResult.Failure) {
                _error.value = if (isPermissionDeniedError(res.throwable)) {
                    R.string.expat_error_access_denied_check_filters
                } else {
                    R.string.expat_error_review_load_failed
                }
                _sessionReady.value = false
                readyDriverUid = null
                _selected.value = emptySet()
                _allList.value = emptyList()
                _list.value = emptyList()
                _monthlyCalendarSummary.value = ExpatMonthlyCalendarSummary()
                _dayCalendarMeta.value = emptyMap()
                _auditLineByWorkdayId.value = emptyMap()
                UiErrorLogger.e(TAG, "range fetch failed", res.throwable)
                logReview(
                    event = "range_fetch",
                    role = roleLower,
                    dateEpochDay = endEpochDay,
                    startEpochDay = startEpochDay,
                    endEpochDay = endEpochDay,
                    count = 0,
                    success = false,
                    reason = failureReason(res.throwable),
                    errorClass = res.throwable::class.simpleName ?: "Throwable"
                )
                DMLog.e(TAG, "Load failed", res.throwable)
            }
        } catch (c: CancellationException) {
            DMLog.d(TAG, "Load cancelled (lifecycle/navigation)")
            throw c
        } finally {
            _loading.value = false
        }
    }

    fun toggle(id: String) {
        if (_bulkRunning.value) {
            DMLog.w(TAG, "Toggle blocked: bulk action running")
            return
        }
        if (_list.value.none { it.id == id }) {
            DMLog.w(TAG, "Toggle blocked: item not in current dataset")
            return
        }
        _selected.value = _selected.value.toMutableSet().also {
            val added = it.add(id)
            if (!added) it.remove(id)
        }
        DMLog.d(TAG, "Toggle → selected=${_selected.value.size}")
    }

    fun approve() = viewModelScope.launch {
        if (!hasAuthorizedBulkContext()) {
            _error.value = R.string.expat_error_not_allowed
            DMLog.w(TAG, "Approve skipped: unauthorized or dataset not ready")
            return@launch
        }
        if (!hasSelectionInCurrentDataset()) {
            _selected.value = emptySet()
            DMLog.w(TAG, "Approve skipped: selection not in current dataset")
            return@launch
        }
        if (_bulkRunning.value) {
            DMLog.w(TAG, "[DRIVER_HISTORY] approve_blocked reason=bulk_running")
            return@launch
        }
        if (currentAuthUid == "-" || currentAuthUid.isBlank()) {
            _error.value = R.string.expat_error_user_not_logged_in
            DMLog.w(
                TAG,
                "[DRIVER_HISTORY] approve_blocked reason=auth_missing role=$currentRole"
            )
            return@launch
        }
        if (_loading.value) {
            DMLog.w(TAG, "Approve skipped: still loading")
            return@launch
        }
        val ids = _selected.value.toList()

        DMLog.i(
            TAG,
            "[DRIVER_HISTORY] approve_start role=$currentRole count=${ids.size}"
        )
        clearBulkMessage()
        _loading.value = true
        _bulkRunning.value = true
        var shouldReload = false
        try {
            val res = bulkApprove(ids)
            handleBulkResult(res, verb = BulkVerb.Approved)
            logReview(
                event = "approve_bulk",
                role = currentRole,
                dateEpochDay = lastEndEpochDay,
                startEpochDay = lastStartEpochDay,
                endEpochDay = lastEndEpochDay,
                count = res.successCount,
                success = res.successCount > 0,
                reason = null,
                errorClass = "none"
            )
            if (res.successCount > 0) {
                DMLog.i(TAG, "Approve success (${res.successCount} items). Reload…")
                shouldReload = true
            }
        } catch (c: CancellationException) {
            DMLog.d(TAG, "Approve cancelled (lifecycle/navigation)")
            throw c
        } finally {
            _loading.value = false
            _bulkRunning.value = false
            if (shouldReload) reloadAfterAction()
        }
    }

    fun reject(id: String, note: String) = viewModelScope.launch {
        if (_loading.value) {
            DMLog.w(TAG, "Reject skipped: still loading")
            return@launch
        }
        if (currentAuthUid == "-" || currentAuthUid.isBlank()) {
            _error.value = R.string.expat_error_user_not_logged_in
            DMLog.w(TAG, "Reject skipped: auth missing")
            return@launch
        }
        DMLog.w(TAG, "Reject start; noteLength=${note.length}")
        _loading.value = true
        var shouldReload = false
        try {
            when (val res = rejectOne(id, note)) {
                is AppResult.Success -> {
                    logReview(
                        event = "reject_single",
                        role = currentRole,
                        dateEpochDay = lastEndEpochDay,
                        startEpochDay = lastStartEpochDay,
                        endEpochDay = lastEndEpochDay,
                        count = 1,
                        success = true,
                        reason = null,
                        errorClass = "none"
                    )
                    DMLog.w(TAG, "Reject success. Reload…")
                    shouldReload = true
                }
                is AppResult.Failure -> {
                    _error.value = R.string.expat_error_reject_failed
                    UiErrorLogger.e(TAG, "reject failed", res.throwable)
                    logReview(
                        event = "reject_single",
                        role = currentRole,
                        dateEpochDay = lastEndEpochDay,
                        startEpochDay = lastStartEpochDay,
                        endEpochDay = lastEndEpochDay,
                        count = 1,
                        success = false,
                        reason = failureReason(res.throwable),
                        errorClass = res.throwable::class.simpleName ?: "Throwable"
                    )
                    DMLog.e(TAG, "Reject failed", res.throwable)
                }
            }
        } catch (c: CancellationException) {
            DMLog.d(TAG, "Reject cancelled (lifecycle/navigation)")
            throw c
        } finally {
            _loading.value = false
            if (shouldReload) reloadAfterAction()
        }
    }

    fun rejectSelected(note: String) = viewModelScope.launch {
        if (!hasAuthorizedBulkContext()) {
            _error.value = R.string.expat_error_not_allowed
            DMLog.w(TAG, "Reject skipped: unauthorized or dataset not ready")
            return@launch
        }
        if (!hasSelectionInCurrentDataset()) {
            _selected.value = emptySet()
            DMLog.w(TAG, "Reject skipped: selection not in current dataset")
            return@launch
        }
        if (_bulkRunning.value) {
            DMLog.w(TAG, "[DRIVER_HISTORY] reject_blocked reason=bulk_running")
            return@launch
        }
        if (currentAuthUid == "-" || currentAuthUid.isBlank()) {
            _bulkMessage.value = UiText(R.string.expat_bulk_error_user_not_logged_in)
            _showCopyDetails.value = false
            DMLog.w(
                TAG,
                "[DRIVER_HISTORY] reject_blocked reason=auth_missing role=$currentRole"
            )
            return@launch
        }
        if (_loading.value) {
            DMLog.w(TAG, "Reject skipped: still loading")
            return@launch
        }
        if (note.isBlank()) {
            _bulkWarning.value = UiText(R.string.expat_bulk_warning_reject_reason_required)
            _showCopyDetails.value = false
            return@launch
        }
        val ids = _selected.value.toList()
        DMLog.i(
            TAG,
            "[DRIVER_HISTORY] reject_start role=$currentRole count=${ids.size}"
        )
        clearBulkMessage()
        _loading.value = true
        _bulkRunning.value = true
        var shouldReload = false
        try {
            val res = bulkReject(ids, note)
            handleBulkResult(res, verb = BulkVerb.Rejected)
            logReview(
                event = "reject_bulk",
                role = currentRole,
                dateEpochDay = lastEndEpochDay,
                startEpochDay = lastStartEpochDay,
                endEpochDay = lastEndEpochDay,
                count = res.successCount,
                success = res.successCount > 0,
                reason = null,
                errorClass = "none"
            )
            if (res.successCount > 0) {
                DMLog.i(TAG, "Reject success (${res.successCount} items). Reload…")
                shouldReload = true
            }
        } catch (c: CancellationException) {
            DMLog.d(TAG, "Reject cancelled (lifecycle/navigation)")
            throw c
        } finally {
            _loading.value = false
            _bulkRunning.value = false
            if (shouldReload) reloadAfterAction()
        }
    }

    fun logGroupingApplied(itemCount: Int) {
        val zone = TimeZone.of("Asia/Jakarta")
        val endEpochDay = if (lastEndEpochDay == 0) TimeUtil.todayEpochDay(zone).toInt() else lastEndEpochDay
        val startEpochDay = if (lastStartEpochDay == 0) {
            TimeUtil.rollingThreeMonthWindowEpochDays(endEpochDay).first
        } else {
            lastStartEpochDay
        }
        logReview(
            event = "grouping_applied",
            role = currentRole,
            dateEpochDay = endEpochDay,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            count = itemCount,
            success = true,
            reason = null,
            errorClass = "none"
        )
    }

    /** Reload data bulan terakhir setelah approve/reject */
    private fun reloadAfterAction() {
        val d = currentDriverUid
        if (d != null) {
            // bisa juga clear selection dulu agar UX bersih
            _selected.value = emptySet()
            load(d, keepBulkFeedback = true)
        } else {
            DMLog.w(TAG, "reloadAfterAction skipped: context missing")
        }
    }

    private fun isProbablyUid(key: String): Boolean =
        key.length >= 20 && !key.contains("@")

    private suspend fun resolveDriverName(uid: String) {
        val cached = nameCache[uid]
        if (!cached.isNullOrBlank()) {
            _driverName.value = cached
            return
        }
        val resolved = when (val res = getUserDisplayName(uid)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> null
        }
        val name = resolved
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.startsWith("User-") }
        if (!name.isNullOrBlank()) {
            nameCache[uid] = name
        }
        _driverName.value = name
    }

    private fun logReview(
        event: String,
        role: String,
        dateEpochDay: Int,
        startEpochDay: Int,
        endEpochDay: Int,
        count: Int,
        success: Boolean,
        reason: String?,
        errorClass: String
    ) {
        val base =
            "[DRIVER_HISTORY] $event role=$role " +
                "dateEpochDay=$dateEpochDay rangeStartEpochDay=$startEpochDay " +
                "rangeEndEpochDay=$endEpochDay count=$count success=$success " +
                "errorClass=$errorClass"
        if (success) {
            DMLog.i(TAG, base)
        } else {
            DMLog.e(TAG, "$base reason=${reason ?: "unknown"}")
        }
    }

    /**
     * Calendar-derived summary and reason chips based on SSOT rules from docs/work_calendar/SSOT.md.
     */
    private suspend fun computeCalendarUiState(
        workdays: List<WorkDay>,
        endEpochDay: Int
    ) {
        if (workdays.isEmpty()) {
            _monthlyCalendarSummary.value = ExpatMonthlyCalendarSummary()
            _dayCalendarMeta.value = emptyMap()
            return
        }

        val activeYear = resolveActiveYear(endEpochDay)
        val years = buildSet {
            add(activeYear)
            workdays.forEach { add(LocalDate.fromEpochDays(it.dateEpochDay).year) }
        }
        val (calendarByYear, scheduleAvailable) = loadCalendarsByYear(
            years = years,
            activeYear = activeYear
        )

        val targetMonth = LocalDate.fromEpochDays(endEpochDay)
        val dayMeta = mutableMapOf<Int, ExpatDayCalendarMeta>()
        val scheduledCountedDays = mutableSetOf<Int>()
        var scheduledMinutes = 0
        var actualMinutes = 0
        var overtimeMinutes = 0

        workdays.forEach { workday ->
            val date = LocalDate.fromEpochDays(workday.dateEpochDay)
            val calendar = calendarByYear[date.year]
            val computation = computeDayComputation(workday, calendar)

            if (computation.overrideKind != null) {
                dayMeta[workday.dateEpochDay] = ExpatDayCalendarMeta(overrideKind = computation.overrideKind)
            }
            if (date.year == targetMonth.year && date.month == targetMonth.month) {
                if (scheduleAvailable && calendar != null && scheduledCountedDays.add(workday.dateEpochDay)) {
                    scheduledMinutes += computation.scheduledMinutes
                }
                actualMinutes += computation.actualMinutes
                overtimeMinutes += computation.overtimeMinutes
            }
        }

        _monthlyCalendarSummary.value = ExpatMonthlyCalendarSummary(
            scheduleAvailable = scheduleAvailable,
            scheduledMinutes = if (scheduleAvailable) scheduledMinutes else 0,
            actualMinutes = actualMinutes,
            overtimeMinutes = overtimeMinutes
        )
        _dayCalendarMeta.value = dayMeta
    }

    private suspend fun resolveActiveYear(endEpochDay: Int): Int {
        return when (val result = workCalendarRepository.getActiveYear(endEpochDay, jakartaZone.id)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                DMLog.w(
                    TAG,
                    "Failed to resolve active calendar year. Fallback to epochDay year=${LocalDate.fromEpochDays(endEpochDay).year}",
                    result.throwable
                )
                LocalDate.fromEpochDays(endEpochDay).year
            }
        }
    }

    private suspend fun loadCalendarsByYear(
        years: Set<Int>,
        activeYear: Int
    ): Pair<Map<Int, WorkCalendarYear>, Boolean> {
        val calendars = mutableMapOf<Int, WorkCalendarYear>()
        var scheduleAvailable = true
        years.sorted().forEach { year ->
            val cached = calendarCache[year]
            if (cached != null) {
                calendars[year] = cached
                return@forEach
            }
            when (val result = workCalendarRepository.getYear(year)) {
                is AppResult.Success -> {
                    calendarCache[year] = result.data
                    calendars[year] = result.data
                }
                is AppResult.Failure -> {
                    if (year == activeYear) {
                        scheduleAvailable = false
                    }
                    DMLog.w(
                        TAG,
                        "Calendar load failed year=$year activeYear=$activeYear",
                        result.throwable
                    )
                }
            }
        }
        return calendars to scheduleAvailable
    }

    private fun computeDayComputation(
        workday: WorkDay,
        calendar: WorkCalendarYear?
    ): DayComputation {
        val date = LocalDate.fromEpochDays(workday.dateEpochDay)
        val isCompleted = workday.clockInMillis > 0L && workday.clockOutMillis > 0L
        val actualMinutes = if (isCompleted) workday.totalMinutes.coerceAtLeast(0) else 0
        val overtimeMinutes = if (isCompleted) workday.overtimeMinutes.coerceAtLeast(0) else 0
        val scheduledMinutes = calendar
            ?.let { resolveWorkSpecForDate(it, workday.dateEpochDay) }
            ?.scheduledNetMinutes()
            ?: 0
        val overrideKind = calendar?.overrides?.firstOrNull { it.date == date }?.kind
        return DayComputation(
            scheduledMinutes = scheduledMinutes,
            actualMinutes = actualMinutes,
            overtimeMinutes = overtimeMinutes,
            overrideKind = overrideKind
        )
    }

    private data class DayComputation(
        val scheduledMinutes: Int,
        val actualMinutes: Int,
        val overtimeMinutes: Int,
        val overrideKind: OverrideKind?
    )

    private fun clearBulkMessage() {
        _bulkMessage.value = null
        _bulkWarning.value = null
        _bulkDetails.value = null
        _showCopyDetails.value = false
    }

    private fun handleBulkResult(res: BulkActionResult, verb: BulkVerb) {
        _bulkMessage.value = buildBulkSummary(res, verb)
        val hasPermissionDenied = res.items.any { it.reasonCode == ReasonCode.PERMISSION_DENIED }
        _bulkWarning.value = buildBulkWarning(res, hasPermissionDenied)
        val details = buildBulkDetails(res)
        _bulkDetails.value = details
        _showCopyDetails.value = !details.isNullOrBlank()
        DMLog.i(
            TAG,
            "[DRIVER_HISTORY] ${res.action.name.lowercase()}_bulk_done " +
                "success=${res.successCount} skipped=${res.skippedCount} failed=${res.failedCount}"
        )
        res.items.filter { it.outcome != BulkActionOutcome.SUCCESS }.forEach { item ->
            DMLog.d(
                TAG,
                "[DRIVER_HISTORY] bulk_item action=${res.action} " +
                    "outcome=${item.outcome} reasonCode=${item.reasonCode} " +
                    "epochDay=${item.dateEpochDay ?: "-"}"
            )
        }
    }

    private fun buildBulkSummary(res: BulkActionResult, verb: BulkVerb): UiText {
        val issueCount = res.skippedCount + res.failedCount
        return when {
            res.successCount > 0 && issueCount > 0 -> {
                val resId = when (verb) {
                    BulkVerb.Approved -> R.string.expat_bulk_summary_approve_with_issues
                    BulkVerb.Rejected -> R.string.expat_bulk_summary_reject_with_issues
                }
                UiText(resId, listOf(res.successCount, issueCount))
            }
            res.successCount == 0 && issueCount > 0 ->
                UiText(R.string.expat_bulk_summary_none_success, listOf(issueCount))
            res.successCount > 0 -> {
                val resId = when (verb) {
                    BulkVerb.Approved -> R.string.expat_bulk_summary_approve_success_only
                    BulkVerb.Rejected -> R.string.expat_bulk_summary_reject_success_only
                }
                UiText(resId, listOf(res.successCount))
            }
            else -> UiText(R.string.expat_bulk_summary_no_items)
        }
    }

    private fun buildBulkWarning(res: BulkActionResult, hasPermissionDenied: Boolean): UiText? {
        return when {
            hasPermissionDenied -> UiText(R.string.expat_bulk_warning_permission_denied)
            res.skippedCount > 0 && res.failedCount > 0 ->
                UiText(R.string.expat_bulk_warning_skipped_failed, listOf(res.skippedCount, res.failedCount))
            res.skippedCount > 0 -> UiText(R.string.expat_bulk_warning_skipped_only, listOf(res.skippedCount))
            res.failedCount > 0 -> UiText(R.string.expat_bulk_warning_failed_only, listOf(res.failedCount))
            else -> null
        }
    }

    private fun buildBulkDetails(res: BulkActionResult): String? {
        val lines = res.items
            .filter { it.outcome != BulkActionOutcome.SUCCESS }
            .joinToString(separator = "\n") { item ->
                val driver = item.driverUid ?: "-"
                val epoch = item.dateEpochDay?.toString() ?: "-"
                val message = item.reasonMessage?.ifBlank { "-" } ?: "-"
                "${item.docId} | $driver | $epoch | ${item.outcome} | ${item.reasonCode} | $message"
        }
        return lines.ifBlank { null }
    }

    private fun applyPendingFilter(source: List<WorkDay>) {
        val filtered = if (_showPendingOnly.value) {
            source.filter { it.approvalStatus() == ApprovalStatus.PENDING }
        } else {
            source
        }
        _list.value = filtered
        sanitizeSelection(filtered)
    }

    private fun sanitizeSelection(visible: List<WorkDay>) {
        val validIds = visible.mapTo(mutableSetOf()) { it.id }
        val sanitized = _selected.value.filterTo(mutableSetOf()) { it in validIds }
        if (sanitized.size != _selected.value.size) {
            _selected.value = sanitized
        }
    }

    private fun hasAuthorizedBulkContext(): Boolean {
        if (!_sessionReady.value) return false
        if (currentRole != "expat") return false
        val driverUid = currentDriverUid ?: return false
        return readyDriverUid == driverUid
    }

    private fun hasSelectionInCurrentDataset(): Boolean {
        val selectedIds = _selected.value
        if (selectedIds.isEmpty()) return false
        val currentIds = _list.value.mapTo(mutableSetOf()) { it.id }
        if (currentIds.isEmpty()) return false
        return selectedIds.all { it in currentIds }
    }

    /** Kategori kegagalan untuk log; pesan mentah backend bisa memuat path dokumen (memuat uid). */
    private fun failureReason(throwable: Throwable?): String =
        if (isPermissionDeniedError(throwable)) "permission_denied" else "unknown"

    private fun isPermissionDeniedError(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun buildAuditLines(workdays: List<WorkDay>): Map<String, ExpatWorkdayAuditLineUi> {
        return workdays.associate { workday ->
            workday.id to workday.toAuditLineUi()
        }
    }

    private fun WorkDay.toAuditLineUi(): ExpatWorkdayAuditLineUi {
        return when (approvalStatus()) {
            ApprovalStatus.APPROVED -> {
                ExpatWorkdayAuditLineUi.Approved(
                    approverName = approverName?.trim()?.takeIf { it.isNotEmpty() },
                    approvedAtMillis = approvedAtMillis?.takeIf { it > 0L }
                )
            }
            ApprovalStatus.REJECTED -> {
                ExpatWorkdayAuditLineUi.Rejected(
                    reason = rejectReason
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: rejectionNote?.trim()?.takeIf { it.isNotEmpty() },
                    rejectedAtMillis = rejectedAtMillis?.takeIf { it > 0L }
                )
            }
            ApprovalStatus.PENDING -> ExpatWorkdayAuditLineUi.None
        }
    }
}
