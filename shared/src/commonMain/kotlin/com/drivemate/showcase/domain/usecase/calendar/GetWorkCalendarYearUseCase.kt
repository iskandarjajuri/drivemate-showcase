package com.drivemate.showcase.domain.usecase.calendar

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.repository.WorkCalendarRepository

/**
 * Load a calendar year.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
class GetWorkCalendarYearUseCase(
    private val repo: WorkCalendarRepository
) {
    suspend operator fun invoke(year: Int): AppResult<WorkCalendarYear> = repo.getYear(year)
}
