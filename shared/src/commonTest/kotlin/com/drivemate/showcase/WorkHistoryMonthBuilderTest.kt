package com.drivemate.showcase

import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.DayCalendarKind
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkHistoryDayState
import com.drivemate.showcase.domain.calendar.WorkHistoryMonthRef
import com.drivemate.showcase.domain.calendar.WorkdayOverride
import com.drivemate.showcase.domain.calendar.buildWorkHistoryMonth
import com.drivemate.showcase.domain.calendar.leadingBlankCountForMonth
import com.drivemate.showcase.domain.calendar.shiftWorkHistoryMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Menutup `buildWorkHistoryMonth` — perakit satu bulan yang dipakai KEDUA platform.
 *
 * Nilai test ini bukan sekadar coverage: `shared/src/iosMain` tidak dikompilasi di Linux,
 * jadi ini satu-satunya tempat logika bulan yang nanti dipanggil iOS bisa benar-benar
 * dieksekusi. Dan karena Android memakai fungsi yang sama, suite Maestro Android yang
 * sudah ada berlaku sebagai bukti perilakunya.
 */
class WorkHistoryMonthBuilderTest {

    private val jakarta = TimeZone.of("Asia/Jakarta")
    private val parseHm: (String, Int) -> Long = { hm, day ->
        TimeUtil.parseHm(hm, day.toLong(), jakarta)
    }

    private fun epochDay(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toInt()

    private fun workDay(
        dateEpochDay: Int,
        clockIn: Long = 0L,
        clockOut: Long = 0L,
        totalMinutes: Int = 0
    ) = WorkDay(
        id = "wd-$dateEpochDay",
        driverUid = "driver",
        dateEpochDay = dateEpochDay,
        clockInMillis = clockIn,
        clockOutMillis = clockOut,
        totalMinutes = totalMinutes
    )

    private fun build(
        year: Int,
        month: Int,
        calendar: WorkCalendarYear = WorkCalendarYear(year = year),
        workDays: List<WorkDay> = emptyList(),
        today: Int
    ) = buildWorkHistoryMonth(
        year = year,
        month = month,
        calendar = calendar,
        workDays = workDays,
        todayEpochDay = today,
        parseHm = parseHm
    )

    // ---------- Bentuk bulan ----------

    @Test
    fun month_has_one_row_per_calendar_day() {
        // Agustus 31 hari, Februari 2026 non-kabisat 28 hari, Februari 2024 kabisat 29.
        assertEquals(31, build(2026, 8, today = epochDay(2026, 8, 31)).days.size)
        assertEquals(28, build(2026, 2, today = epochDay(2026, 2, 28)).days.size)
        assertEquals(29, build(2024, 2, today = epochDay(2024, 2, 29)).days.size)
    }

    @Test
    fun december_rolls_into_next_year_without_losing_days() {
        // Bug klasik monthEpochDayRange: month+1 pada Desember harus jadi Januari tahun depan.
        val data = build(2026, 12, today = epochDay(2026, 12, 31))
        assertEquals(31, data.days.size)
        assertEquals(epochDay(2026, 12, 1), data.days.first().dateEpochDay)
        assertEquals(epochDay(2026, 12, 31), data.days.last().dateEpochDay)
    }

    @Test
    fun days_are_ordered_ascending_and_contiguous() {
        val data = build(2026, 9, today = epochDay(2026, 9, 30))
        data.days.zipWithNext().forEach { (a, b) ->
            assertEquals(a.dateEpochDay + 1, b.dateEpochDay)
        }
    }

    // ---------- Offset grid Senin-dulu ----------

    @Test
    fun leading_blanks_are_monday_first_not_sunday_first() {
        // 1 Agustus 2026 = SABTU. Senin-dulu -> 5 sel kosong (Sen,Sel,Rab,Kam,Jum).
        // Grid Minggu-dulu (default Calendar.current di en_US) akan bilang 6 — kalau
        // angka di bawah pernah berubah jadi 6, grid iOS bergeser satu kolom.
        assertEquals(5, leadingBlankCountForMonth(2026, 8))
        assertEquals(5, build(2026, 8, today = epochDay(2026, 8, 31)).leadingBlankCount)
    }

    @Test
    fun a_month_starting_on_monday_has_zero_leading_blanks() {
        // 1 Juni 2026 = SENIN.
        assertEquals(0, leadingBlankCountForMonth(2026, 6))
    }

    @Test
    fun a_month_starting_on_sunday_has_six_leading_blanks() {
        // 1 November 2026 = MINGGU — Minggu adalah kolom TERAKHIR pada grid Senin-dulu.
        assertEquals(6, leadingBlankCountForMonth(2026, 11))
    }

    // ---------- Lima state hari ----------

    @Test
    fun weekend_without_record_is_off_weekend_not_absent() {
        val sunday = epochDay(2026, 8, 2)
        val day = build(2026, 8, today = epochDay(2026, 8, 31)).days.first { it.dateEpochDay == sunday }
        assertEquals(WorkHistoryDayState.OFF_WEEKEND, day.state)
        assertEquals(DayCalendarKind.WEEKEND, day.calendarKind)
        assertNull(day.minutes)
    }

    @Test
    fun scheduled_workday_without_record_is_absent() {
        // Senin 3 Agustus 2026, tidak ada catatan kerja, sudah lampau.
        val monday = epochDay(2026, 8, 3)
        val day = build(2026, 8, today = epochDay(2026, 8, 31)).days.first { it.dateEpochDay == monday }
        assertEquals(WorkHistoryDayState.SCHEDULED_ABSENT, day.state)
    }

    @Test
    fun company_holiday_is_distinct_from_weekend() {
        // Inti SSOT: "libur" (memang bukan hari kerja) HARUS beda dari "tidak hadir",
        // dan libur perusahaan harus beda dari akhir pekan biasa.
        val holiday = epochDay(2026, 8, 17)
        val calendar = WorkCalendarYear(
            year = 2026,
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 8, 17), isWorkday = false, note = null)
            )
        )
        val day = build(2026, 8, calendar = calendar, today = epochDay(2026, 8, 31))
            .days.first { it.dateEpochDay == holiday }
        assertEquals(WorkHistoryDayState.OFF_COMPANY_HOLIDAY, day.state)
        assertEquals(DayCalendarKind.COMPANY_HOLIDAY, day.calendarKind)
    }

    @Test
    fun company_holiday_without_note_is_still_a_company_holiday() {
        // Regresi terhadap kelossyan IOSWorkCalendarSnapshot: bridge lama membuang
        // override yang `note`-nya null (mapNotNull { override.note?... }), sehingga
        // libur perusahaan tanpa catatan kolaps jadi akhir pekan. Jalur ini memakai
        // WorkCalendarYear langsung, jadi note null TIDAK boleh berpengaruh.
        val holiday = epochDay(2026, 9, 15)
        val calendar = WorkCalendarYear(
            year = 2026,
            overrides = listOf(
                WorkdayOverride(date = LocalDate(2026, 9, 15), isWorkday = false, note = null)
            )
        )
        val day = build(2026, 9, calendar = calendar, today = epochDay(2026, 9, 30))
            .days.first { it.dateEpochDay == holiday }
        assertEquals(WorkHistoryDayState.OFF_COMPANY_HOLIDAY, day.state)
    }

    @Test
    fun future_days_are_not_judged_absent() {
        // Hari ini 10 Agustus 2026: sisa bulan belum terjadi, tidak boleh dicap absen.
        val today = epochDay(2026, 8, 10)
        val data = build(2026, 8, today = today)
        data.days.filter { it.dateEpochDay > today }.forEach {
            assertEquals(WorkHistoryDayState.FUTURE, it.state)
        }
        // Dan tanggal 11 (Selasa, hari kerja) tetap FUTURE, bukan SCHEDULED_ABSENT.
        assertEquals(
            WorkHistoryDayState.FUTURE,
            data.days.first { it.dateEpochDay == epochDay(2026, 8, 11) }.state
        )
    }

    @Test
    fun a_record_on_a_weekend_still_counts_as_worked() {
        val sunday = epochDay(2026, 8, 2)
        val data = build(
            2026, 8,
            workDays = listOf(
                workDay(
                    sunday,
                    clockIn = parseHm("08:00", sunday),
                    clockOut = parseHm("12:00", sunday),
                    totalMinutes = 240
                )
            ),
            today = epochDay(2026, 8, 31)
        )
        val day = data.days.first { it.dateEpochDay == sunday }
        assertEquals(WorkHistoryDayState.WORKED, day.state)
        // Kalender tetap bilang akhir pekan — dua fakta terpisah, tidak saling menimpa.
        assertEquals(DayCalendarKind.WEEKEND, day.calendarKind)
        assertNotNull(day.minutes)
    }

    // ---------- Agregasi bulan ----------

    @Test
    fun summary_totals_only_days_that_have_minutes() {
        val d1 = epochDay(2026, 8, 3)
        val d2 = epochDay(2026, 8, 4)
        val data = build(
            2026, 8,
            workDays = listOf(
                workDay(d1, parseHm("07:30", d1), parseHm("16:30", d1), totalMinutes = 540),
                workDay(d2, parseHm("07:30", d2), parseHm("16:30", d2), totalMinutes = 540)
            ),
            today = epochDay(2026, 8, 31)
        )
        assertEquals(2, data.summary.workedDayCount)
        assertEquals(1080, data.summary.totalWorkedMinutes)
    }

    @Test
    fun overtime_is_counted_when_clock_out_is_past_the_reference_window() {
        val d = epochDay(2026, 8, 3)
        // Keluar 18:30 padahal jendela standar berakhir 16:30 -> 120 menit lembur.
        val data = build(
            2026, 8,
            workDays = listOf(
                workDay(d, parseHm("07:30", d), parseHm("18:30", d), totalMinutes = 660)
            ),
            today = epochDay(2026, 8, 31)
        )
        val m = data.days.first { it.dateEpochDay == d }.minutes
        assertNotNull(m)
        assertEquals(120, m.overtimeMinutes)
        assertEquals(540, m.normalMinutes)
        assertEquals(660, m.totalMinutes)
        assertEquals(120, data.summary.totalOvertimeMinutes)
    }

    @Test
    fun scheduled_workday_count_comes_from_the_calendar_not_a_hardcoded_number() {
        // Agustus 2026: 21 weekday. Satu dijadikan libur perusahaan -> penyebut 20,
        // bukan konstanta "20 hari" atau hitungan weekday generik.
        val plain = build(2026, 8, today = epochDay(2026, 8, 31))
        val withHoliday = build(
            2026, 8,
            calendar = WorkCalendarYear(
                year = 2026,
                overrides = listOf(
                    WorkdayOverride(date = LocalDate(2026, 8, 17), isWorkday = false, note = null)
                )
            ),
            today = epochDay(2026, 8, 31)
        )
        assertEquals(plain.summary.scheduledWorkdayCount - 1, withHoliday.summary.scheduledWorkdayCount)
    }

    @Test
    fun records_outside_the_month_are_ignored() {
        // Repository bisa mengembalikan rentang lebih lebar; perakit hanya mengambil
        // yang jatuh dalam bulan yang diminta, bukan menempelkannya ke sel sembarang.
        val insideAug = epochDay(2026, 8, 3)
        val julyDay = epochDay(2026, 7, 20)
        val data = build(
            2026, 8,
            workDays = listOf(
                workDay(insideAug, parseHm("07:30", insideAug), parseHm("16:30", insideAug), 540),
                workDay(julyDay, parseHm("07:30", julyDay), parseHm("16:30", julyDay), 540)
            ),
            today = epochDay(2026, 8, 31)
        )
        assertEquals(1, data.summary.workedDayCount)
        assertEquals(540, data.summary.totalWorkedMinutes)
    }

    @Test
    fun year_and_month_are_echoed_back_for_the_navigator() {
        val data = build(2026, 3, today = epochDay(2026, 3, 15))
        assertEquals(2026, data.year)
        assertEquals(3, data.month)
        assertEquals(epochDay(2026, 3, 15), data.todayEpochDay)
    }
}

/**
 * Navigator bulan. Terpisah dari perakit karena ini murni aritmetika kalender, dan
 * dipakai kedua platform (Android `showPreviousMonth`/`showNextMonth`, iOS
 * `IOSWorkHistoryMonthRef`).
 */
class WorkHistoryMonthShiftTest {

    @Test
    fun forward_inside_the_same_year() {
        val r = shiftWorkHistoryMonth(2026, 3, 1)
        assertEquals(2026, r.year)
        assertEquals(4, r.month)
    }

    @Test
    fun backward_inside_the_same_year() {
        val r = shiftWorkHistoryMonth(2026, 3, -1)
        assertEquals(2026, r.year)
        assertEquals(2, r.month)
    }

    @Test
    fun december_forward_rolls_the_year_over() {
        val r = shiftWorkHistoryMonth(2026, 12, 1)
        assertEquals(2027, r.year)
        assertEquals(1, r.month)
    }

    @Test
    fun january_backward_rolls_the_year_back() {
        val r = shiftWorkHistoryMonth(2026, 1, -1)
        assertEquals(2025, r.year)
        assertEquals(12, r.month)
    }

    @Test
    fun a_delta_larger_than_a_year_is_still_correct() {
        // Cabang "if (month == 1) year - 1" gaya lama diam-diam salah di sini.
        assertEquals(WorkHistoryMonthRef(2027, 3), shiftWorkHistoryMonth(2026, 3, 12))
        assertEquals(WorkHistoryMonthRef(2024, 3), shiftWorkHistoryMonth(2026, 3, -24))
        assertEquals(WorkHistoryMonthRef(2027, 2), shiftWorkHistoryMonth(2026, 1, 13))
    }

    @Test
    fun month_stays_inside_one_to_twelve_for_every_delta_across_four_years() {
        for (delta in -24..24) {
            val r = shiftWorkHistoryMonth(2026, 1, delta)
            assertTrue(r.month in 1..12, "delta=$delta menghasilkan month=${r.month}")
        }
    }

    @Test
    fun zero_delta_is_identity() {
        assertEquals(WorkHistoryMonthRef(2026, 7), shiftWorkHistoryMonth(2026, 7, 0))
    }

    @Test
    fun twelve_single_steps_forward_equals_one_year() {
        var ref = WorkHistoryMonthRef(2026, 5)
        repeat(12) { ref = shiftWorkHistoryMonth(ref.year, ref.month, 1) }
        assertEquals(WorkHistoryMonthRef(2027, 5), ref)
    }
}
