package com.drivemate.showcase

import com.drivemate.showcase.core.model.WORK_END_HM_FRIDAY
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.endOfWorkMillis
import com.drivemate.showcase.core.model.startOfWorkMillis
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/**
 * Blok 0.4 — fallback jam kerja yang tahu hari Jumat.
 *
 * ## Apa yang sebenarnya rusak
 * PRD §17 melaporkan dua jalur buta-Jumat: `WORK_*_HM` (`WorkDay.kt`) dan `DEFAULT_WORK_*_HM`
 * (`WorkHistoryMonth.kt`). Diperiksa ke kode: **hanya yang pertama.** `resolveWorkSpecForDate`
 * sudah mengembalikan Jumat dengan benar (start 450, end 1010, istirahat 80), jadi
 * `DEFAULT_WORK_*_HM` hanya menyala untuk hari NON-kerja — di mana tidak ada jendela terjadwal
 * untuk dilampaui, sehingga tidak ada lembur semu yang bisa muncul dari sana.
 *
 * Dampaknya juga bukan "lembur semu" secara langsung, melainkan **default dialog edit rencana
 * kerja** (`IOSBridgeSnapshots.kt:105` mengisi `defaultEndMillis` dari `endOfWorkMillis()`):
 * expat yang mengedit rencana hari Jumat mendapat jam selesai 16:30, dan menyimpannya membuat
 * rencana 20 menit lebih pendek dari jadwal sebenarnya. Lembur semu datang SETELAH itu.
 */
class FridayAwareFallbackTest {

    private val calendar = WorkCalendarYear(year = 2026, timezoneId = "Asia/Jakarta")

    private fun workDay(date: LocalDate) = WorkDay(
        id = "x", driverUid = "u", dateEpochDay = date.toEpochDays().toInt(),
        clockInMillis = 0L, clockOutMillis = 0L, totalMinutes = 0, overtimeMinutes = 0,
    )

    /** Menit-dalam-hari waktu Jakarta dari epoch millis. */
    private fun jakartaMinuteOfDay(millis: Long): Int =
        (((millis / 60_000L) + 7 * 60) % (24 * 60)).toInt()

    @Test
    fun fridayEndsAtSixteenFifty() {
        val friday = LocalDate(2026, 9, 4)
        assertEquals(16 * 60 + 50, jakartaMinuteOfDay(workDay(friday).endOfWorkMillis()))
    }

    @Test
    fun mondayToThursdayStillEndAtSixteenThirty() {
        for (day in listOf(1, 2, 3)) {          // Sen 31 Agu.. pakai Sep 1-3 (Sel,Rab,Kam)
            val d = LocalDate(2026, 9, day)
            assertEquals(
                16 * 60 + 30, jakartaMinuteOfDay(workDay(d).endOfWorkMillis()),
                "hari ke-$day tidak boleh ikut bergeser",
            )
        }
    }

    @Test
    fun allDaysStartAtSevenThirty() {
        // Jumat mulai pada jam yang sama; yang berbeda hanya jam selesainya.
        for (day in 1..7) {
            val d = LocalDate(2026, 9, day)
            assertEquals(7 * 60 + 30, jakartaMinuteOfDay(workDay(d).startOfWorkMillis()))
        }
    }

    @Test
    fun fallbackAgreesWithCalendarDefaultRules() {
        // Literal "16:50" ada di DUA tempat: WORK_END_HM_FRIDAY (core/model) dan
        // DefaultRules.fridayRule (domain/calendar). Duplikasi itu disengaja — core/model tidak
        // boleh mengimpor domain/calendar, itu akan membalik arah ketergantungan lapis. Test
        // inilah yang mencegahnya mendrift diam-diam.
        val friday = LocalDate(2026, 9, 4)
        val spec = resolveWorkSpecForDate(calendar, friday.toEpochDays().toInt())!!
        assertEquals(spec.endMinutes, jakartaMinuteOfDay(workDay(friday).endOfWorkMillis()))
        assertEquals(spec.startMinutes, jakartaMinuteOfDay(workDay(friday).startOfWorkMillis()))
        assertEquals("16:50", WORK_END_HM_FRIDAY)
    }

    @Test
    fun weekendUsesWeekdayWindowBecauseThereIsNoScheduleToExceed() {
        // Sabtu/Minggu bukan hari kerja. Jendela apa pun di sana tidak menghasilkan lembur
        // semu karena seluruh kerja pada hari libur memang dihitung lembur. Dipaku supaya
        // jelas ini keputusan, bukan kelalaian.
        for (d in listOf(LocalDate(2026, 9, 5), LocalDate(2026, 9, 6))) {
            assertEquals(16 * 60 + 30, jakartaMinuteOfDay(workDay(d).endOfWorkMillis()))
        }
    }
}
