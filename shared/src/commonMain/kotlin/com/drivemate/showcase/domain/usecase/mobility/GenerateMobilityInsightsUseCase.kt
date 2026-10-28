package com.drivemate.showcase.domain.usecase.mobility

import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.Checklist
import com.drivemate.showcase.core.model.DeviationFlag
import com.drivemate.showcase.core.model.Location
import com.drivemate.showcase.core.model.MobilityInsight
import com.drivemate.showcase.core.model.MobilityInsightReason
import com.drivemate.showcase.core.model.MobilityInsightReasonCode
import com.drivemate.showcase.core.model.MobilityInsightSeverity
import com.drivemate.showcase.core.model.MobilityInsightType
import com.drivemate.showcase.core.model.SafetyDeclaration
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.attendanceState

private const val LONG_DUTY_ELEVATED_MINUTES = 10 * 60
private const val LONG_DUTY_HIGH_MINUTES = 12 * 60
private const val LOCATION_STALE_MINUTES = 90
private const val MINUTE_MILLIS = 60_000L

/**
 * v0 thresholds intentionally stay conservative and deterministic:
 * - long duty elevated at >= 10h
 * - long duty high at >= 12h
 * - location stale at >= 90 minutes while attendance is still open
 */
class GenerateMobilityInsightsUseCase {

    operator fun invoke(input: MobilityInsightInput): List<MobilityInsight> {
        val insights = mutableListOf<MobilityInsight>()

        buildAttendanceIncompleteInsight(input)?.let(insights::add)
        buildSafetyDeclarationMissingInsight(input)?.let(insights::add)
        buildChecklistIssueInsight(input)?.let(insights::add)
        buildLongDutyInsight(input)?.let(insights::add)
        buildLocationStaleInsight(input)?.let(insights::add)
        buildAdminReviewInsight(input)?.let(insights::add)

        return insights.sortedWith(
            compareByDescending<MobilityInsight> { it.severity.priority }
                .thenBy { it.type.name }
        )
    }

    private fun buildAttendanceIncompleteInsight(input: MobilityInsightInput): MobilityInsight? {
        val attendanceState = input.resolveAttendanceState()
        if (attendanceState != AttendanceDailyState.OPEN) return null

        return input.toInsight(
            type = MobilityInsightType.ATTENDANCE_INCOMPLETE,
            severity = MobilityInsightSeverity.HIGH,
            reasons = listOf(
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.ATTENDANCE_STATE,
                    value = attendanceState.toRaw()
                )
            )
        )
    }

    private fun buildSafetyDeclarationMissingInsight(input: MobilityInsightInput): MobilityInsight? {
        if (!input.hasStartedDuty()) return null
        if (input.resolveSafetyDeclarationPresence() != false) return null

        return input.toInsight(
            type = MobilityInsightType.SAFETY_DECLARATION_MISSING,
            severity = MobilityInsightSeverity.HIGH,
            reasons = listOf(
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.SAFETY_DECLARATION_STATUS,
                    value = "missing"
                )
            )
        )
    }

    private fun buildChecklistIssueInsight(input: MobilityInsightInput): MobilityInsight? {
        val checklist = input.checklist ?: return null
        if (!checklist.hasIssue) return null

        return input.toInsight(
            type = MobilityInsightType.CHECKLIST_ISSUE_DETECTED,
            severity = MobilityInsightSeverity.ELEVATED,
            reasons = listOf(
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.CHECKLIST_STATUS,
                    value = "issue_detected"
                )
            )
        )
    }

    private fun buildLongDutyInsight(input: MobilityInsightInput): MobilityInsight? {
        val dutyMinutes = input.resolveDutyMinutes() ?: return null
        val (severity, thresholdMinutes) = when {
            dutyMinutes >= LONG_DUTY_HIGH_MINUTES ->
                MobilityInsightSeverity.HIGH to LONG_DUTY_HIGH_MINUTES
            dutyMinutes >= LONG_DUTY_ELEVATED_MINUTES ->
                MobilityInsightSeverity.ELEVATED to LONG_DUTY_ELEVATED_MINUTES
            else -> return null
        }

        return input.toInsight(
            type = MobilityInsightType.LONG_DUTY_DURATION,
            severity = severity,
            reasons = listOf(
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.DUTY_MINUTES,
                    value = dutyMinutes.toString()
                ),
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.DUTY_THRESHOLD_MINUTES,
                    value = thresholdMinutes.toString()
                )
            )
        )
    }

    private fun buildLocationStaleInsight(input: MobilityInsightInput): MobilityInsight? {
        val attendanceState = input.resolveAttendanceState()
        if (attendanceState != AttendanceDailyState.OPEN) return null

        val nowMillis = input.nowMillis ?: return null
        val latestLocation = input.latestLocation ?: return null
        val staleMillis = nowMillis - latestLocation.updatedAtMillis
        if (staleMillis < LOCATION_STALE_MINUTES * MINUTE_MILLIS) return null

        val staleMinutes = (staleMillis / MINUTE_MILLIS).toInt()
        return input.toInsight(
            type = MobilityInsightType.LOCATION_STALE,
            severity = MobilityInsightSeverity.LOW,
            reasons = listOf(
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.ATTENDANCE_STATE,
                    value = attendanceState.toRaw()
                ),
                MobilityInsightReason(
                    code = MobilityInsightReasonCode.LOCATION_STALE_MINUTES,
                    value = staleMinutes.toString()
                )
            )
        )
    }

    private fun buildAdminReviewInsight(input: MobilityInsightInput): MobilityInsight? {
        val workDay = input.workDay ?: return null
        if (!workDay.reviewHold && workDay.deviationFlags.isEmpty()) return null

        val reasons = buildList {
            if (workDay.reviewHold) {
                add(
                    MobilityInsightReason(
                        code = MobilityInsightReasonCode.REVIEW_HOLD_STATUS,
                        value = "active"
                    )
                )
            }
            workDay.deviationFlags.forEach { flag ->
                add(
                    MobilityInsightReason(
                        code = MobilityInsightReasonCode.DEVIATION_FLAG,
                        value = flag.toRaw()
                    )
                )
            }
        }

        return input.toInsight(
            type = MobilityInsightType.ADMIN_REVIEW_NEEDED,
            severity = MobilityInsightSeverity.ELEVATED,
            reasons = reasons
        )
    }
}

data class MobilityInsightInput(
    val driverUid: String? = null,
    val dateEpochDay: Int? = null,
    val workDay: WorkDay? = null,
    val todayAttendance: TodayAttendance? = null,
    val checklist: Checklist? = null,
    val safetyDeclaration: SafetyDeclaration? = null,
    val latestLocation: Location? = null,
    val nowMillis: Long? = null,
    val hasSafetyDeclaration: Boolean? = null
)

private fun MobilityInsightInput.hasStartedDuty(): Boolean {
    return when (resolveAttendanceState()) {
        AttendanceDailyState.OPEN,
        AttendanceDailyState.CLOSED -> true
        else -> false
    }
}

private fun MobilityInsightInput.resolveAttendanceState(): AttendanceDailyState? {
    return when {
        workDay != null -> workDay.attendanceState()
        todayAttendance != null -> todayAttendance.state
        else -> null
    }
}

private fun MobilityInsightInput.resolveSafetyDeclarationPresence(): Boolean? {
    return when {
        safetyDeclaration != null -> true
        hasSafetyDeclaration != null -> hasSafetyDeclaration
        workDay?.hasSafetyDeclaration != null -> workDay.hasSafetyDeclaration
        else -> null
    }
}

private fun MobilityInsightInput.resolveDutyMinutes(): Int? {
    val state = resolveAttendanceState() ?: return null
    return when (state) {
        AttendanceDailyState.CLOSED -> workDay?.totalMinutes?.coerceAtLeast(0)
        AttendanceDailyState.OPEN -> resolveOpenDutyMinutes()
        AttendanceDailyState.NOT_STARTED -> null
    }
}

private fun MobilityInsightInput.resolveOpenDutyMinutes(): Int? {
    val now = nowMillis ?: return null
    val clockInMillis = workDay?.clockInMillis?.takeIf { it > 0L }
        ?: todayAttendance?.clockInMillis?.takeIf { it > 0L }
        ?: return null
    val elapsedMillis = now - clockInMillis
    if (elapsedMillis <= 0L) return null
    return (elapsedMillis / MINUTE_MILLIS).toInt()
}

private fun MobilityInsightInput.resolveDriverUid(): String? {
    return workDay?.driverUid ?: todayAttendance?.driverUid ?: latestLocation?.driverUid ?: driverUid
}

private fun MobilityInsightInput.resolveDateEpochDay(): Int? {
    return workDay?.dateEpochDay ?: todayAttendance?.epochDay?.toInt() ?: dateEpochDay
}

private fun MobilityInsightInput.toInsight(
    type: MobilityInsightType,
    severity: MobilityInsightSeverity,
    reasons: List<MobilityInsightReason>
): MobilityInsight {
    val resolvedDriverUid = resolveDriverUid()
    val resolvedDateEpochDay = resolveDateEpochDay()
    val resolvedWorkdayId = workDay?.id
    val key = listOf(
        type.name.lowercase(),
        resolvedWorkdayId,
        resolvedDriverUid,
        resolvedDateEpochDay?.toString()
    ).filterNotNull().joinToString(separator = ":")

    return MobilityInsight(
        key = key,
        type = type,
        severity = severity,
        titleKey = type.titleKey(),
        messageKey = type.messageKey(),
        recommendedActionKey = type.recommendedActionKey(),
        reasons = reasons,
        driverUid = resolvedDriverUid,
        dateEpochDay = resolvedDateEpochDay,
        workdayId = resolvedWorkdayId
    )
}

private fun MobilityInsightType.titleKey(): String =
    "mobility_insight.${toKeySegment()}.title"

private fun MobilityInsightType.messageKey(): String =
    "mobility_insight.${toKeySegment()}.message"

private fun MobilityInsightType.recommendedActionKey(): String =
    "mobility_insight.${toKeySegment()}.action"

private fun MobilityInsightType.toKeySegment(): String =
    name.lowercase()

private fun AttendanceDailyState.toRaw(): String =
    name.lowercase()
