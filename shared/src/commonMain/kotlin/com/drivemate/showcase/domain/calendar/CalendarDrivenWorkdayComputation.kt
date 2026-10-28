package com.drivemate.showcase.domain.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Calendar-driven workday computation used by attendance -> workday generation.
 * Rules are resolved from WorkCalendar SSOT (default rules + overrides).
 */
data class CalendarDrivenWorkdayComputation(
    val totalMinutes: Int,
    val overtimeMinutes: Int,
    val isWorkday: Boolean,
    val plannedStartMillis: Long?,
    val plannedEndMillis: Long?,
)

/**
 * Compute total/overtime/planned window for a day from calendar SSOT.
 */
fun computeCalendarDrivenWorkdayComputation(
    calendar: WorkCalendarYear,
    dateEpochDay: Int,
    sessions: List<WorkSession>,
): CalendarDrivenWorkdayComputation {
    val date = LocalDate.fromEpochDays(dateEpochDay)
    val summary = computeDailySummary(
        calendar = calendar,
        date = date,
        sessions = sessions
    )
    val workSpec = resolveWorkSpecForDate(calendar, dateEpochDay)
    val zone = TimeZone.of(calendar.timezoneId)
    return CalendarDrivenWorkdayComputation(
        totalMinutes = summary.netMinutes,
        overtimeMinutes = summary.overtimeMinutes,
        isWorkday = summary.isWorkday,
        plannedStartMillis = workSpec?.let { epochMillisFromMinutes(date, it.startMinutes, zone) },
        plannedEndMillis = workSpec?.let { epochMillisFromMinutes(date, it.endMinutes, zone) }
    )
}

private fun epochMillisFromMinutes(date: LocalDate, minutes: Int, zone: TimeZone): Long {
    val hour = minutes / 60
    val minute = minutes % 60
    return LocalDateTime(date.year, date.month, date.day, hour, minute)
        .toInstant(zone)
        .toEpochMilliseconds()
}
