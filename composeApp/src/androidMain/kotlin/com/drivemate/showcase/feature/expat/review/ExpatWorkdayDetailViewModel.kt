package com.drivemate.showcase.feature.expat.review

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.SopStatus
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.isFinalizedByStatusOrActor
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_CALENDAR_TIMEZONE_ID
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.scheduledNetMinutes
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.domain.usecase.calendar.ResolveWorkSpecForDateUseCase
import com.drivemate.showcase.domain.usecase.report.ApproveWorkDayUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkdayDetailUseCase
import com.drivemate.showcase.domain.usecase.report.RejectWorkDayUseCase
import com.drivemate.showcase.domain.usecase.user.GetDriverSopStatusUseCase
import com.drivemate.showcase.domain.usecase.user.GetUserDisplayNameUseCase
import com.drivemate.showcase.util.UiErrorLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.java.KoinJavaComponent.getKoin

data class ExpatWorkdayDetailState(
    val loading: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val data: WorkDay? = null
)

sealed interface ExpatReviewActionState {
    data object Idle : ExpatReviewActionState
    data object Submitting : ExpatReviewActionState
    data class Success(@param:StringRes val messageRes: Int) : ExpatReviewActionState
    data class Error(@param:StringRes val messageRes: Int) : ExpatReviewActionState
}

data class ExpatWorkdayScheduleSummary(
    val scheduleAvailable: Boolean = true,
    val isOffDay: Boolean = false,
    val scheduledMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val overtimeMinutes: Int = 0
)

sealed interface ExpatWorkdayReviewAuditUi {
    data class Approved(
        val approverName: String?,
        val approvedAtMillis: Long?,
        val approvalNote: String?
    ) : ExpatWorkdayReviewAuditUi

    data class Rejected(
        val reason: String?,
        val rejectionNote: String?,
        val rejectedAtMillis: Long?,
        val rejectedByName: String?
    ) : ExpatWorkdayReviewAuditUi
}

class ExpatWorkdayDetailViewModel(
    private val getDetail: GetWorkdayDetailUseCase,
    private val approveWorkDay: ApproveWorkDayUseCase,
    private val rejectWorkDay: RejectWorkDayUseCase,
    private val getDriverSopStatus: GetDriverSopStatusUseCase,
    private val getUserDisplayName: GetUserDisplayNameUseCase,
    private val authRepo: AuthRepository
) : ViewModel() {

    private companion object { const val TAG = "DM/ExpatWorkdayDetail" }

    private val _state = MutableStateFlow(ExpatWorkdayDetailState())
    val state: StateFlow<ExpatWorkdayDetailState> = _state

    private val _driverName = MutableStateFlow<String?>(null)
    val driverName: StateFlow<String?> = _driverName

    private val _sopStatus = MutableStateFlow<SopStatus?>(null)
    val sopStatus: StateFlow<SopStatus?> = _sopStatus

    private val _actionState = MutableStateFlow<ExpatReviewActionState>(ExpatReviewActionState.Idle)
    val actionState: StateFlow<ExpatReviewActionState> = _actionState
    private val _scheduleSummary = MutableStateFlow(ExpatWorkdayScheduleSummary())
    val scheduleSummary: StateFlow<ExpatWorkdayScheduleSummary> = _scheduleSummary
    private val _canReviewActions = MutableStateFlow(false)
    val canReviewActions: StateFlow<Boolean> = _canReviewActions
    private val _reviewAudit = MutableStateFlow<ExpatWorkdayReviewAuditUi?>(null)
    val reviewAudit: StateFlow<ExpatWorkdayReviewAuditUi?> = _reviewAudit
    private val _reviewLockedByFinalized = MutableStateFlow(false)
    val reviewLockedByFinalized: StateFlow<Boolean> = _reviewLockedByFinalized

    private val workCalendarRepository: WorkCalendarRepository by lazy { getKoin().get() }
    private val resolveWorkSpecForDate = ResolveWorkSpecForDateUseCase()
    private val jakartaZone = TimeZone.of(DEFAULT_WORK_CALENDAR_TIMEZONE_ID)
    private val calendarCache = mutableMapOf<Int, WorkCalendarYear>()

    private var currentAuthUid: String = "-"
    private var currentRole: String = "-"

    fun load(workdayId: String) {
        if (_state.value.loading) return
        _state.value = ExpatWorkdayDetailState(loading = true)
        _actionState.value = ExpatReviewActionState.Idle
        _scheduleSummary.value = ExpatWorkdayScheduleSummary()
        _reviewAudit.value = null
        _reviewLockedByFinalized.value = false

        viewModelScope.launch {
            val (authUid, roleLabel) = resolveAuthContext()
            currentAuthUid = authUid
            currentRole = roleLabel
            _canReviewActions.value = canCurrentUserReview()

            when (val res = getDetail(workdayId)) {
                is AppResult.Success -> {
                    val data = res.data
                    if (data == null) {
                        _state.value = ExpatWorkdayDetailState(errorRes = R.string.expat_error_workday_detail_not_found)
                        logReview("load_detail", null, false, "NotFound")
                        return@launch
                    }
                    _state.value = ExpatWorkdayDetailState(data = data)
                    val finalized = data.isFinalizedByStatusOrActor()
                    _reviewLockedByFinalized.value = finalized
                    _canReviewActions.value = canCurrentUserReview() && !finalized
                    _reviewAudit.value = resolveReviewAuditUi(data)
                    resolveDriverName(data.driverUid)
                    resolveSopStatus(data.driverUid)
                    resolveScheduleSummary(data)
                    logReview("load_detail", data.dateEpochDay, true, "none")
                }
                is AppResult.Failure -> {
                    _state.value = ExpatWorkdayDetailState(errorRes = R.string.expat_error_workday_detail_load_failed)
                    UiErrorLogger.e(TAG, "load detail failed", res.throwable)
                    logReview(
                        "load_detail",
                        null,
                        false,
                        res.throwable::class.simpleName ?: "Throwable"
                    )
                }
            }
        }
    }

    fun approve(note: String?) {
        val data = _state.value.data ?: return
        if (data.isFinalizedByStatusOrActor()) {
            _actionState.value = ExpatReviewActionState.Error(R.string.expat_error_workday_finalized_locked)
            return
        }
        if (!canCurrentUserReview()) {
            _actionState.value = ExpatReviewActionState.Error(R.string.expat_error_not_allowed)
            return
        }
        if (_actionState.value is ExpatReviewActionState.Submitting) return
        _actionState.value = ExpatReviewActionState.Submitting

        viewModelScope.launch {
            try {
                when (val res = approveWorkDay(data.id, note)) {
                    is AppResult.Success -> {
                        _actionState.value = ExpatReviewActionState.Success(R.string.expat_success_workday_approved)
                        logReview("approve", data.dateEpochDay, true, "none")
                        load(data.id)
                    }
                    is AppResult.Failure -> {
                        _actionState.value = ExpatReviewActionState.Error(R.string.expat_error_workday_approve_failed)
                        UiErrorLogger.e(TAG, "approve failed", res.throwable)
                        logReview(
                            "approve",
                            data.dateEpochDay,
                            false,
                            res.throwable::class.simpleName ?: "Throwable"
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            }
        }
    }

    fun reject(reason: String) {
        val data = _state.value.data ?: return
        if (data.isFinalizedByStatusOrActor()) {
            _actionState.value = ExpatReviewActionState.Error(R.string.expat_error_workday_finalized_locked)
            return
        }
        if (!canCurrentUserReview()) {
            _actionState.value = ExpatReviewActionState.Error(R.string.expat_error_not_allowed)
            return
        }
        if (reason.isBlank()) {
            _actionState.value = ExpatReviewActionState.Error(R.string.expat_dialog_reject_reason_error)
            return
        }
        if (_actionState.value is ExpatReviewActionState.Submitting) return
        _actionState.value = ExpatReviewActionState.Submitting

        viewModelScope.launch {
            try {
                when (val res = rejectWorkDay(data.id, reason)) {
                    is AppResult.Success -> {
                        _actionState.value = ExpatReviewActionState.Success(R.string.expat_success_workday_rejected)
                        logReview("reject", data.dateEpochDay, true, "none")
                        load(data.id)
                    }
                    is AppResult.Failure -> {
                        _actionState.value = ExpatReviewActionState.Error(R.string.expat_error_workday_reject_failed)
                        UiErrorLogger.e(TAG, "reject failed", res.throwable)
                        logReview(
                            "reject",
                            data.dateEpochDay,
                            false,
                            res.throwable::class.simpleName ?: "Throwable"
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            }
        }
    }

    fun clearActionMessage() {
        if (_actionState.value !is ExpatReviewActionState.Submitting) {
            _actionState.value = ExpatReviewActionState.Idle
        }
    }

    private suspend fun resolveDriverName(uid: String) {
        val name = when (val res = getUserDisplayName(uid)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> null
        }
        _driverName.value = name
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.startsWith("User-") }
    }

    private suspend fun resolveSopStatus(uid: String) {
        val status = when (val res = getDriverSopStatus(uid)) {
            is AppResult.Success -> res.data
            is AppResult.Failure -> null
        }
        _sopStatus.value = status
    }

    private suspend fun resolveReviewAuditUi(workday: WorkDay): ExpatWorkdayReviewAuditUi? {
        return when (workday.approvalStatus()) {
            ApprovalStatus.APPROVED -> ExpatWorkdayReviewAuditUi.Approved(
                approverName = workday.approverName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveUserDisplayName(workday.approvedByUid ?: workday.approverUid),
                approvedAtMillis = workday.approvedAtMillis?.takeIf { it > 0L },
                approvalNote = workday.approvalNote?.trim()?.takeIf { it.isNotEmpty() }
            )
            ApprovalStatus.REJECTED -> ExpatWorkdayReviewAuditUi.Rejected(
                reason = workday.rejectReason?.trim()?.takeIf { it.isNotEmpty() },
                rejectionNote = workday.rejectionNote?.trim()?.takeIf { it.isNotEmpty() },
                rejectedAtMillis = workday.rejectedAtMillis?.takeIf { it > 0L },
                rejectedByName = resolveUserDisplayName(workday.rejectedByUid)
            )
            ApprovalStatus.PENDING -> null
        }
    }

    private suspend fun resolveScheduleSummary(workday: WorkDay) {
        val todayEpochDay = TimeUtil.todayEpochDay(jakartaZone).toInt()
        val activeYear = resolveActiveYear(todayEpochDay)
        val workdayYear = LocalDate.fromEpochDays(workday.dateEpochDay).year
        val (calendar, scheduleAvailable) = loadCalendar(workdayYear, activeYear)
        val isCompleted = workday.clockInMillis > 0L && workday.clockOutMillis > 0L
        val actualMinutes = if (isCompleted) workday.totalMinutes.coerceAtLeast(0) else 0
        val overtimeMinutes = if (isCompleted) workday.overtimeMinutes.coerceAtLeast(0) else 0
        val scheduledSpec = calendar?.let { resolveWorkSpecForDate(it, workday.dateEpochDay) }
        _scheduleSummary.value = ExpatWorkdayScheduleSummary(
            scheduleAvailable = scheduleAvailable,
            isOffDay = scheduleAvailable && calendar != null && scheduledSpec == null,
            scheduledMinutes = scheduledSpec?.scheduledNetMinutes() ?: 0,
            actualMinutes = actualMinutes,
            overtimeMinutes = overtimeMinutes
        )
    }

    private suspend fun resolveActiveYear(todayEpochDay: Int): Int {
        return when (val result = workCalendarRepository.getActiveYear(todayEpochDay, jakartaZone.id)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                val fallbackYear = LocalDate.fromEpochDays(todayEpochDay).year
                DMLog.w(
                    TAG,
                    "Failed to resolve active year. fallbackYear=$fallbackYear",
                    result.throwable
                )
                fallbackYear
            }
        }
    }

    private suspend fun loadCalendar(
        targetYear: Int,
        activeYear: Int
    ): Pair<WorkCalendarYear?, Boolean> {
        val cached = calendarCache[targetYear]
        if (cached != null) return cached to true
        return when (val result = workCalendarRepository.getYear(targetYear)) {
            is AppResult.Success -> {
                calendarCache[targetYear] = result.data
                result.data to true
            }
            is AppResult.Failure -> {
                DMLog.w(
                    TAG,
                    "Calendar load failed targetYear=$targetYear activeYear=$activeYear",
                    result.throwable
                )
                null to false
            }
        }
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

    private suspend fun resolveUserDisplayName(uid: String?): String? {
        if (uid.isNullOrBlank()) return null
        return when (val result = getUserDisplayName(uid)) {
            is AppResult.Success -> {
                result.data
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.takeIf { !it.startsWith("User-") }
            }
            is AppResult.Failure -> null
        }
    }

    private fun canCurrentUserReview(): Boolean {
        if (currentAuthUid.isBlank() || currentAuthUid == "-") return false
        return currentRole == "expat" || currentRole == "expatriate" || currentRole == "expatriat"
    }

    private fun logReview(
        event: String,
        dateEpochDay: Int?,
        success: Boolean,
        errorClass: String
    ) {
        val base =
            "[DRIVER_HISTORY] $event role=$currentRole " +
                "dateEpochDay=${dateEpochDay ?: -1} success=$success errorClass=$errorClass"
        if (success) {
            DMLog.i(TAG, base)
        } else {
            DMLog.e(TAG, base)
        }
    }

}
