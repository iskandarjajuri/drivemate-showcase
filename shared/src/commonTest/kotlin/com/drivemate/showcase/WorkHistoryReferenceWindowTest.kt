package com.drivemate.showcase

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_END_HM
import com.drivemate.showcase.domain.calendar.DEFAULT_WORK_START_HM
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.WorkdayRule
import com.drivemate.showcase.domain.calendar.resolveWorkHistoryReferenceWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Menutup KETIGA cabang prioritas `resolveWorkHistoryReferenceWindow` + batas tengah malam
 * Asia/Jakarta. Sebelum Batch B logika ini private di ViewModel Android dan nol test.
 */
class WorkHistoryReferenceWindowTest {

    private val jakarta = TimeZone.of("Asia/Jakarta")
    private val parseHm: (String, Int) -> Long = { hm, day ->
        TimeUtil.parseHm(hm, day.toLong(), jakarta)
    }

    private fun epochDay(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toInt()

    private fun workDay(
        dateEpochDay: Int,
        plannedStart: Long? = null,
        plannedEnd: Long? = null
    ) = WorkDay(
        id = "wd",
        driverUid = "driver",
        dateEpochDay = dateEpochDay,
        clockInMillis = 0L,
        clockOutMillis = 0L,
        totalMinutes = 0,
        plannedStartMillis = plannedStart,
        plannedEndMillis = plannedEnd
    )

    // ---------- Cabang 1: rencana eksplisit menang ----------

    @Test
    fun explicit_plan_wins_over_calendar() {
        val day = epochDay(2026, 8, 3) // Senin, hari kerja
        val w = resolveWorkHistoryReferenceWindow(
            workDay = workDay(day, plannedStart = 111L, plannedEnd = 222L),
            calendar = WorkCalendarYear(year = 2026),
            dateEpochDay = day,
            parseHm = parseHm
        )
        assertEquals(111L, w.startMillis)
        assertEquals(222L, w.endMillis)
        assertTrue(w.isExplicitPlan)
    }

    @Test
    fun half_a_plan_is_not_a_plan() {
        // Hanya salah satu ujung terisi -> JANGAN dipakai; jatuh ke kalender.
        val day = epochDay(2026, 8, 3)
        val onlyStart = resolveWorkHistoryReferenceWindow(
            workDay(day, plannedStart = 111L, plannedEnd = null),
            WorkCalendarYear(year = 2026), day, parseHm
        )
        val onlyEnd = resolveWorkHistoryReferenceWindow(
            workDay(day, plannedStart = null, plannedEnd = 222L),
            WorkCalendarYear(year = 2026), day, parseHm
        )
        assertFalse(onlyStart.isExplicitPlan)
        assertFalse(onlyEnd.isExplicitPlan)
        assertEquals(parseHm("07:30", day), onlyStart.startMillis)
        assertEquals(parseHm("07:30", day), onlyEnd.startMillis)
    }

    // ---------- Cabang 2: WorkSpec kalender ----------

    @Test
    fun weekday_uses_calendar_weekday_rule() {
        val day = epochDay(2026, 8, 3) // Senin -> 07:30-16:30
        val w = resolveWorkHistoryReferenceWindow(
            workDay(day), WorkCalendarYear(year = 2026), day, parseHm
        )
        assertFalse(w.isExplicitPlan)
        assertEquals(parseHm("07:30", day), w.startMillis)
        assertEquals(parseHm("16:30", day), w.endMillis)
    }

    @Test
    fun friday_uses_friday_rule_not_weekday_rule() {
        val day = epochDay(2026, 8, 7) // Jumat -> 07:30-16:50
        val w = resolveWorkHistoryReferenceWindow(
            workDay(day), WorkCalendarYear(year = 2026), day, parseHm
        )
        assertEquals(parseHm("16:50", day), w.endMillis)
    }

    @Test
    fun custom_override_rule_is_honoured() {
        val day = epochDay(2026, 8, 5)
        val cal = WorkCalendarYear(
            year = 2026,
            overrides = listOf(
                WorkdayOverride(
                    date = LocalDate(2026, 8, 5),
                    isWorkday = true,
                    ruleOverride = WorkdayRule(
                        startLocalTime = LocalTime(6, 0),
                        endLocalTime = LocalTime(14, 15),
                        breakMinutes = 45
                    )
                )
            )
        )
        val w = resolveWorkHistoryReferenceWindow(workDay(day), cal, day, parseHm)
        assertEquals(parseHm("06:00", day), w.startMillis)
        assertEquals(parseHm("14:15", day), w.endMillis)
    }

    // ---------- Cabang 3: fallback jam standar ----------

    @Test
    fun weekend_falls_back_to_standard_hours() {
        // Sabtu bukan hari kerja -> resolveWorkSpecForDate null -> fallback.
        val day = epochDay(2026, 8, 1)
        val w = resolveWorkHistoryReferenceWindow(
            workDay(day), WorkCalendarYear(year = 2026), day, parseHm
        )
        assertEquals(parseHm(DEFAULT_WORK_START_HM, day), w.startMillis)
        assertEquals(parseHm(DEFAULT_WORK_END_HM, day), w.endMillis)
    }

    @Test
    fun company_holiday_falls_back_to_standard_hours() {
        val day = epochDay(2026, 8, 17)
        val cal = WorkCalendarYear(
            year = 2026,
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 17), isWorkday = false)
            )
        )
        val w = resolveWorkHistoryReferenceWindow(workDay(day), cal, day, parseHm)
        assertEquals(parseHm("07:30", day), w.startMillis)
    }

    // ---------- Batas tengah malam Asia/Jakarta ----------

    @Test
    fun window_lands_on_the_requested_jakarta_day_not_utc_day() {
        // 07:30 Jakarta = 00:30 UTC hari yang sama. Kalau ada yang salah pakai UTC,
        // jendelanya akan bergeser satu hari dan test ini menangkapnya.
        val day = epochDay(2026, 8, 3)
        val w = resolveWorkHistoryReferenceWindow(
            workDay(day), WorkCalendarYear(year = 2026), day, parseHm
        )
        val dayStartJakarta = parseHm("00:00", day)
        val dayEndJakarta = parseHm("00:00", day + 1)
        assertTrue(w.startMillis >= dayStartJakarta, "start bocor ke hari sebelumnya")
        assertTrue(w.endMillis < dayEndJakarta, "end bocor ke hari berikutnya")
        assertEquals(7 * 60 + 30, ((w.startMillis - dayStartJakarta) / 60_000L).toInt())
    }

    @Test
    fun consecutive_days_are_exactly_24h_apart() {
        val d1 = epochDay(2026, 8, 3)
        val d2 = epochDay(2026, 8, 4)
        val w1 = resolveWorkHistoryReferenceWindow(workDay(d1), WorkCalendarYear(2026), d1, parseHm)
        val w2 = resolveWorkHistoryReferenceWindow(workDay(d2), WorkCalendarYear(2026), d2, parseHm)
        assertEquals(24 * 60 * 60 * 1000L, w2.startMillis - w1.startMillis)
    }

    @Test
    fun broken_calendar_minutes_are_clamped_not_thrown() {
        // Nilai kalender rusak (menit > 1 hari) tidak boleh melempar.
        val day = epochDay(2026, 8, 5)
        val cal = WorkCalendarYear(
            year = 2026,
            overrides = listOf(
                WorkdayOverride(
                    date = LocalDate(2026, 8, 5),
                    isWorkday = true,
                    ruleOverride = WorkdayRule(
                        startLocalTime = LocalTime(23, 59),
                        endLocalTime = LocalTime(23, 59),
                        breakMinutes = 0
                    )
                )
            )
        )
        val w = resolveWorkHistoryReferenceWindow(workDay(day), cal, day, parseHm)
        assertEquals(parseHm("23:59", day), w.startMillis)
    }
}
