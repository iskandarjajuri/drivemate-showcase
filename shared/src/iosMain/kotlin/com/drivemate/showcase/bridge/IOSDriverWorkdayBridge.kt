package com.drivemate.showcase.bridge

import com.drivemate.showcase.core.model.DriverTodaySchedule
import com.drivemate.showcase.core.model.UserSession
import com.drivemate.showcase.core.model.resolveAttendanceReferenceWindow
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.usecase.report.AdminOverrideWorkdayDecisionUseCase
import com.drivemate.showcase.domain.usecase.report.ApproveWorkDayUseCase
import com.drivemate.showcase.domain.usecase.report.BulkApproveWorkDaysUseCase
import com.drivemate.showcase.domain.usecase.report.BulkRejectWorkDaysUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkDaysByRangeUseCase
import com.drivemate.showcase.domain.usecase.report.GetWorkdayDetailUseCase
import com.drivemate.showcase.domain.usecase.report.RejectWorkDayUseCase
import com.drivemate.showcase.domain.usecase.workday.GetDriverTodayScheduleUseCase
import com.drivemate.showcase.domain.usecase.workday.UpsertWorkdayPlanUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IOSDriverWorkdayBridge(
    private val scope: CoroutineScope,
    private val getWorkDaysByRange: GetWorkDaysByRangeUseCase,
    private val getWorkdayDetail: GetWorkdayDetailUseCase,
    private val approveWorkDay: ApproveWorkDayUseCase,
    private val rejectWorkDay: RejectWorkDayUseCase,
    private val upsertWorkdayPlan: UpsertWorkdayPlanUseCase,
    private val adminOverrideWorkdayDecision: AdminOverrideWorkdayDecisionUseCase,
    private val bulkApproveWorkDays: BulkApproveWorkDaysUseCase,
    private val bulkRejectWorkDays: BulkRejectWorkDaysUseCase,
    private val getDriverTodaySchedule: GetDriverTodayScheduleUseCase,
    private val currentSession: () -> UserSession?
) {
    companion object {
        /** Matches Android's AdminWorkdayDetailViewModel.MAX_OVERRIDE_REASON_LENGTH. */
        const val MAX_OVERRIDE_REASON_LENGTH = 500
    }

    fun fetchWorkdayDetail(
        workdayId: String,
        completion: (IOSDriverWorkdaySnapshot?, String?) -> Unit
    ) {
        scope.launch {
            when (val result = getWorkdayDetail(workdayId)) {
                is AppResult.Success -> completion(result.data?.toIosSnapshot(), null)
                is AppResult.Failure -> completion(null, result.throwable.message ?: "Failed to load workday.")
            }
        }
    }

    fun loadDriverWorkdays(
        targetDriverUid: String?,
        completion: (List<IOSDriverWorkdaySnapshot>, String?) -> Unit
    ) {
        val session = currentSession()
        val roleStr = session?.role?.toFirestore()
        if (session == null || (roleStr != "driver" && roleStr != "expat" && roleStr != "admin")) {
            completion(emptyList(), null)
            return
        }

        // Driver views own history; expat/admin view a specific selected driver.
        val effectiveUid = if (roleStr == "driver") {
            session.uid
        } else {
            val target = targetDriverUid?.trim().orEmpty()
            if (target.isEmpty()) {
                completion(emptyList(), "loadDriverWorkdays rejected: blank driverUid for role=$roleStr")
                return
            }
            target
        }

        val range = TimeUtil.rollingThreeMonthWindowEpochDays(zone = IOSDriverTimezone)
        scope.launch {
            when (val result = getWorkDaysByRange(effectiveUid, range.first, range.last)) {
                is AppResult.Success -> completion(result.data.map { it.toIosSnapshot() }, null)
                is AppResult.Failure -> completion(emptyList(), result.throwable.message ?: "Failed to load workdays.")
            }
        }
    }

    /**
     * R4 — today's planned start/end. Mirrors Android's `DriverViewModel.refreshHomeState`:
     * `GetDriverTodayScheduleUseCase` first checks the day's own `workdays` doc, then falls
     * back to `workday_plans` directly so a plan an expat set before tap-in still shows.
     */
    fun loadTodaySchedule(completion: (IOSScheduleSnapshot?) -> Unit) {
        val session = currentSession()
        if (session == null || session.role?.toFirestore() != "driver") {
            completion(null)
            return
        }
        val epochDay = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt()
        scope.launch {
            when (val result = getDriverTodaySchedule(session.uid, epochDay)) {
                is AppResult.Success -> completion(result.data.toIosSnapshot())
                is AppResult.Failure -> completion(null)
            }
        }
    }

    /**
     * R-Attendance — the 3-zone timeline's reference window (early-overtime /
     * normal / late-overtime boundaries). Mirrors Android's exact resolution
     * order (`DriverAttendanceScreen.kt`): `GetDriverTodayScheduleUseCase` first
     * checks today's `workdays` doc, then falls back to `workday_plans`, then
     * to the standard 07:30/16:30 work hours — `resolveAttendanceReferenceWindow`
     * is that same fallback chain, called here instead of reimplemented in
     * Swift so the two platforms can never drift on it.
     *
     * A failed schedule fetch still resolves (to the standard-hours fallback,
     * via a schedule of all-nulls) rather than completing with null — the
     * timeline is a best-effort preview on Android too, never a hard error.
     */
    fun loadAttendanceReferenceWindow(completion: (IOSAttendanceReferenceWindowSnapshot) -> Unit) {
        val session = currentSession()
        val epochDay = TimeUtil.todayEpochDay(IOSDriverTimezone).toInt()
        if (session == null || session.role?.toFirestore() != "driver") {
            completion(resolveAttendanceReferenceWindow(DriverTodaySchedule(null, null, null), epochDay, IOSDriverTimezone).toIosSnapshot())
            return
        }
        scope.launch {
            val schedule = when (val result = getDriverTodaySchedule(session.uid, epochDay)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> DriverTodaySchedule(null, null, null)
            }
            completion(resolveAttendanceReferenceWindow(schedule, epochDay, IOSDriverTimezone).toIosSnapshot())
        }
    }

    fun approveWorkday(
        workdayId: String,
        note: String?,
        completion: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            when (val result = approveWorkDay(workdayId, note)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    /**
     * Admin overrides an already-final approve/reject decision.
     *
     * Distinct from [approveWorkday]/[rejectWorkday]: those record a first
     * decision, this one *replaces* a finalized decision and therefore demands an
     * audit reason. Mirrors Android's `AdminWorkdayDetailViewModel.submitOverride`
     * (`:111-150`), including the 500-character cap on the override reason.
     *
     * The repository re-validates actor role, target status, and both reasons —
     * this layer's checks exist so an invalid call never reaches Firestore, not as
     * the only line of defence.
     */
    fun adminOverrideWorkdayDecision(
        workdayId: String,
        targetStatus: String,
        rejectReason: String?,
        overrideReason: String,
        completion: (Boolean, String?) -> Unit
    ) {
        val normalizedTarget = targetStatus.trim().lowercase()
        if (normalizedTarget != "approved" && normalizedTarget != "rejected") {
            completion(false, "Invalid target status")
            return
        }
        val auditReason = overrideReason.trim()
        if (auditReason.isBlank() || auditReason.length > MAX_OVERRIDE_REASON_LENGTH) {
            completion(false, "Override reason is required")
            return
        }
        val normalizedRejectReason = rejectReason?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedTarget == "rejected" && normalizedRejectReason == null) {
            completion(false, "Reject reason is required")
            return
        }
        scope.launch {
            val result = adminOverrideWorkdayDecision(
                workDayId = workdayId,
                targetStatus = normalizedTarget,
                rejectReason = normalizedRejectReason,
                overrideReason = auditReason
            )
            when (result) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    fun rejectWorkday(
        workdayId: String,
        reason: String,
        completion: (Boolean, String?) -> Unit
    ) {
        if (reason.isBlank()) {
            completion(false, "Reject reason is required.")
            return
        }
        scope.launch {
            when (val result = rejectWorkDay(workdayId, reason)) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }

    /**
     * Simpan rencana kerja (jam mulai/selesai terencana) untuk satu hari.
     *
     * `source` di-hardcode `"expat"` mengikuti Android — satu-satunya pemanggil di
     * kedua platform adalah layar Expat (`ExpatDriverDetailViewModel` /
     * `ExpatMonthlyReviewViewModel`). Repository memvalidasi nilai ini dan hanya
     * menerima `"admin"` atau `"expat"`.
     *
     * Cek rentang start < end dilakukan di sini juga, bukan hanya di UI, supaya
     * bridge tidak bergantung pada UI yang benar — sama seperti `rejectWorkday`
     * yang tetap menolak alasan kosong walau UI sudah memvalidasinya.
     */
    /**
     * Bulk approve the given workdays.
     *
     * Returns counts rather than a plain success flag because the underlying
     * repository walks each id independently — a run can partly succeed, and the
     * expat needs to see that rather than a single "done".
     */
    fun bulkApproveWorkdays(
        workdayIds: List<String>,
        completion: (IOSBulkActionResultSnapshot?, String?) -> Unit
    ) {
        if (workdayIds.isEmpty()) {
            completion(null, "No workdays selected.")
            return
        }
        scope.launch {
            val result = bulkApproveWorkDays(workdayIds)
            completion(result.toIosSnapshot(), null)
        }
    }

    /**
     * Bulk reject the given workdays. The note is required and is shown to the
     * driver, matching single reject.
     */
    fun bulkRejectWorkdays(
        workdayIds: List<String>,
        note: String,
        completion: (IOSBulkActionResultSnapshot?, String?) -> Unit
    ) {
        if (workdayIds.isEmpty()) {
            completion(null, "No workdays selected.")
            return
        }
        val trimmedNote = note.trim()
        if (trimmedNote.isEmpty()) {
            completion(null, "Reject reason is required.")
            return
        }
        scope.launch {
            val result = bulkRejectWorkDays(workdayIds, trimmedNote)
            completion(result.toIosSnapshot(), null)
        }
    }

    fun saveWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        completion: (Boolean, String?) -> Unit
    ) {
        if (plannedStartMillis >= plannedEndMillis) {
            completion(false, "Invalid plan range")
            return
        }
        scope.launch {
            val result = upsertWorkdayPlan(
                driverUid = driverUid,
                epochDay = epochDay,
                plannedStartMillis = plannedStartMillis,
                plannedEndMillis = plannedEndMillis,
                source = "expat"
            )
            when (result) {
                is AppResult.Success -> completion(true, null)
                is AppResult.Failure -> completion(false, result.throwable.message)
            }
        }
    }
}
