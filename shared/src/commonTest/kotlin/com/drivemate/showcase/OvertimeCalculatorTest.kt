package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.computeAttendanceTimelineZones
import com.drivemate.showcase.domain.calendar.WorkCalendarYear
import com.drivemate.showcase.domain.calendar.WorkSession
import com.drivemate.showcase.domain.calendar.computeDailySummary
import com.drivemate.showcase.domain.calendar.computeDayWorkBreakdown
import com.drivemate.showcase.domain.calendar.resolveWorkSpecForDate
import com.drivemate.showcase.domain.calendar.scheduledNetMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.datetime.LocalDate

/**
 * Blok 0.2 — Mesin A sebagai satu-satunya penghasil angka lembur.
 *
 * Test terpenting di file ini adalah [agreesWithComputeDailySummaryForTheSameDay]: ia mengunci
 * bahwa fungsi baru dan `computeDailySummary` (yang menulis catatan resmi lewat Cloud Function)
 * menjawab **identik** untuk hari yang sama. Kalau keduanya bisa berbeda, kita tidak menyatukan
 * dua mesin — kita menambah mesin ketiga.
 */
class OvertimeCalculatorTest {

    private val calendar = WorkCalendarYear(year = 2024, timezoneId = "Asia/Jakarta")
    private val jakartaOffsetMinutes = 7 * 60

    private fun millis(date: LocalDate, minuteOfDay: Int): Long =
        (date.toEpochDays() * 24L * 60L + minuteOfDay - jakartaOffsetMinutes) * 60_000L

    private fun windowFor(date: LocalDate): Pair<AttendanceReferenceWindow, Int> {
        val spec = resolveWorkSpecForDate(calendar, date.toEpochDays().toInt())!!
        return AttendanceReferenceWindow(
            startMillis = millis(date, spec.startMinutes),
            endMillis = millis(date, spec.endMinutes),
            isExplicitPlan = false,
        ) to spec.breakMinutes
    }

    // MARK: - Kesetaraan dengan catatan resmi

    @Test
    fun agreesWithComputeDailySummaryForTheSameDay() {
        // Enam bentuk hari yang berbeda, semuanya harus cocok menit-per-menit dengan Mesin A.
        val cases = listOf(
            Triple(LocalDate(2024, 1, 8), 7 * 60 + 30, 16 * 60 + 30),  // Senin tepat jadwal
            Triple(LocalDate(2024, 1, 8), 7 * 60, 16 * 60 + 30),       // Senin masuk 30 mnt awal
            Triple(LocalDate(2024, 1, 9), 7 * 60 + 15, 17 * 60),       // Selasa awal + lambat
            Triple(LocalDate(2024, 1, 10), 7 * 60 + 30, 19 * 60),      // Rabu lembur panjang
            Triple(LocalDate(2024, 1, 12), 7 * 60 + 30, 16 * 60 + 50), // Jumat tepat jadwal
            Triple(LocalDate(2024, 1, 12), 7 * 60 + 30, 17 * 60 + 30), // Jumat lembur
        )
        for ((date, startMin, endMin) in cases) {
            val (window, breakMinutes) = windowFor(date)
            val mine = computeDayWorkBreakdown(
                clockInMillis = millis(date, startMin),
                clockOutMillis = millis(date, endMin),
                window = window,
                breakMinutes = breakMinutes,
            )
            val official = computeDailySummary(
                calendar = calendar,
                date = date,
                sessions = listOf(
                    WorkSession(
                        clockInMillis = millis(date, startMin),
                        clockOutMillis = millis(date, endMin),
                    )
                ),
            )
            val label = "$date $startMin->$endMin"
            assertEquals(official.netMinutes, mine.netMinutes, "net $label")
            assertEquals(official.scheduledNetMinutes, mine.scheduledNetMinutes, "terjadwal $label")
            assertEquals(official.overtimeMinutes, mine.overtimeMinutes, "lembur $label")
        }
    }

    // MARK: - Di mana kedua mesin BENAR-BENAR berbeda

    /**
     * **KOREKSI ATAS PRD §17.** Dokumen itu memakai contoh "masuk 07:00, keluar 16:30, Senin"
     * dan mengklaim Mesin A → 0 menit sementara Mesin B → 30 menit. Aritmetikanya salah:
     * 07:00→16:30 adalah 570 menit (9j30m), bukan 540; dikurangi istirahat 60 menjadi **510**
     * menit bersih, bukan 480. Jadi Mesin A pun menghasilkan `510 − 480 = 30` menit — untuk
     * contoh itu **kedua mesin justru sepakat.**
     *
     * Ini bukan nitpick. Kalau saya "memperbaiki" Mesin A supaya menghasilkan 0 pada contoh itu,
     * saya akan merusak angka yang sudah tersimpan sebagai catatan resmi.
     */
    @Test
    fun prdExampleActuallyAgrees_documentWasWrong() {
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)
        val clockIn = millis(date, 7 * 60)
        val clockOut = millis(date, 16 * 60 + 30)

        val mesinA = computeDayWorkBreakdown(clockIn, clockOut, window, breakMinutes)
        assertEquals(510, mesinA.netMinutes, "570 bruto − 60 istirahat")
        assertEquals(480, mesinA.scheduledNetMinutes)
        assertEquals(30, mesinA.overtimeMinutes)

        val mesinB = computeAttendanceTimelineZones(window, clockIn, clockOut)
        assertEquals(30, mesinB.earlyOvertimeMinutes + mesinB.lateOvertimeMinutes)
    }

    /**
     * Perbedaan yang NYATA: shift yang **bergeser**, bukan **memanjang**.
     *
     * Mesin A mengukur *berapa banyak* seseorang bekerja. Mesin B mengukur *kapan*. Keduanya
     * sepakat selama harinya lebih panjang dari jadwal, dan berbeda begitu harinya hanya
     * digeser.
     *
     * Untuk dasar upah, Mesin A yang benar: driver yang masuk 07:00 dan keluar 16:00 bekerja
     * tepat sepanjang jadwalnya. Ia tidak bekerja lembur — ia bekerja pada jam yang berbeda.
     */
    @Test
    fun shiftedShiftIsWhereEnginesDiverge() {
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)

        // Digeser 30 menit lebih awal: 07:00→16:00, tetap 9 jam.
        val geserAwal = computeDayWorkBreakdown(
            millis(date, 7 * 60), millis(date, 16 * 60), window, breakMinutes,
        )
        val zonaAwal = computeAttendanceTimelineZones(
            window, millis(date, 7 * 60), millis(date, 16 * 60),
        )
        assertEquals(0, geserAwal.overtimeMinutes, "kerja tepat sepanjang jadwal -> nol lembur")
        assertEquals(30, zonaAwal.earlyOvertimeMinutes + zonaAwal.lateOvertimeMinutes)
        assertNotEquals(
            geserAwal.overtimeMinutes,
            zonaAwal.earlyOvertimeMinutes + zonaAwal.lateOvertimeMinutes,
        )

        // Digeser 30 menit lebih lambat: 08:00→17:00, juga tetap 9 jam.
        val geserLambat = computeDayWorkBreakdown(
            millis(date, 8 * 60), millis(date, 17 * 60), window, breakMinutes,
        )
        val zonaLambat = computeAttendanceTimelineZones(
            window, millis(date, 8 * 60), millis(date, 17 * 60),
        )
        assertEquals(0, geserLambat.overtimeMinutes)
        assertEquals(30, zonaLambat.earlyOvertimeMinutes + zonaLambat.lateOvertimeMinutes)
    }

    @Test
    fun lengthenedShiftIsWhereEnginesAgree() {
        // Harinya benar-benar lebih panjang: 07:00→17:00 = 10 jam. Keduanya setuju 60 menit.
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)
        val a = computeDayWorkBreakdown(
            millis(date, 7 * 60), millis(date, 17 * 60), window, breakMinutes,
        )
        val b = computeAttendanceTimelineZones(
            window, millis(date, 7 * 60), millis(date, 17 * 60),
        )
        assertEquals(60, a.overtimeMinutes)
        assertEquals(60, b.earlyOvertimeMinutes + b.lateOvertimeMinutes)
    }

    @Test
    fun engineBstillUsefulForDrawing() {
        // Mesin B tidak dihapus: ia yang tahu POSISI jam terhadap jendela, dan itu yang
        // dibutuhkan bar timeline. Test ini menjaga fungsinya tetap ada dan tetap benar.
        val date = LocalDate(2024, 1, 8)
        val (window, _) = windowFor(date)
        val zones = computeAttendanceTimelineZones(
            window, millis(date, 7 * 60), millis(date, 17 * 60),
        )
        assertEquals(30, zones.earlyOvertimeMinutes)
        assertEquals(30, zones.lateOvertimeMinutes)
    }

    // MARK: - Aturan yang harus eksplisit

    @Test
    fun openDayHasNoOvertimeNumber() {
        // Hari yang masih berjalan tidak punya angka lembur yang sah. Menampilkan angka
        // sementara yang nanti berubah lebih buruk daripada tidak menampilkan apa pun.
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)
        val b = computeDayWorkBreakdown(millis(date, 7 * 60 + 30), null, window, breakMinutes)
        assertEquals(0, b.netMinutes)
        assertEquals(0, b.overtimeMinutes)
    }

    @Test
    fun clockOutBeforeClockInIsZeroNotNegative() {
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)
        val b = computeDayWorkBreakdown(
            millis(date, 17 * 60), millis(date, 7 * 60), window, breakMinutes,
        )
        assertEquals(0, b.netMinutes)
        assertEquals(0, b.overtimeMinutes)
    }

    @Test
    fun nonWorkdayCountsEverythingAsOvertime() {
        val date = LocalDate(2024, 1, 13) // Sabtu
        val window = AttendanceReferenceWindow(
            startMillis = millis(date, 7 * 60 + 30),
            endMillis = millis(date, 16 * 60 + 30),
            isExplicitPlan = false,
        )
        val b = computeDayWorkBreakdown(
            clockInMillis = millis(date, 8 * 60),
            clockOutMillis = millis(date, 12 * 60),
            window = window,
            breakMinutes = 60,
            isWorkday = false,
        )
        assertEquals(180, b.netMinutes)
        assertEquals(0, b.scheduledNetMinutes)
        assertEquals(180, b.overtimeMinutes, "hari libur: seluruh kerja bersih adalah lembur")
    }

    @Test
    fun normalPlusOvertimeAlwaysEqualsNet() {
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)
        for (endMin in listOf(12 * 60, 16 * 60 + 30, 17 * 60, 20 * 60)) {
            val b = computeDayWorkBreakdown(
                millis(date, 7 * 60 + 30), millis(date, endMin), window, breakMinutes,
            )
            assertEquals(
                b.netMinutes, b.normalMinutes + b.overtimeMinutes,
                "normal + lembur harus berjumlah net (endMin=$endMin)",
            )
        }
    }

    @Test
    fun roundingIsFloorToWholeMinutes() {
        // 90 detik lewat harus dibaca 1 menit, bukan 2 — SSOT memilih floor.
        val date = LocalDate(2024, 1, 8)
        val (window, breakMinutes) = windowFor(date)
        val clockIn = millis(date, 7 * 60 + 30)
        val clockOut = millis(date, 16 * 60 + 30) + 90_000L
        val b = computeDayWorkBreakdown(clockIn, clockOut, window, breakMinutes)
        assertEquals(1, b.overtimeMinutes)
    }

}
