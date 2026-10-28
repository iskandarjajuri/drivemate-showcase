package com.drivemate.showcase.domain.repository

import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.calendar.WorkCalendarYear

/**
 * Repository contract for work calendar year data.
 * Rules are defined in docs/work_calendar/SSOT.md.
 */
interface WorkCalendarRepository {
    suspend fun getYear(year: Int): AppResult<WorkCalendarYear>
    suspend fun upsertYear(calendar: WorkCalendarYear): AppResult<Unit>
    suspend fun getActiveYear(nowEpochDay: Int, zoneId: String): AppResult<Int>
}
