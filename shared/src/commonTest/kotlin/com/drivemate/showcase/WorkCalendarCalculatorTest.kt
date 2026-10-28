package com.drivemate.showcase

import com.drivemate.showcase.domain.calendar.ComputedWorkSummary
import com.drivemate.showcase.domain.calendar.DefaultRules
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSession
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.computeDailySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class WorkCalendarCalculatorTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val calendar = WorkCalendarYear(year = 2024, defaultRules = DefaultRules())

    @Test
    fun weekdayNormal_noOvertime() {
        val date = LocalDate(2024, 1, 8) // Monday
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(session(date, "07:30", "16:30"))
        )
        assertSummary(summary, net = 480, scheduled = 480, overtime = 0, isWorkday = true)
    }

    @Test
    fun weekdayOvertime() {
        val date = LocalDate(2024, 1, 9) // Tuesday
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(session(date, "07:15", "17:00"))
        )
        assertSummary(summary, net = 525, scheduled = 480, overtime = 45, isWorkday = true)
    }

    @Test
    fun fridayRule_applies() {
        val date = LocalDate(2024, 1, 12) // Friday
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(session(date, "07:30", "16:50"))
        )
        // Jumat 07:30–16:50 = 560 menit − istirahat 80 = 480 bersih, sama dengan Senin–Kamis.
        assertSummary(summary, net = 480, scheduled = 480, overtime = 0, isWorkday = true)
    }

    @Test
    fun fridayOvertime() {
        val date = LocalDate(2024, 1, 19) // Friday
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(session(date, "07:30", "17:30"))
        )
        // Lembur tetap 40 menit: istirahat saling menghapus di `net − netTerjadwal`.
        // Yang bergeser hanya net, 540 → 520.
        assertSummary(summary, net = 520, scheduled = 480, overtime = 40, isWorkday = true)
    }

    @Test
    fun holidayWork_allCountsAsOvertime() {
        val date = LocalDate(2024, 1, 10) // Wednesday
        val holidayCalendar = calendar.copy(
            overrides = listOf(
                WorkdayOverride(date = date, isWorkday = false)
            )
        )
        val summary = computeDailySummary(
            calendar = holidayCalendar,
            date = date,
            sessions = listOf(session(date, "08:00", "13:00", breakOverride = 0))
        )
        assertSummary(summary, net = 300, scheduled = 0, overtime = 300, isWorkday = false)
    }

    @Test
    fun swapWorkSaturday_usesWeekdayRule() {
        val date = LocalDate(2024, 1, 13) // Saturday
        val swapCalendar = calendar.copy(
            overrides = listOf(
                WorkdayOverride(date = date, isWorkday = true, ruleOverride = null)
            )
        )
        val summary = computeDailySummary(
            calendar = swapCalendar,
            date = date,
            sessions = listOf(session(date, "07:30", "16:30"))
        )
        assertSummary(summary, net = 480, scheduled = 480, overtime = 0, isWorkday = true)
    }

    @Test
    fun customOverride_ruleApplied_breakOverrideWins() {
        val date = LocalDate(2024, 1, 11) // Thursday
        val customRule = WorkdayRule(
            startLocalTime = LocalTime(9, 0),
            endLocalTime = LocalTime(16, 0),
            breakMinutes = 90,
        )
        val customCalendar = calendar.copy(
            overrides = listOf(
                WorkdayOverride(date = date, isWorkday = true, ruleOverride = customRule)
            )
        )
        val summary = computeDailySummary(
            calendar = customCalendar,
            date = date,
            sessions = listOf(session(date, "09:00", "16:00", breakOverride = 30))
        )
        assertSummary(summary, net = 390, scheduled = 330, overtime = 60, isWorkday = true)
    }

    @Test
    fun inProgress_excludedFromTotals() {
        val date = LocalDate(2024, 1, 15) // Monday
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(WorkSession(clockInMillis = epochMillis(date, "07:30"), clockOutMillis = null))
        )
        assertSummary(summary, net = 0, scheduled = 480, overtime = 0, isWorkday = true)
        assertTrue(summary.isInProgress)
    }

    @Test
    fun multipleSessions_summedForNet() {
        val date = LocalDate(2024, 1, 16) // Tuesday
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(
                session(date, "07:30", "12:00", breakOverride = 0),
                session(date, "13:00", "17:00", breakOverride = 0)
            )
        )
        assertSummary(summary, net = 510, scheduled = 480, overtime = 30, isWorkday = true)
    }

    private fun session(date: LocalDate, start: String, end: String, breakOverride: Int? = null): WorkSession {
        return WorkSession(
            clockInMillis = epochMillis(date, start),
            clockOutMillis = epochMillis(date, end),
            breakMinutesOverride = breakOverride,
        )
    }

    private fun epochMillis(date: LocalDate, hm: String): Long {
        val (hour, minute) = hm.split(":").map { it.toInt() }
        val dt = LocalDateTime(date.year, date.month, date.day, hour, minute)
        return dt.toInstant(zone).toEpochMilliseconds()
    }

    private fun assertSummary(
        summary: ComputedWorkSummary,
        net: Int,
        scheduled: Int,
        overtime: Int,
        isWorkday: Boolean,
    ) {
        assertEquals(net, summary.netMinutes)
        assertEquals(scheduled, summary.scheduledNetMinutes)
        assertEquals(overtime, summary.overtimeMinutes)
        assertEquals(isWorkday, summary.isWorkday)
    }
}
