package com.drivemate.showcase.feature.driver.calendar

import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkDay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Adapter to normalize WorkDay summary values for Driver History UI.
 * Workday totals/overtime are treated as canonical because they are produced by
 * the attendance->workday pipeline that already applies calendar SSOT.
 */
fun computeDriverSummary(workDay: WorkDay): DriverWorkSummary {
    val date = LocalDate.fromEpochDays(workDay.dateEpochDay)
    val hint = when {
        workDay.planSource == PlanSource.WEEKEND -> DriverScheduleHint.Holiday
        date.dayOfWeek == DayOfWeek.FRIDAY -> DriverScheduleHint.Friday
        else -> DriverScheduleHint.Weekday
    }
    return DriverWorkSummary(
        netMinutes = workDay.totalMinutes.coerceAtLeast(0),
        overtimeMinutes = workDay.overtimeMinutes.coerceAtLeast(0),
        isInProgress = workDay.clockInMillis > 0L && workDay.clockOutMillis <= 0L,
        scheduleHint = hint
    )
}

data class DriverWorkSummary(
    val netMinutes: Int,
    val overtimeMinutes: Int,
    val isInProgress: Boolean,
    val scheduleHint: DriverScheduleHint
)

enum class DriverScheduleHint {
    Weekday,
    Friday,
    Holiday
}
