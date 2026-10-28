@file:Suppress("DEPRECATION")
package com.drivemate.showcase.domain.calendar

import kotlin.math.max
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pure daily summary calculation.
 * Rules are defined in docs/work_calendar/SSOT.md and docs/work_calendar/TEST_CASES.md.
 *
 * Sessions are associated to the given [date] by the clock-in local date in [calendar.timezoneId].
 * Sessions that span midnight are counted by clock-in date only.
 */
fun computeDailySummary(
    calendar: WorkCalendarYear,
    date: LocalDate,
    sessions: List<WorkSession>,
): ComputedWorkSummary {
    val zone = TimeZone.of(calendar.timezoneId)
    val resolved = resolveRule(calendar, date)
    val netResult = computeNetMinutes(date, sessions, zone, resolved.rule)
    val scheduledNetMinutes = if (resolved.isWorkday && resolved.rule != null) {
        minutesBetween(resolved.rule.startLocalTime, resolved.rule.endLocalTime) - resolved.rule.breakMinutes
    } else {
        0
    }
    val overtimeMinutes = if (resolved.isWorkday) {
        max(0, netResult.netMinutes - scheduledNetMinutes)
    } else {
        max(0, netResult.netMinutes)
    }
    return ComputedWorkSummary(
        netMinutes = netResult.netMinutes,
        scheduledNetMinutes = scheduledNetMinutes,
        overtimeMinutes = overtimeMinutes,
        isWorkday = resolved.isWorkday,
        isInProgress = netResult.isInProgress,
    )
}

private data class NetResult(
    val netMinutes: Int,
    val isInProgress: Boolean,
)

private fun computeNetMinutes(
    date: LocalDate,
    sessions: List<WorkSession>,
    zone: TimeZone,
    rule: WorkdayRule?,
): NetResult {
    var netMinutes = 0
    var isInProgress = false
    for (session in sessions) {
        if (sessionLocalDate(session.clockInMillis, zone) != date) continue
        val clockOut = session.clockOutMillis
        if (clockOut == null) {
            isInProgress = true
            continue
        }
        val durationMinutes = ((clockOut - session.clockInMillis) / 60_000L).toInt()
        val breakMinutes = session.breakMinutesOverride ?: rule?.breakMinutes ?: 0
        netMinutes += (durationMinutes - breakMinutes)
    }
    return NetResult(netMinutes, isInProgress)
}

private data class ResolvedRule(
    val isWorkday: Boolean,
    val rule: WorkdayRule?,
)

private fun resolveRule(calendar: WorkCalendarYear, date: LocalDate): ResolvedRule {
    val override = calendar.overrides.firstOrNull { it.date == date }
    if (override != null) {
        if (!override.isWorkday) return ResolvedRule(isWorkday = false, rule = null)
        val rule = override.ruleOverride ?: defaultRuleForDate(calendar, date, treatWeekendAsWeekday = true)
        return ResolvedRule(isWorkday = true, rule = rule)
    }
    return defaultWorkdayRule(calendar, date)
}

private fun defaultWorkdayRule(calendar: WorkCalendarYear, date: LocalDate): ResolvedRule {
    val rule = defaultRuleForDate(calendar, date, treatWeekendAsWeekday = false)
    return if (rule == null) {
        ResolvedRule(isWorkday = false, rule = null)
    } else {
        ResolvedRule(isWorkday = true, rule = rule)
    }
}

private fun defaultRuleForDate(
    calendar: WorkCalendarYear,
    date: LocalDate,
    treatWeekendAsWeekday: Boolean,
): WorkdayRule? {
    return when (date.dayOfWeek) {
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY -> calendar.defaultRules.weekdayRule
        DayOfWeek.FRIDAY -> calendar.defaultRules.fridayRule
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY -> if (treatWeekendAsWeekday) calendar.defaultRules.weekdayRule else null
    }
}

private fun minutesBetween(start: LocalTime, end: LocalTime): Int {
    val startMinutes = (start.hour * 60) + start.minute
    val endMinutes = (end.hour * 60) + end.minute
    return endMinutes - startMinutes
}

private fun sessionLocalDate(epochMillis: Long, zone: TimeZone): LocalDate {
    return Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(zone)
        .date
}
