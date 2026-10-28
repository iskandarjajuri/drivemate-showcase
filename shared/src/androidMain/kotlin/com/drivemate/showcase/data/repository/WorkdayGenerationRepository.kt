package com.drivemate.showcase.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.ExplicitWorkdayPlanWindow
import com.drivemate.showcase.core.model.PlanAuthor
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkdayPlan
import com.drivemate.showcase.core.model.computeDeviationFlags
import com.drivemate.showcase.core.model.resolveApprovalStatus
import com.drivemate.showcase.core.model.shouldHoldReview
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.data.FirestoreConstants
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_CALENDAR_TIMEZONE_ID
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSession
import com.drivemate.showcase.domain.calendar.computeCalendarDrivenWorkdayComputation
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

internal class WorkdayGenerationRepository(
    private val db: FirebaseFirestore,
    private val authRepo: AuthRepository,
    private val workCalendarRepository: WorkCalendarRepository
) {
    private val workdaysCol get() = db.collection("workdays")
    private val workdayPlansCol get() = db.collection("workday_plans")

    // ─── Calendar / plan helpers ──────────────────────────────────────────────

    /** Public entry point for [FirebaseWorkdayRepository.getExplicitPlan] — reuses [getExplicitPlan] as-is. */
    internal suspend fun getExplicitPlanWindow(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<ExplicitWorkdayPlanWindow?> = try {
        val plan = getExplicitPlan(driverUid, dateEpochDay)
        AppResult.success(
            plan?.let { ExplicitWorkdayPlanWindow(it.plannedStartMillis, it.plannedEndMillis) }
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        AppResult.failure(t)
    }

    private suspend fun getExplicitPlan(
        driverUid: String,
        dateEpochDay: Int
    ): WorkdayPlan? {
        val docId = "${driverUid}_${dateEpochDay}"
        val snap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
            val snap = workdayPlansCol.document(docId).get(Source.SERVER).await()
            snap
        }
        if (!snap.exists()) return null
        val plannedStart = snap.getLong("plannedStartMillis")
        val plannedEnd = snap.getLong("plannedEndMillis")
        if (plannedStart == null || plannedEnd == null) return null
        return WorkdayPlan(
            driverUid = driverUid,
            dateEpochDay = dateEpochDay,
            plannedStartMillis = plannedStart,
            plannedEndMillis = plannedEnd,
            source = PlanAuthor.fromRaw(snap.getString("source")),
            createdAt = snap.getLong("createdAt"),
            createdByUid = snap.getString("createdByUid"),
            note = snap.getString("note")
        )
    }

    private suspend fun getCalendarForEpochDay(dateEpochDay: Int): WorkCalendarYear {
        val year = LocalDate.fromEpochDays(dateEpochDay).year
        return when (val result = workCalendarRepository.getYear(year)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                DMLog.w(
                    "DM/WorkdayRepo",
                    "getCalendarForEpochDay fallback year=$year reason=${result.throwable.message}"
                )
                WorkCalendarYear(
                    year = year,
                    timezoneId = DEFAULT_WORK_CALENDAR_TIMEZONE_ID
                )
            }
        }
    }

    // ─── WorkDayRepository implementations ────────────────────────────────────

    internal suspend fun generateWorkdayFromAttendances(
        driverUid: String,
        dateEpochDay: Int
    ): AppResult<Unit> {
        try {
            val uid = try {
                resolveDriverUidOrThrow(driverUid, "generateWorkdayFromAttendances")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                return AppResult.Failure(t)
            }
            DMLog.i("DM/WorkdayGen", "generateWorkdayFromAttendances raw=$driverUid canonical=$uid day=$dateEpochDay")
            val start = TimeUtil.parseHm("00:00", dateEpochDay.toLong())
            val end = TimeUtil.parseHm("00:00", (dateEpochDay + 1).toLong())

            val attDocs = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                db.collection("attendances")
                    .whereEqualTo("driverUid", uid)
                    .whereGreaterThanOrEqualTo("timestampMillis", start)
                    .whereLessThan("timestampMillis", end)
                    .orderBy("timestampMillis", Query.Direction.ASCENDING)
                    .get(Source.SERVER)
                    .await()
                    .documents
            }

            val firstIn = attDocs.firstOrNull { it.getString("type") == "IN" || it.getString("note") == "IN" }
            val lastOut = attDocs.lastOrNull { it.getString("type") == "OUT" || it.getString("note") == "OUT" }

            if (firstIn == null || lastOut == null) {
                DMLog.d("DM/WorkdayGen", "Skip generate: missing IN/OUT for uid=$uid day=$dateEpochDay")
                return AppResult.Success(Unit)
            }

            val clockIn = firstIn.getLong("timestampMillis") ?: return AppResult.Success(Unit)
            val clockOut = lastOut.getLong("timestampMillis") ?: return AppResult.Success(Unit)
            val calendar = getCalendarForEpochDay(dateEpochDay)
            val computed = computeCalendarDrivenWorkdayComputation(
                calendar = calendar,
                dateEpochDay = dateEpochDay,
                sessions = listOf(
                    WorkSession(
                        clockInMillis = clockIn,
                        clockOutMillis = clockOut
                    )
                )
            )
            val totalMinutes = computed.totalMinutes
            val overtimeMinutes = computed.overtimeMinutes
            val now = TimeUtil.nowEpochMillis()
            val explicitPlan = getExplicitPlan(uid, dateEpochDay)
            val plannedStart = explicitPlan?.plannedStartMillis
                ?: computed.plannedStartMillis
            val plannedEnd = explicitPlan?.plannedEndMillis
                ?: computed.plannedEndMillis
            val planSource = when {
                explicitPlan != null -> PlanSource.EXPLICIT
                computed.isWorkday -> PlanSource.DEFAULT
                // PlanSource has no dedicated HOLIDAY type; keep existing off-day bucket.
                else -> PlanSource.WEEKEND
            }
            val isCalendarOffDay = !computed.isWorkday
            val deviationFlags = computeDeviationFlags(
                isWeekend = isCalendarOffDay,
                planStartMillis = plannedStart,
                planEndMillis = plannedEnd,
                clockInMillis = clockIn,
                clockOutMillis = clockOut,
                graceMinutes = 15,
                thresholdMinutes = 30
            )
            val reviewHold = shouldHoldReview(deviationFlags)

            val existingSnap = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                workdaysCol
                    .whereEqualTo("driverUid", uid)
                    .whereEqualTo("dateEpochDay", dateEpochDay)
                    .limit(2)
                    .get(Source.SERVER)
                    .await()
            }
            val existingDoc = existingSnap.documents.firstOrNull()
            if (existingSnap.documents.size > 1) {
                DMLog.w(
                    "DM/WorkdayGen",
                    "Multiple workdays found for uid=$uid day=$dateEpochDay; updating first"
                )
            }
            val targetRef = existingDoc?.reference
                ?: workdaysCol.document("${uid}_${dateEpochDay}")

            val prevClockIn = existingDoc?.getLong("clockInMillis")
            val prevClockOut = existingDoc?.getLong("clockOutMillis")
            val prevTotalMinutes = existingDoc?.getLong("totalMinutes")?.toInt()
            val prevOvertimeMinutes = existingDoc?.getLong("overtimeMinutes")?.toInt()
            val prevStatusRaw = existingDoc?.getString("status")
            val prevApprovedByExpat = existingDoc?.getBoolean("approvedByExpat")
            val prevApprovedByUid = existingDoc?.getString("approvedByUid")
            val prevRejectedByUid = existingDoc?.getString("rejectedByUid")
            val prevRejectedAtMillis = existingDoc?.getLong("rejectedAtMillis")
            val prevRejectReason = existingDoc?.getString("rejectReason")
            val prevRejectionNote = existingDoc?.getString("rejectionNote")
            val prevDecision = resolveApprovalStatus(
                statusRaw = prevStatusRaw,
                approvedByExpat = prevApprovedByExpat,
                approvedByUid = prevApprovedByUid,
                rejectedByUid = prevRejectedByUid,
                rejectedAtMillis = prevRejectedAtMillis,
                rejectReason = prevRejectReason,
                rejectionNote = prevRejectionNote
            )
            val prevStatus = when (prevDecision) {
                true -> STATUS_APPROVED
                false -> STATUS_REJECTED
                null -> STATUS_PENDING
            }
            val computedChanged = existingDoc == null
                || prevClockIn == null
                || prevClockOut == null
                || prevTotalMinutes == null
                || prevOvertimeMinutes == null
                || prevClockIn != clockIn
                || prevClockOut != clockOut
                || prevTotalMinutes != totalMinutes
                || prevOvertimeMinutes != overtimeMinutes
            val shouldResetApproval = computedChanged
            val shouldSetPending = existingDoc == null || shouldResetApproval ||
                (prevStatusRaw.isNullOrBlank() && prevDecision == null)
            val newStatus = if (shouldSetPending) STATUS_PENDING else prevStatus
            DMLog.i(
                "DM/WorkdayGen",
                "[WORKDAY_REGEN] docId=${targetRef.id} computedChanged=$computedChanged " +
                    "prevStatus=$prevStatus newStatus=$newStatus"
            )

            val payload = mutableMapOf<String, Any?>(
                "driverUid" to uid,
                "dateEpochDay" to dateEpochDay,
                "clockInMillis" to clockIn,
                "clockOutMillis" to clockOut,
                "totalMinutes" to totalMinutes,
                "overtimeMinutes" to overtimeMinutes,
                "plannedStartMillis" to (plannedStart ?: FieldValue.delete()),
                "plannedEndMillis" to (plannedEnd ?: FieldValue.delete()),
                "planSource" to planSource.toRaw(),
                "deviationFlags" to deviationFlags.map { it.toRaw() },
                "reviewHold" to reviewHold,
                "updatedAt" to now
            )

            if (existingDoc != null && computedChanged) {
                payload["clockCorrectionMeta"] = mapOf(
                    "correctedByRole" to "SYSTEM",
                    "correctedAt" to FieldValue.serverTimestamp()
                )
            }

            if (existingDoc == null) {
                payload["createdAt"] = now
                payload["status"] = STATUS_PENDING
            } else if (shouldResetApproval) {
                payload["status"] = STATUS_PENDING
                val clears = listOf(
                    "approvedByExpat",
                    "approverUid",
                    "approverName",
                    "approvedByUid",
                    "approvedAtMillis",
                    "approvalNote",
                    "rejectedByUid",
                    "rejectedAtMillis",
                    "rejectReason",
                    "rejectionNote"
                )
                clears.forEach { key -> payload[key] = FieldValue.delete() }
            } else if (prevStatusRaw.isNullOrBlank() && prevDecision == null) {
                payload["status"] = STATUS_PENDING
            }

            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                targetRef
                    .set(payload, SetOptions.merge())
                    .await()
            }
            DMLog.i(
                "DM/WorkdayGen",
                "Upsert workday uid=$uid day=$dateEpochDay docId=${targetRef.id}"
            )

            return AppResult.Success(Unit)
        } catch (c: CancellationException) {
            DMLog.d("DM/WorkdayGen", "Generate cancelled (lifecycle)")
            throw c
        } catch (t: Throwable) {
            DMLog.e("DM/WorkdayGen", "Generate failed: ${t.message}", t)
            return AppResult.Failure(t)
        }
    }

    internal suspend fun upsertWorkdayPlan(
        driverUid: String,
        epochDay: Int,
        plannedStartMillis: Long,
        plannedEndMillis: Long,
        source: String
    ): AppResult<Unit> {
        try {
            val uid = try {
                resolveDriverUidOrThrow(driverUid, "upsertWorkdayPlan")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                return AppResult.Failure(t)
            }
            if (plannedStartMillis >= plannedEndMillis) {
                return AppResult.Failure(IllegalArgumentException("Invalid plan range"))
            }
            val normalizedSource = source.trim().lowercase()
            if (normalizedSource != "admin" && normalizedSource != "expat") {
                return AppResult.Failure(IllegalArgumentException("Invalid plan source"))
            }
            val authUid = when (val res = authRepo.currentUser()) {
                is AppResult.Success -> res.data?.uid
                is AppResult.Failure -> null
            }
            if (authUid.isNullOrBlank()) {
                return AppResult.Failure(IllegalStateException("Missing auth"))
            }
            val now = TimeUtil.nowEpochMillis()
            val docRef = workdayPlansCol.document("${uid}_${epochDay}")
            val payload = mutableMapOf<String, Any>(
                "driverUid" to uid,
                "dateEpochDay" to epochDay,
                "plannedStartMillis" to plannedStartMillis,
                "plannedEndMillis" to plannedEndMillis,
                "source" to normalizedSource
            )
            val existing = withTimeout(FirestoreConstants.READ_TIMEOUT_MS) {
                docRef.get(Source.SERVER).await()
            }
            if (!existing.exists()) {
                payload["createdAt"] = now
                payload["createdByUid"] = authUid
            }
            withTimeout(FirestoreConstants.WRITE_TIMEOUT_MS) {
                docRef
                    .set(payload, SetOptions.merge())
                    .await()
            }
            return AppResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return AppResult.Failure(t)
        }
    }
}
