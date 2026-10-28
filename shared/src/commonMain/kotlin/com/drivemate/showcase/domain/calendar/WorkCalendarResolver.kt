package com.drivemate.showcase.domain.calendar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Resolve the effective work spec for a given date (epoch day) using SSOT rules.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
fun resolveWorkSpecForDate(calendar: WorkCalendarYear, dateEpochDay: Int): WorkSpec? {
    val date = LocalDate.fromEpochDays(dateEpochDay)
    val override = calendar.overrides.firstOrNull { it.date == date }
    if (override != null) {
        if (!override.isWorkday) return null
        val rule = override.ruleOverride ?: defaultRuleForDate(calendar, date, treatWeekendAsWeekday = true)
        return rule?.toWorkSpec()
    }
    val rule = defaultRuleForDate(calendar, date, treatWeekendAsWeekday = false)
    return rule?.toWorkSpec()
}

/**
 * Workday override kind derived from fields.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
val WorkdayOverride.kind: OverrideKind
    get() = when {
        !isWorkday -> OverrideKind.HOLIDAY
        ruleOverride != null -> OverrideKind.CUSTOM
        else -> OverrideKind.WORKDAY_SWAP
    }

/**
 * Epoch day derived from the override date.
 */
val WorkdayOverride.dateEpochDay: Int
    get() = date.toEpochDays().toInt()

/**
 * Convert a time-based rule into a minute-based work spec.
 */
fun WorkdayRule.toWorkSpec(): WorkSpec {
    val start = (startLocalTime.hour * 60) + startLocalTime.minute
    val end = (endLocalTime.hour * 60) + endLocalTime.minute
    return WorkSpec(startMinutes = start, endMinutes = end, breakMinutes = breakMinutes)
}

/**
 * Scheduled net minutes for a work spec.
 */
fun WorkSpec.scheduledNetMinutes(): Int {
    return (endMinutes - startMinutes - breakMinutes).coerceAtLeast(0)
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
