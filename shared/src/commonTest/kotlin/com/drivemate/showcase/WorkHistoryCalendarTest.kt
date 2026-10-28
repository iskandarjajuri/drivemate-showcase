package com.drivemate.showcase

import com.drivemate.showcase.domain.calendar.DayCalendarKind
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkHistoryDayState
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.monthEpochDayRange
import com.drivemate.showcase.domain.calendar.resolveDayCalendarKind
import com.drivemate.showcase.domain.calendar.resolveWorkHistoryDayState
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import com.drivemate.showcase.domain.calendar.scheduledWorkdayCountInMonth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkHistoryCalendarTest {

    private fun epochDay(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toInt()

    // Agustus 2026: 1 Agu = Sabtu, 3 Agu = Senin.
    private val plain = WorkCalendarYear(year = 2026)

    // ---------- klasifikasi 3 state ----------

    @Test
    fun weekday_is_scheduled_workday() {
        assertEquals(
            DayCalendarKind.SCHEDULED_WORKDAY,
            resolveDayCalendarKind(plain, epochDay(2026, 8, 3))
        )
    }

    @Test
    fun weekend_is_weekend_not_holiday() {
        assertEquals(
            DayCalendarKind.WEEKEND,
            resolveDayCalendarKind(plain, epochDay(2026, 8, 1))
        )
    }

    @Test
    fun company_holiday_is_distinguished_from_weekend() {
        // Ini inti gap yang diperbaiki: dua-duanya "bukan hari kerja", tapi HARUS beda.
        val withHoliday = plain.copy(
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 17), isWorkday = false, note = "HUT RI")
            )
        )
        val holiday = epochDay(2026, 8, 17)   // Senin, biasanya hari kerja
        val weekend = epochDay(2026, 8, 1)    // Sabtu

        assertEquals(DayCalendarKind.COMPANY_HOLIDAY, resolveDayCalendarKind(withHoliday, holiday))
        assertEquals(DayCalendarKind.WEEKEND, resolveDayCalendarKind(withHoliday, weekend))

        // resolveWorkSpecForDate lama TIDAK bisa membedakan keduanya — keduanya null.
        assertNull(resolveWorkSpecForDate(withHoliday, holiday))
        assertNull(resolveWorkSpecForDate(withHoliday, weekend))
    }

    @Test
    fun workday_swap_override_turns_weekend_into_workday() {
        val withSwap = plain.copy(
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 1), isWorkday = true)
            )
        )
        val sat = epochDay(2026, 8, 1)
        assertEquals(DayCalendarKind.SCHEDULED_WORKDAY, resolveDayCalendarKind(withSwap, sat))
        assertNotNull(resolveWorkSpecForDate(withSwap, sat))
    }

    @Test
    fun classification_never_disagrees_with_resolveWorkSpecForDate() {
        // Invariant: SCHEDULED_WORKDAY <=> resolveWorkSpecForDate != null, untuk SEMUA hari
        // di bulan itu, termasuk yang kena override.
        val cal = plain.copy(
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 17), isWorkday = false),
                WorkdayOverride(date = LocalDate(2026, 8, 1), isWorkday = true)
            )
        )
        for (day in monthEpochDayRange(2026, 8)) {
            val isWorkday = resolveDayCalendarKind(cal, day) == DayCalendarKind.SCHEDULED_WORKDAY
            val hasSpec = resolveWorkSpecForDate(cal, day) != null
            assertEquals(hasSpec, isWorkday, "beda pendapat di epochDay=$day")
        }
    }

    // ---------- penyebut "X/Y" ----------

    @Test
    fun month_range_covers_every_day() {
        assertEquals(31, monthEpochDayRange(2026, 8).count())
        assertEquals(28, monthEpochDayRange(2026, 2).count())
        assertEquals(31, monthEpochDayRange(2026, 12).count())
    }

    @Test
    fun december_range_rolls_into_next_year() {
        val r = monthEpochDayRange(2026, 12)
        assertEquals(epochDay(2026, 12, 1), r.first)
        assertEquals(epochDay(2026, 12, 31), r.last)
    }

    @Test
    fun scheduled_count_excludes_weekends() {
        // Agustus 2026: 31 hari, 5 Sabtu + 5 Minggu = 21 hari kerja.
        assertEquals(21, scheduledWorkdayCountInMonth(plain, 2026, 8))
    }

    @Test
    fun company_holiday_reduces_scheduled_count() {
        val withHoliday = plain.copy(
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 17), isWorkday = false)
            )
        )
        assertEquals(20, scheduledWorkdayCountInMonth(withHoliday, 2026, 8))
    }

    @Test
    fun workday_swap_increases_scheduled_count() {
        val withSwap = plain.copy(
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 1), isWorkday = true)
            )
        )
        assertEquals(22, scheduledWorkdayCountInMonth(withSwap, 2026, 8))
    }

    // ---------- 4 state UI ----------

    @Test
    fun worked_wins_regardless_of_calendar() {
        val s = resolveWorkHistoryDayState(
            calendarKind = DayCalendarKind.WEEKEND,
            hasWorkRecord = true,
            dateEpochDay = 100,
            todayEpochDay = 200
        )
        assertEquals(WorkHistoryDayState.WORKED, s)
    }

    @Test
    fun scheduled_workday_without_record_is_absent_not_off() {
        val s = resolveWorkHistoryDayState(
            calendarKind = DayCalendarKind.SCHEDULED_WORKDAY,
            hasWorkRecord = false,
            dateEpochDay = 100,
            todayEpochDay = 200
        )
        assertEquals(WorkHistoryDayState.SCHEDULED_ABSENT, s)
    }

    @Test
    fun weekend_and_company_holiday_map_to_different_states() {
        val weekend = resolveWorkHistoryDayState(
            DayCalendarKind.WEEKEND, false, 100, 200
        )
        val holiday = resolveWorkHistoryDayState(
            DayCalendarKind.COMPANY_HOLIDAY, false, 100, 200
        )
        assertEquals(WorkHistoryDayState.OFF_WEEKEND, weekend)
        assertEquals(WorkHistoryDayState.OFF_COMPANY_HOLIDAY, holiday)
    }

    @Test
    fun future_scheduled_day_is_not_marked_absent() {
        // Hari kerja yang belum terjadi tidak boleh dituduh bolos.
        val s = resolveWorkHistoryDayState(
            calendarKind = DayCalendarKind.SCHEDULED_WORKDAY,
            hasWorkRecord = false,
            dateEpochDay = 300,
            todayEpochDay = 200
        )
        assertEquals(WorkHistoryDayState.FUTURE, s)
    }

    @Test
    fun today_itself_is_not_future() {
        val s = resolveWorkHistoryDayState(
            DayCalendarKind.SCHEDULED_WORKDAY, false, 200, 200
        )
        assertEquals(WorkHistoryDayState.SCHEDULED_ABSENT, s)
    }
}
