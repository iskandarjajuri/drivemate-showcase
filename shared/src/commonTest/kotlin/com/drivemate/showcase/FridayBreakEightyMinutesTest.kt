package com.drivemate.showcase

import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.computeDailySummary
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import com.drivemate.showcase.domain.calendar.scheduledNetMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Istirahat Jumat 60 → 80 menit (Blok 0.1).
 *
 * ## Kenapa angka ini penting
 * DriveMate menjadi dasar perhitungan upah lembur sejak 7 September 2026. Jendela Jumat
 * 07:30–16:50 dengan istirahat 60 menit menghasilkan **500** menit kerja bersih terjadwal, yang
 * membuat total sepekan menjadi 4×480 + 500 = **2420** menit — melampaui batas 40 jam
 * (2400 menit) pada PP 35/2021. Dengan istirahat 80 menit, Jumat menjadi 480 dan totalnya tepat
 * 2400.
 *
 * ## Yang TIDAK berubah, dan ini perlu diketahui
 * Menit **lembur** tidak terpengaruh sama sekali. Buktinya aljabar, bukan kebetulan:
 * `lembur = net − netTerjadwal = (durasi − istirahat) − (jendela − istirahat) = durasi − jendela`.
 * Istirahat saling menghapus. Jadi perubahan ini menggeser **jam normal** yang dibayar, bukan
 * lemburnya — dan itu justru maksudnya. Lihat [fridayOvertimeUnaffectedByBreakChange].
 */
class FridayBreakEightyMinutesTest {

    private val calendar = WorkCalendarYear(year = 2024, timezoneId = "Asia/Jakarta")

    /** Jumat, 12 Januari 2024. */
    private val friday = LocalDate(2024, 1, 12)

    @Test
    fun fridayScheduledNetIsFourHundredEighty() {
        val spec = resolveWorkSpecForDate(calendar, friday.toEpochDays().toInt())
        assertEquals(480, spec?.scheduledNetMinutes())
    }

    @Test
    fun fridayBreakIsEightyMinutes() {
        assertEquals(80, calendar.defaultRules.fridayRule.breakMinutes)
    }

    @Test
    fun fiveWorkdayWeekIsExactlyFortyHours() {
        // Senin 8 Jan s.d. Jumat 12 Jan 2024.
        val week = (8..12).map { day ->
            val d = LocalDate(2024, 1, day)
            resolveWorkSpecForDate(calendar, d.toEpochDays().toInt())?.scheduledNetMinutes() ?: 0
        }
        assertEquals(listOf(480, 480, 480, 480, 480), week)
        assertEquals(2400, week.sum(), "batas 40 jam/pekan PP 35/2021")
    }

    @Test
    fun nothingReturnsFiveHundredForFriday() {
        // Penjaga eksplisit: 500 adalah nilai LAMA. Kalau ada jalur yang masih
        // mengembalikannya, test ini yang menangkapnya — bukan slip gaji.
        val spec = resolveWorkSpecForDate(calendar, friday.toEpochDays().toInt())
        assertTrue(spec!!.scheduledNetMinutes() != 500, "Jumat masih memakai istirahat 60 menit")

        val summary = computeDailySummary(
            calendar = calendar,
            date = friday,
            sessions = listOf(sessionOn(friday, startMin = 7 * 60 + 30, endMin = 16 * 60 + 50)),
        )
        assertTrue(summary.scheduledNetMinutes != 500, "computeDailySummary masih 500")
        assertEquals(480, summary.scheduledNetMinutes)
        assertEquals(480, summary.netMinutes)
        assertEquals(0, summary.overtimeMinutes)
    }

    @Test
    fun fridayOvertimeUnaffectedByBreakChange() {
        // Jumat 07:30–17:30 = jendela + 40 menit. Lembur harus 40 menit baik dengan
        // istirahat 60 maupun 80, karena istirahat saling menghapus di rumusnya.
        // Yang bergeser hanya netMinutes: 540 → 520.
        val date = LocalDate(2024, 1, 19)
        val summary = computeDailySummary(
            calendar = calendar,
            date = date,
            sessions = listOf(sessionOn(date, startMin = 7 * 60 + 30, endMin = 17 * 60 + 30)),
        )
        assertEquals(40, summary.overtimeMinutes)
        assertEquals(520, summary.netMinutes)
        assertEquals(480, summary.scheduledNetMinutes)
    }

    private fun sessionOn(date: LocalDate, startMin: Int, endMin: Int) =
        com.drivemate.showcase.domain.calendar.WorkSession(
            clockInMillis = millisOf(date, startMin),
            clockOutMillis = millisOf(date, endMin),
        )

    private fun millisOf(date: LocalDate, minuteOfDay: Int): Long {
        val jakartaOffsetMinutes = 7 * 60
        val epochMinutes = date.toEpochDays() * 24L * 60L + minuteOfDay - jakartaOffsetMinutes
        return epochMinutes * 60_000L
    }
}
