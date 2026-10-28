package com.drivemate.showcase

import com.drivemate.showcase.domain.calendar.DefaultRules
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSpec
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import com.drivemate.showcase.domain.calendar.scheduledNetMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class WorkCalendarResolverTest {

    private val calendar = WorkCalendarYear(year = 2024, defaultRules = DefaultRules())

    @Test
    fun defaultWeekdayRule_appliesForMonday() {
        val date = LocalDate(2024, 1, 8) // Monday
        val spec = resolveWorkSpecForDate(calendar, date.toEpochDays().toInt())
        assertSpec(spec, start = 450, end = 990, breakMinutes = 60, scheduled = 480)
    }

    @Test
    fun fridayRule_applies() {
        val date = LocalDate(2024, 1, 12) // Friday
        val spec = resolveWorkSpecForDate(calendar, date.toEpochDays().toInt())
        assertSpec(spec, start = 450, end = 1010, breakMinutes = 80, scheduled = 480)
    }

    @Test
    fun holidayOverride_makesNonWorkday() {
        val date = LocalDate(2024, 1, 10)
        val holiday = calendar.copy(overrides = listOf(WorkdayOverride(date = date, isWorkday = false)))
        val spec = resolveWorkSpecForDate(holiday, date.toEpochDays().toInt())
        assertNull(spec)
    }

    @Test
    fun swapWorkSaturday_usesWeekdayRule() {
        val date = LocalDate(2024, 1, 13) // Saturday
        val swap = calendar.copy(overrides = listOf(WorkdayOverride(date = date, isWorkday = true)))
        val spec = resolveWorkSpecForDate(swap, date.toEpochDays().toInt())
        assertSpec(spec, start = 450, end = 990, breakMinutes = 60, scheduled = 480)
    }

    @Test
    fun customOverride_appliesCustomRule() {
        val date = LocalDate(2024, 1, 11)
        val customRule = WorkdayRule(LocalTime(9, 0), LocalTime(16, 0), breakMinutes = 30)
        val custom = calendar.copy(
            overrides = listOf(WorkdayOverride(date = date, isWorkday = true, ruleOverride = customRule))
        )
        val spec = resolveWorkSpecForDate(custom, date.toEpochDays().toInt())
        assertSpec(spec, start = 540, end = 960, breakMinutes = 30, scheduled = 390)
    }

    @Test
    fun overrideWinsOverDefault() {
        val date = LocalDate(2024, 1, 12) // Friday
        val customRule = WorkdayRule(LocalTime(8, 0), LocalTime(15, 0), breakMinutes = 60)
        val custom = calendar.copy(
            overrides = listOf(WorkdayOverride(date = date, isWorkday = true, ruleOverride = customRule))
        )
        val spec = resolveWorkSpecForDate(custom, date.toEpochDays().toInt())
        assertSpec(spec, start = 480, end = 900, breakMinutes = 60, scheduled = 360)
    }

    private fun assertSpec(
        spec: WorkSpec?,
        start: Int,
        end: Int,
        breakMinutes: Int,
        scheduled: Int
    ) {
        assertNotNull(spec)
        assertEquals(start, spec.startMinutes)
        assertEquals(end, spec.endMinutes)
        assertEquals(breakMinutes, spec.breakMinutes)
        assertEquals(scheduled, spec.scheduledNetMinutes())
    }
}
