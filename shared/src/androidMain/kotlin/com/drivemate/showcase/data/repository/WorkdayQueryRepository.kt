package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.WorkdaySummary
import com.drivemate.showcase.core.model.normalizeApprovalStatus
import com.drivemate.showcase.core.model.parseDeviationFlags
import com.drivemate.showcase.core.model.resolveApprovalStatus
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal class WorkdayQueryRepository(private val db: FirebaseFirestore) {

    private val workdaysCol get() = db.collection("workdays")
    private val attendanceDailyCol get() = db.collection("attendance_daily")

    private companion object {
        const val TAG = "DM/WorkdayRepo"
        const val SMALL_ATTENDANCE_DAILY_RANGE_DAYS = 7
    }

    private class WorkdayServerReadTimeoutException(
        operation: String,
        cause: Throwable
    ) : IllegalStateException(
        "Firestore server read timed out in $operation after ${FirestoreConstants.READ_TIMEOUT_MS}ms",
        cause
    )

    private suspend fun <T> withServerReadTimeout(
        operation: String,
        block: suspend () -> T
    ): T = try {
        withTimeout(FirestoreConstants.READ_TIMEOUT_MS) { block() }
    } catch (t: TimeoutCancellationException) {
        throw WorkdayServerReadTimeoutException(operation, t)
    } catch (c: CancellationException) {
        throw c
    }

    // ─── Internal range queries ───────────────────────────────────────────────

    private suspend fun byResolvedDriverAndEpochRange(
        driverUid: String,
        startEpochDayInclusive: Int,
        endEpochDayInclusive: Int,
        limit: Int = 100
    ): List<WorkdayDTO> {
        val query = workdaysCol
            .whereEqualTo("driverUid", driverUid)
            .whereGreaterThanOrEqualTo("dateEpochDay", startEpochDayInclusive)
            .whereLessThanOrEqualTo("dateEpochDay", endEpochDayInclusive)
            .orderBy("dateEpochDay", Query.Direction.DESCENDING)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        try {
            val snap = withServerReadTimeout("workdays.byDriverAndEpochRange") {
                query.get(Source.SERVER).await()
            }
            return snap.documents.map(::toDTO)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                throw IllegalStateException(
                    "Missing Firestore index for Workday: " +
                            "create composite index (driverUid ASC, dateEpochDay DESC, updatedAt DESC)."
                )
            }
            throw e
        }
    }

    /** Ambil N hari terakhir untuk driver tertentu (default 90 hari, limit 30). */
    suspend fun latestForDriver(
        driverUid: String,
        daysBack: Int = 90,
        limit: Int = 30
    ): List<WorkdayDTO> {
        // ✅ tanpa java.time; aman untuk minSdk 24 dan iOS
        val todayEpochDay = TimeUtil.todayEpochDay().toInt()
        val start = todayEpochDay - daysBack
        return byDriverAndEpochRange(
            driverUid = driverUid,
            startEpochDayInclusive = start,
            endEpochDayInclusive = todayEpochDay,
            limit = limit
        )
    }

    /** Query rentang epochDay + pengurutan stabil (sesuai index). */
    suspend fun byDriverAndEpochRange(
        driverUid: String,
        startEpochDayInclusive: Int,
        endEpochDayInclusive: Int,
        limit: Int = 100
    ): List<WorkdayDTO> {
        val uid = resolveDriverUidOrThrow(driverUid, "byDriverAndEpochRange")
        return byResolvedDriverAndEpochRange(
            driverUid = uid,
            startEpochDayInclusive = startEpochDayInclusive,
            endEpochDayInclusive = endEpochDayInclusive,
            limit = limit
        )
    }

    // ─── Attendance-daily clock lookups ───────────────────────────────────────

    private suspend fun getAttendanceDailyClocksByRange(
        driverUid: String,
        startEpochDayInclusive: Int,
        endEpochDayInclusive: Int
    ): Map<Int, AttendanceDailyClock> {
        if (startEpochDayInclusive > endEpochDayInclusive) return emptyMap()
        val dayCount = endEpochDayInclusive - startEpochDayInclusive + 1
        if (dayCount <= SMALL_ATTENDANCE_DAILY_RANGE_DAYS) {
            return (startEpochDayInclusive..endEpochDayInclusive)
                .mapNotNull { day ->
                    val clock = getAttendanceDailyClock(driverUid, day) ?: return@mapNotNull null
                    day to clock
                }
                .toMap()
        }
        return try {
            val snap = attendanceDailyCol
                .whereEqualTo("driverUid", driverUid)
                .whereGreaterThanOrEqualTo("epochDay", startEpochDayInclusive)
                .whereLessThanOrEqualTo("epochDay", endEpochDayInclusive)
                .limit(500L)
                .let { query ->
                    withServerReadTimeout("attendance_daily.clockRange") {
                        query.get(Source.SERVER).await()
                    }
                }
            snap.documents.mapNotNull { doc ->
                val day = doc.getLong("epochDay")?.toInt() ?: return@mapNotNull null
                day to AttendanceDailyClock(
                    clockInMillis = doc.getLong("clockInMillis"),
                    clockOutMillis = doc.getLong("clockOutMillis")
                )
            }.toMap()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "getAttendanceDailyClocksByRange failed: ${t.message}", t)
            throw t
        }
    }

    private suspend fun getAttendanceDailyClock(
        driverUid: String,
        epochDay: Int
    ): AttendanceDailyClock? {
        return try {
            val docId = "${driverUid}_${epochDay}"
            val snap = withServerReadTimeout("attendance_daily.clock") {
                attendanceDailyCol.document(docId).get(Source.SERVER).await()
            }
            if (!snap.exists()) return null
            AttendanceDailyClock(
                clockInMillis = snap.getLong("clockInMillis"),
                clockOutMillis = snap.getLong("clockOutMillis")
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "getAttendanceDailyClock failed: ${t.message}", t)
            throw t
        }
    }

    // ─── WorkDayRepository implementations ────────────────────────────────────

    internal suspend fun getWorkDaysByRange(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): List<WorkDay> {
        val uid = resolveDriverUidOrThrow(driverUid, "getWorkDaysByRange")
        val rows = byResolvedDriverAndEpochRange(
            driverUid = uid,
            startEpochDayInclusive = startEpochDay,
            endEpochDayInclusive = endEpochDay,
            limit = 500
        )
        if (rows.isEmpty()) return emptyList()
        val attendanceDailyByDay = getAttendanceDailyClocksByRange(
            driverUid = uid,
            startEpochDayInclusive = startEpochDay,
            endEpochDayInclusive = endEpochDay
        )
        return rows.map { dto ->
            val normalized = normalizeApprovalStatus(
                statusRaw = dto.status,
                approvedByExpat = dto.approvedByExpat,
                approvedByUid = dto.approvedByUid,
                rejectedByUid = dto.rejectedByUid,
                rejectedAtMillis = dto.rejectedAtMillis,
                rejectReason = dto.rejectReason,
                rejectionNote = dto.rejectionNote
            )
            val parsedPlanSource = PlanSource.fromRaw(dto.planSource)
            val parsedFlags = parseDeviationFlags(dto.deviationFlags)
            val attendanceDailyClock = attendanceDailyByDay[dto.dateEpochDay]
            val resolvedClockIn = resolveClockMillis(dto.clockInMillis, attendanceDailyClock?.clockInMillis)
            val resolvedClockOut = resolveClockMillis(dto.clockOutMillis, attendanceDailyClock?.clockOutMillis)
            val isClockCorrected = dto.clockCorrectionMeta != null
                || dto.expatCorrectionHistoryCount > 0
                || hasLegacyClockCorrection(
                    workdayClockIn = dto.clockInMillis,
                    workdayClockOut = dto.clockOutMillis,
                    attendanceDailyClock = attendanceDailyClock
                )
            val originalClockIn = resolveOriginalClockMillis(
                storedOriginal = dto.originalClockInMillis,
                workdayClock = dto.clockInMillis,
                attendanceDailyClock = attendanceDailyClock?.clockInMillis,
                isClockCorrected = isClockCorrected
            )
            val originalClockOut = resolveOriginalClockMillis(
                storedOriginal = dto.originalClockOutMillis,
                workdayClock = dto.clockOutMillis,
                attendanceDailyClock = attendanceDailyClock?.clockOutMillis,
                isClockCorrected = isClockCorrected
            )
            val correctedByRole = resolveCorrectedByRole(
                correctionMeta = dto.clockCorrectionMeta,
                expatCorrectionHistoryCount = dto.expatCorrectionHistoryCount,
                adjustedBy = dto.adjustedBy
            )
            val correctedAtMillis = resolveCorrectedAtMillis(
                correctionMeta = dto.clockCorrectionMeta,
                adjustedAt = dto.adjustedAt
            )
            val (resolvedTotalMinutes, resolvedOvertimeMinutes) = resolveComputedMinutes(
                totalMinutes = dto.totalMinutes,
                overtimeMinutes = dto.overtimeMinutes,
                clockInMillis = resolvedClockIn,
                clockOutMillis = resolvedClockOut
            )
            WorkDay(
                id = dto.id,
                driverUid = uid,
                dateEpochDay = dto.dateEpochDay,
                clockInMillis = resolvedClockIn,
                clockOutMillis = resolvedClockOut,
                totalMinutes = resolvedTotalMinutes,
                overtimeMinutes = resolvedOvertimeMinutes,
                driverName = dto.driverName,
                approvedByExpat = normalized.approvedByExpat,
                rejectionNote = dto.rejectionNote,
                adjustedBy = dto.adjustedBy,
                adjustedAt = dto.adjustedAt,
                adjustedReason = dto.adjustedReason,
                hasChecklist = dto.hasChecklist,
                checklistSubmittedAt = dto.checklistSubmittedAt,
                hasSafetyDeclaration = dto.hasSafetyDeclaration,
                safetySubmittedAt = dto.safetySubmittedAt,
                approverUid = dto.approverUid,
                approverName = dto.approverName,
                status = normalized.status,
                approvedByUid = dto.approvedByUid,
                approvedAtMillis = dto.approvedAtMillis,
                approvalNote = dto.approvalNote,
                rejectedByUid = dto.rejectedByUid,
                rejectedAtMillis = dto.rejectedAtMillis,
                rejectReason = dto.rejectReason,
                plannedStartMillis = dto.plannedStartMillis,
                plannedEndMillis = dto.plannedEndMillis,
                planSource = parsedPlanSource,
                deviationFlags = parsedFlags,
                reviewHold = dto.reviewHold ?: false,
                clockCorrectionMeta = dto.clockCorrectionMeta,
                expatCorrectionHistoryCount = dto.expatCorrectionHistoryCount,
                isClockCorrected = isClockCorrected,
                originalClockInMillis = originalClockIn,
                originalClockOutMillis = originalClockOut,
                correctedByRole = correctedByRole,
                correctedAtMillis = correctedAtMillis
            )
        }
    }

    internal suspend fun getWorkdaySummary(
        driverUid: String,
        startEpochDay: Int,
        endEpochDay: Int
    ): WorkdaySummary {
        val uid = resolveDriverUidOrThrow(driverUid, "getWorkdaySummary")
        val limit = (endEpochDay - startEpochDay + 1).coerceAtLeast(1).coerceAtMost(200)
        val rows = byResolvedDriverAndEpochRange(
            driverUid = uid,
            startEpochDayInclusive = startEpochDay,
            endEpochDayInclusive = endEpochDay,
            limit = limit
        )
        var approvedCount = 0
        var rejectedCount = 0
        var pendingCount = 0
        rows.forEach { row ->
            when (resolveApprovalStatus(
                statusRaw = row.status,
                approvedByExpat = row.approvedByExpat,
                approvedByUid = row.approvedByUid,
                rejectedByUid = row.rejectedByUid,
                rejectedAtMillis = row.rejectedAtMillis,
                rejectReason = row.rejectReason,
                rejectionNote = row.rejectionNote
            )) {
                true -> approvedCount += 1
                false -> rejectedCount += 1
                null -> pendingCount += 1
            }
        }
        val lastEpochDay = rows.firstOrNull()?.dateEpochDay
        return WorkdaySummary(
            driverUid = uid,
            totalDays = rows.size,
            approvedCount = approvedCount,
            rejectedCount = rejectedCount,
            pendingCount = pendingCount,
            lastEpochDay = lastEpochDay
        )
    }

    internal suspend fun getWorkDayById(workDayId: String): AppResult<WorkDay?> {
        return try {
            val doc = withServerReadTimeout("workdays.getById") {
                workdaysCol.document(workDayId).get(Source.SERVER).await()
            }
            if (!doc.exists()) return AppResult.Success(null)
            val dto = toDTO(doc)
            val normalized = normalizeApprovalStatus(
                statusRaw = dto.status,
                approvedByExpat = dto.approvedByExpat,
                approvedByUid = dto.approvedByUid,
                rejectedByUid = dto.rejectedByUid,
                rejectedAtMillis = dto.rejectedAtMillis,
                rejectReason = dto.rejectReason,
                rejectionNote = dto.rejectionNote
            )
            val attendanceDailyClock = getAttendanceDailyClock(
                driverUid = dto.driverUid,
                epochDay = dto.dateEpochDay
            )
            val resolvedClockIn = resolveClockMillis(dto.clockInMillis, attendanceDailyClock?.clockInMillis)
            val resolvedClockOut = resolveClockMillis(dto.clockOutMillis, attendanceDailyClock?.clockOutMillis)
            val isClockCorrected = dto.clockCorrectionMeta != null
                || dto.expatCorrectionHistoryCount > 0
                || hasLegacyClockCorrection(
                    workdayClockIn = dto.clockInMillis,
                    workdayClockOut = dto.clockOutMillis,
                    attendanceDailyClock = attendanceDailyClock
                )
            val originalClockIn = resolveOriginalClockMillis(
                storedOriginal = dto.originalClockInMillis,
                workdayClock = dto.clockInMillis,
                attendanceDailyClock = attendanceDailyClock?.clockInMillis,
                isClockCorrected = isClockCorrected
            )
            val originalClockOut = resolveOriginalClockMillis(
                storedOriginal = dto.originalClockOutMillis,
                workdayClock = dto.clockOutMillis,
                attendanceDailyClock = attendanceDailyClock?.clockOutMillis,
                isClockCorrected = isClockCorrected
            )
            val correctedByRole = resolveCorrectedByRole(
                correctionMeta = dto.clockCorrectionMeta,
                expatCorrectionHistoryCount = dto.expatCorrectionHistoryCount,
                adjustedBy = dto.adjustedBy
            )
            val correctedAtMillis = resolveCorrectedAtMillis(
                correctionMeta = dto.clockCorrectionMeta,
                adjustedAt = dto.adjustedAt
            )
            val (resolvedTotalMinutes, resolvedOvertimeMinutes) = resolveComputedMinutes(
                totalMinutes = dto.totalMinutes,
                overtimeMinutes = dto.overtimeMinutes,
                clockInMillis = resolvedClockIn,
                clockOutMillis = resolvedClockOut
            )
            AppResult.Success(
                WorkDay(
                    id = dto.id,
                    driverUid = dto.driverUid,
                    dateEpochDay = dto.dateEpochDay,
                    clockInMillis = resolvedClockIn,
                    clockOutMillis = resolvedClockOut,
                    totalMinutes = resolvedTotalMinutes,
                    overtimeMinutes = resolvedOvertimeMinutes,
                    driverName = dto.driverName,
                    approvedByExpat = normalized.approvedByExpat,
                    rejectionNote = dto.rejectionNote,
                    adjustedBy = dto.adjustedBy,
                    adjustedAt = dto.adjustedAt,
                    adjustedReason = dto.adjustedReason,
                    hasChecklist = dto.hasChecklist,
                    checklistSubmittedAt = dto.checklistSubmittedAt,
                    hasSafetyDeclaration = dto.hasSafetyDeclaration,
                    safetySubmittedAt = dto.safetySubmittedAt,
                    approverUid = dto.approverUid,
                    approverName = dto.approverName,
                    status = normalized.status,
                    approvedByUid = dto.approvedByUid,
                    approvedAtMillis = dto.approvedAtMillis,
                    approvalNote = dto.approvalNote,
                    rejectedByUid = dto.rejectedByUid,
                    rejectedAtMillis = dto.rejectedAtMillis,
                    rejectReason = dto.rejectReason,
                    clockCorrectionMeta = dto.clockCorrectionMeta,
                    expatCorrectionHistoryCount = dto.expatCorrectionHistoryCount,
                    isClockCorrected = isClockCorrected,
                    originalClockInMillis = originalClockIn,
                    originalClockOutMillis = originalClockOut,
                    correctedByRole = correctedByRole,
                    correctedAtMillis = correctedAtMillis
                )
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppResult.Failure(t)
        }
    }
}
