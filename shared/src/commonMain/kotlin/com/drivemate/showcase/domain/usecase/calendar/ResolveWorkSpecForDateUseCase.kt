package com.drivemate.showcase.domain.usecase.calendar

import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSpec
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate

/**
 * Resolve effective work spec for the given date.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
class ResolveWorkSpecForDateUseCase {
    operator fun invoke(calendar: WorkCalendarYear, dateEpochDay: Int): WorkSpec? =
        resolveWorkSpecForDate(calendar, dateEpochDay)
}
