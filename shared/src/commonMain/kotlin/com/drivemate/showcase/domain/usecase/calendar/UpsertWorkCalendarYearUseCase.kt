package com.drivemate.showcase.domain.usecase.calendar

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.repository.WorkCalendarRepository

/**
 * Create or update a calendar year.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
class UpsertWorkCalendarYearUseCase(
    private val repo: WorkCalendarRepository
) {
    suspend operator fun invoke(calendar: WorkCalendarYear): AppResult<Unit> = repo.upsertYear(calendar)
}
