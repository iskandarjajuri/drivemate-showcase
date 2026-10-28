package com.drivemate.showcase.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.ClockCorrectionMeta
import com.drivemate.showcase.core.model.ReasonCode
import com.drivemate.showcase.core.model.resolveAttendanceDailyState
import com.drivemate.showcase.core.model.toFirestore
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.normalizePersistedTotalMinutes
import com.drivemate.showcase.domain.repository.AuthRepository

// ─── Status constants ────────────────────────────────────────────────────────

internal const val STATUS_PENDING  = "pending"
internal const val STATUS_APPROVED = "approved"
internal const val STATUS_REJECTED = "rejected"

internal val WORKDAY_ALLOWED_KEYS = setOf(
    "driverUid",
    "driverName",
    "dateEpochDay",
    "clockInMillis",
    "clockOutMillis",
    "totalMinutes",
    "overtimeMinutes",
    "hasChecklist",
    "checklistSubmittedAt",
    "hasSafetyDeclaration",
    "safetySubmittedAt",
    "approvedByExpat",
    "approverUid",
    "approverName",
    "status",
    "approvedByUid",
    "approvedAtMillis",
    "approvalNote",
    "rejectedByUid",
    "rejectedAtMillis",
    "rejectReason",
    "rejectionNote",
    "adjustedAt",
    "adjustedBy",
    "adjustedReason",
    "overrideByUid",
    "overrideAtMillis",
    "overrideReason",
    "overrideFromStatus",
    "plannedStartMillis",
    "plannedEndMillis",
    "planSource",
    "deviationFlags",
    "reviewHold",
    "originalClockInMillis",
    "originalClockOutMillis",
    "clockCorrectionMeta",
    "expatCorrectionHistory",
    "expatDecision",
    "createdAt",
    "updatedAt"
)

// ─── Internal data types ─────────────────────────────────────────────────────

internal data class WorkdayDTO(
    val id: String,
    val driverUid: String,
    val dateEpochDay: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val clockInMillis: Long?,
    val clockOutMillis: Long?,
    val totalMinutes: Int,
    val overtimeMinutes: Int,
    val driverName: String?,
    val approvedByExpat: Boolean?,
    val rejectionNote: String?,
    val adjustedAt: Long?,
    val adjustedBy: String?,
    val adjustedReason: String?,
    val hasChecklist: Boolean?,
    val checklistSubmittedAt: Long?,
    val hasSafetyDeclaration: Boolean?,
    val safetySubmittedAt: Long?,
    val approverUid: String?,
    val approverName: String?,
    val status: String?,
    val approvedByUid: String?,
    val approvedAtMillis: Long?,
    val approvalNote: String?,
    val rejectedByUid: String?,
    val rejectedAtMillis: Long?,
    val rejectReason: String?,
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?,
    val planSource: String?,
    val deviationFlags: List<String>?,
    val reviewHold: Boolean?,
    val originalClockInMillis: Long?,
    val originalClockOutMillis: Long?,
    val clockCorrectionMeta: ClockCorrectionMeta?,
    val expatCorrectionHistoryCount: Int
)

internal data class AttendanceDailyClock(
    val clockInMillis: Long?,
    val clockOutMillis: Long?
)

// ─── Auth / UID guard helpers ─────────────────────────────────────────────────

private const val TAG = "DM/WorkdayRepo"

internal fun isProbablyUid(key: String): Boolean =
    key.length >= 20 && !key.contains("@")

internal fun resolveDriverUidOrThrow(raw: String, origin: String): String {
    val uid = raw.trim()
    if (!isProbablyUid(uid)) {
        DMLog.w(TAG, "Reject non-UID driver key in $origin: '$raw'")
        throw IllegalStateException("Driver UID required in $origin")
    }
    return uid
}

internal suspend fun currentActorUidOrThrow(authRepo: AuthRepository, origin: String): String {
    val uid = when (val res = authRepo.currentUser()) {
        is AppResult.Success -> res.data?.uid
        is AppResult.Failure -> null
    }
    return uid?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Missing auth in $origin")
}

internal suspend fun currentRoleOrThrow(authRepo: AuthRepository, origin: String): String {
    val role = when (val res = authRepo.currentUser()) {
        is AppResult.Success -> res.data?.role?.toFirestore()
        is AppResult.Failure -> null
    }
    return role?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Missing auth role in $origin")
}

// ─── Firestore document → DTO ─────────────────────────────────────────────────

internal fun parseClockCorrectionMeta(raw: Any?): ClockCorrectionMeta? {
    val map = raw as? Map<*, *> ?: return null
    val role = (map["correctedByRole"] as? String)
        ?.trim()
        ?.uppercase()
        ?.takeIf { it == "ADMIN" || it == "EXPAT" || it == "SYSTEM" }
        ?: return null
    val correctedAtMillis = when (val value = map["correctedAt"]) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }?.takeIf { it > 0L } ?: return null
    val reason = (map["reason"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    return ClockCorrectionMeta(
        correctedByRole = role,
        correctedAtMillis = correctedAtMillis,
        reason = reason
    )
}

internal fun parseExpatCorrectionHistoryCount(raw: Any?): Int {
    val entries = raw as? List<*> ?: return 0
    return entries.count { entry ->
        when (entry) {
            is Timestamp -> true
            is Number -> entry.toLong() > 0L
            else -> false
        }
    }
}

internal fun toDTO(d: DocumentSnapshot): WorkdayDTO {
    val totalMinutesRaw = d.getLong("totalMinutes")
    val overtimeMinutesRaw = d.getLong("overtimeMinutes")
    return WorkdayDTO(
        id = d.id,
        driverUid = d.getString("driverUid") ?: "",
        dateEpochDay = (d.getLong("dateEpochDay") ?: 0L).toInt(),
        createdAt = d.getLong("createdAt") ?: 0L,
        updatedAt = d.getLong("updatedAt") ?: 0L,
        clockInMillis = d.getLong("clockInMillis"),
        clockOutMillis = d.getLong("clockOutMillis"),
        totalMinutes = normalizePersistedTotalMinutes(totalMinutesRaw, overtimeMinutesRaw),
        overtimeMinutes = (overtimeMinutesRaw ?: 0L).toInt(),
        driverName = d.getString("driverName"),
        approvedByExpat = d.getBoolean("approvedByExpat"),
        rejectionNote = d.getString("rejectionNote"),
        adjustedAt = d.getLong("adjustedAt"),
        adjustedBy = d.getString("adjustedBy"),
        adjustedReason = d.getString("adjustedReason"),
        hasChecklist = d.getBoolean("hasChecklist"),
        checklistSubmittedAt = d.getLong("checklistSubmittedAt"),
        hasSafetyDeclaration = d.getBoolean("hasSafetyDeclaration"),
        safetySubmittedAt = d.getLong("safetySubmittedAt"),
        approverUid = d.getString("approverUid"),
        approverName = d.getString("approverName"),
        status = d.getString("status"),
        approvedByUid = d.getString("approvedByUid"),
        approvedAtMillis = d.getLong("approvedAtMillis"),
        approvalNote = d.getString("approvalNote"),
        rejectedByUid = d.getString("rejectedByUid"),
        rejectedAtMillis = d.getLong("rejectedAtMillis"),
        rejectReason = d.getString("rejectReason"),
        plannedStartMillis = d.getLong("plannedStartMillis"),
        plannedEndMillis = d.getLong("plannedEndMillis"),
        planSource = d.getString("planSource"),
        deviationFlags = (d.get("deviationFlags") as? List<*>)?.mapNotNull { it as? String },
        reviewHold = d.getBoolean("reviewHold"),
        originalClockInMillis = d.getLong("originalClockInMillis"),
        originalClockOutMillis = d.getLong("originalClockOutMillis"),
        clockCorrectionMeta = parseClockCorrectionMeta(d.get("clockCorrectionMeta")),
        expatCorrectionHistoryCount = parseExpatCorrectionHistoryCount(d.get("expatCorrectionHistory"))
    )
}

// ─── Clock resolution helpers ─────────────────────────────────────────────────

internal fun resolveClockMillis(primary: Long?, fallback: Long?): Long {
    val p = primary?.takeIf { it > 0L }
    if (p != null) return p
    return fallback?.takeIf { it > 0L } ?: 0L
}

internal fun hasLegacyClockCorrection(
    workdayClockIn: Long?,
    workdayClockOut: Long?,
    attendanceDailyClock: AttendanceDailyClock?
): Boolean {
    val wdIn = workdayClockIn?.takeIf { it > 0L }
    val wdOut = workdayClockOut?.takeIf { it > 0L }
    val adIn = attendanceDailyClock?.clockInMillis?.takeIf { it > 0L }
    val adOut = attendanceDailyClock?.clockOutMillis?.takeIf { it > 0L }
    val inMismatch = wdIn != null && adIn != null && wdIn != adIn
    val outMismatch = wdOut != null && adOut != null && wdOut != adOut
    return inMismatch || outMismatch
}

internal fun resolveOriginalClockMillis(
    storedOriginal: Long?,
    workdayClock: Long?,
    attendanceDailyClock: Long?,
    isClockCorrected: Boolean
): Long? {
    val persisted = storedOriginal?.takeIf { it > 0L }
    if (persisted != null) return persisted
    if (!isClockCorrected) return null
    val workday = workdayClock?.takeIf { it > 0L }
    val attendance = attendanceDailyClock?.takeIf { it > 0L }
    return if (workday != null && attendance != null && workday != attendance) {
        attendance
    } else {
        null
    }
}

internal fun resolveCorrectedByRole(
    correctionMeta: ClockCorrectionMeta?,
    expatCorrectionHistoryCount: Int,
    adjustedBy: String?
): String? {
    correctionMeta?.correctedByRole?.let { return it }
    if (expatCorrectionHistoryCount > 0) return "EXPAT"
    if (!adjustedBy.isNullOrBlank()) return "ADMIN"
    return null
}

internal fun resolveCorrectedAtMillis(
    correctionMeta: ClockCorrectionMeta?,
    adjustedAt: Long?
): Long? = correctionMeta?.correctedAtMillis ?: adjustedAt?.takeIf { it > 0L }

internal fun resolveComputedMinutes(
    totalMinutes: Int,
    overtimeMinutes: Int,
    clockInMillis: Long,
    clockOutMillis: Long
): Pair<Int, Int> {
    val state = resolveAttendanceDailyState(
        clockInMillis = clockInMillis.takeIf { it > 0L },
        clockOutMillis = clockOutMillis.takeIf { it > 0L }
    )
    return if (state == AttendanceDailyState.OPEN) {
        0 to 0
    } else {
        totalMinutes.coerceAtLeast(0) to overtimeMinutes.coerceAtLeast(0)
    }
}

// ─── Approval precheck ────────────────────────────────────────────────────────

internal data class WorkdayPrecheck(
    val ok: Boolean,
    val missing: List<String>,
    val wrongType: List<String>,
    val extraKeys: List<String>,
    val driverUidFromId: String?,
    val epochFromId: Int?,
    val backfill: Map<String, Any>,
    val driverUidValue: String?,
    val dateEpochDayValue: Int?,
    val willBackfill: Boolean,
    val reasonCode: ReasonCode,
    val reasonMessage: String?
) {
    fun invalidReason(docId: String): String =
        "$docId missing=$missing wrongType=$wrongType extra=$extraKeys"
}

internal fun precheckWorkday(docId: String, data: Map<String, Any>): WorkdayPrecheck {
    val (driverUidFromId, epochFromId) = parseWorkdayDocId(docId)
    val missing = mutableListOf<String>()
    val wrongType = mutableListOf<String>()
    val extraKeys = data.keys.filter { it !in WORKDAY_ALLOWED_KEYS }.sorted()

    val driverUidVal = data["driverUid"]
    val driverUidOk = driverUidVal is String && driverUidVal.isNotBlank()
    if (!driverUidOk) {
        if (driverUidVal == null) missing.add("driverUid") else wrongType.add("driverUid")
    }

    val dateVal = data["dateEpochDay"]
    val dateEpochDayValue = when (dateVal) {
        is Long -> dateVal.toInt()
        is Int -> dateVal
        else -> null
    }
    val hasDateField = dateEpochDayValue != null
    if (!hasDateField) {
        if (dateVal == null) missing.add("dateEpochDay") else wrongType.add("dateEpochDay")
    }

    val totalVal = data["totalMinutes"]
    val totalOk = totalVal is Long || totalVal is Int
    if (!totalOk) {
        if (totalVal == null) missing.add("totalMinutes") else wrongType.add("totalMinutes")
    }

    val backfill = mutableMapOf<String, Any>()
    val hasEpochFromId = epochFromId != null
    // Backfill dateEpochDay from docId only when field is missing and docId is parseable.
    if (!hasDateField && hasEpochFromId) {
        backfill["dateEpochDay"] = epochFromId
    }
    if (!totalOk) {
        backfill["totalMinutes"] = 0
    }
    val willBackfill = backfill.isNotEmpty()
    val ok = driverUidOk && extraKeys.isEmpty() && (hasDateField || hasEpochFromId)
    val invalidDocId = !hasDateField && !hasEpochFromId
    val reasonCode = when {
        ok -> ReasonCode.NONE
        !driverUidOk -> ReasonCode.INVALID_SCHEMA
        extraKeys.isNotEmpty() -> ReasonCode.INVALID_SCHEMA
        invalidDocId -> ReasonCode.INVALID_DOC_ID_FORMAT
        else -> ReasonCode.INVALID_SCHEMA
    }
    val reasonMessage = if (ok) null else "missing=$missing wrongType=$wrongType extra=$extraKeys"
    return WorkdayPrecheck(
        ok = ok,
        missing = missing,
        wrongType = wrongType,
        extraKeys = extraKeys,
        driverUidFromId = driverUidFromId,
        epochFromId = epochFromId,
        backfill = backfill,
        driverUidValue = driverUidVal as? String,
        dateEpochDayValue = dateEpochDayValue ?: epochFromId,
        willBackfill = willBackfill,
        reasonCode = reasonCode,
        reasonMessage = reasonMessage
    )
}

internal fun decisionStatusFromSnapshot(snapshot: DocumentSnapshot): String {
    val status = snapshot.getString("status")?.trim()?.lowercase()
    if (status == STATUS_APPROVED || status == STATUS_REJECTED) return status
    val hasRejectedUid = !snapshot.getString("rejectedByUid").isNullOrBlank()
    if (hasRejectedUid) return STATUS_REJECTED
    val hasApprovedUid = !snapshot.getString("approvedByUid").isNullOrBlank()
    if (hasApprovedUid) return STATUS_APPROVED
    return STATUS_PENDING
}

internal fun isFinalizedByStatusOrActor(snapshot: DocumentSnapshot): Boolean {
    val status = snapshot.getString("status")?.trim()?.lowercase()
    if (status == STATUS_APPROVED || status == STATUS_REJECTED) return true
    if (!snapshot.getString("approvedByUid").isNullOrBlank()) return true
    if (!snapshot.getString("rejectedByUid").isNullOrBlank()) return true
    return false
}

internal fun parseWorkdayDocId(docId: String): Pair<String?, Int?> {
    val idx = docId.lastIndexOf('_')
    if (idx <= 0 || idx >= docId.length - 1) return Pair(null, null)
    val driverUid = docId.substring(0, idx).ifBlank { null }
    val epoch = docId.substring(idx + 1).toIntOrNull()
    return Pair(driverUid, epoch)
}
