package com.drivemate.showcase

import com.drivemate.showcase.domain.calendar.DefaultRules
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSession
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.computeCalendarDrivenWorkdayComputation
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class CalendarDrivenWorkdayComputationTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val calendar = WorkCalendarYear(year = 2024, defaultRules = DefaultRules())

    @Test
    fun defaultRuleDay_usesCalendarPlannedWindowAndNetMinutes() {
        val date = LocalDate(2024, 1, 8) // Monday
        val result = computeCalendarDrivenWorkdayComputation(
            calendar = calendar,
            dateEpochDay = date.toEpochDays().toInt(),
            sessions = listOf(
                WorkSession(
                    clockInMillis = epochMillis(date, 7, 30),
                    clockOutMillis = epochMillis(date, 16, 30)
                )
            )
        )

        assertTrue(result.isWorkday)
        assertEquals(480, result.totalMinutes)
        assertEquals(0, result.overtimeMinutes)
        assertEquals(epochMillis(date, 7, 30), result.plannedStartMillis)
        assertEquals(epochMillis(date, 16, 30), result.plannedEndMillis)
    }

    @Test
    fun overrideRules_areAppliedForHolidayAndCustomDays() {
        val holidayDate = LocalDate(2024, 1, 10)
        val customDate = LocalDate(2024, 1, 11)
        val customRule = WorkdayRule(
            startLocalTime = LocalTime(9, 0),
            endLocalTime = LocalTime(16, 0),
            breakMinutes = 30
        )
        val overrideCalendar = calendar.copy(
            overrides = listOf(
                WorkdayOverride(date = holidayDate, isWorkday = false),
                WorkdayOverride(date = customDate, isWorkday = true, ruleOverride = customRule)
            )
        )

        val holidayResult = computeCalendarDrivenWorkdayComputation(
            calendar = overrideCalendar,
            dateEpochDay = holidayDate.toEpochDays().toInt(),
            sessions = listOf(
                WorkSession(
                    clockInMillis = epochMillis(holidayDate, 8, 0),
                    clockOutMillis = epochMillis(holidayDate, 12, 0)
                )
            )
        )
        assertFalse(holidayResult.isWorkday)
        assertEquals(240, holidayResult.totalMinutes)
        assertEquals(240, holidayResult.overtimeMinutes)
        assertNull(holidayResult.plannedStartMillis)
        assertNull(holidayResult.plannedEndMillis)

        val customResult = computeCalendarDrivenWorkdayComputation(
            calendar = overrideCalendar,
            dateEpochDay = customDate.toEpochDays().toInt(),
            sessions = listOf(
                WorkSession(
                    clockInMillis = epochMillis(customDate, 9, 0),
                    clockOutMillis = epochMillis(customDate, 16, 0)
                )
            )
        )
        assertTrue(customResult.isWorkday)
        assertEquals(390, customResult.totalMinutes)
        assertEquals(0, customResult.overtimeMinutes)
        assertEquals(epochMillis(customDate, 9, 0), customResult.plannedStartMillis)
        assertEquals(epochMillis(customDate, 16, 0), customResult.plannedEndMillis)
    }

    @Test
    fun firstAndLastDayOfYear_matchResolvedWorkSpecWindow() {
        val yearCalendar = WorkCalendarYear(year = 2026, defaultRules = DefaultRules())
        assertPlannedWindowMatchesResolvedSpec(yearCalendar, LocalDate(2026, 1, 1))
        assertPlannedWindowMatchesResolvedSpec(yearCalendar, LocalDate(2026, 12, 31))
    }

    private fun assertPlannedWindowMatchesResolvedSpec(calendar: WorkCalendarYear, date: LocalDate) {
        val epochDay = date.toEpochDays().toInt()
        val result = computeCalendarDrivenWorkdayComputation(
            calendar = calendar,
            dateEpochDay = epochDay,
            sessions = emptyList()
        )
        val spec = resolveWorkSpecForDate(calendar, epochDay)
        if (spec == null) {
            assertFalse(result.isWorkday)
            assertNull(result.plannedStartMillis)
            assertNull(result.plannedEndMillis)
            return
        }
        assertTrue(result.isWorkday)
        assertEquals(epochMillis(date, spec.startMinutes / 60, spec.startMinutes % 60), result.plannedStartMillis)
        assertEquals(epochMillis(date, spec.endMinutes / 60, spec.endMinutes % 60), result.plannedEndMillis)
    }

    private fun epochMillis(date: LocalDate, hour: Int, minute: Int): Long {
        return LocalDateTime(date.year, date.month, date.day, hour, minute)
            .toInstant(zone)
            .toEpochMilliseconds()
    }
}
