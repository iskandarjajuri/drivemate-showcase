package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.domain.calendar.OVERTIME_MINIMUM_MINUTES
import com.drivemate.showcase.domain.calendar.OVERTIME_ROUNDING_MINUTES
import com.drivemate.showcase.domain.calendar.computeWorkHistoryDayMinutes
import com.drivemate.showcase.domain.calendar.payableOvertimeMinutes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lembur terbayar dibulatkan ke bawah ke kelipatan **15 menit**, setelah ambang 30 menit
 * (keputusan Iskandar, 7 September 2026).
 *
 * ## Tebing di 30/31 menit — disengaja, bukan bug
 * Ambang PP 35/2021 berbunyi "melampaui 30 menit", jadi 30 menit tepat tidak terbayar
 * sementara 31 menit terbayar 30. Satu menit menggeser 30 menit upah. Itu konsekuensi
 * regulasinya, bukan pilihan implementasi — dan dipaku test supaya tidak ada yang
 * "memperbaikinya" tanpa keputusan sadar. Kalau tebing itu tidak diinginkan,
 * `minimumMinutes = 0` membuat pembulatan 15 menit bekerja sendiri (29 -> 15, 14 -> 0).
 *
 * ## Arah pembulatan: ke BAWAH
 * Mengikuti pilihan `docs/work_calendar/SSOT.md` untuk konversi millis->menit, dan tidak
 * membayar waktu yang tidak dikerjakan. Naik-ke-atas akan menambah upah untuk menit yang
 * tidak ada, dan itu keputusan yang tidak bisa saya ambil sendiri.
 */
class OvertimeFifteenMinuteRoundingTest {

    @Test
    fun roundingUnitIsFifteenMinutes() {
        assertEquals(15, OVERTIME_ROUNDING_MINUTES)
        assertEquals(30, OVERTIME_MINIMUM_MINUTES)
    }

    @Test
    fun roundsDownToFifteenMinuteMultiples() {
        assertEquals(30, payableOvertimeMinutes(31))
        assertEquals(30, payableOvertimeMinutes(44))
        assertEquals(45, payableOvertimeMinutes(45))
        assertEquals(45, payableOvertimeMinutes(59))
        assertEquals(60, payableOvertimeMinutes(60))
        assertEquals(60, payableOvertimeMinutes(74))
        assertEquals(75, payableOvertimeMinutes(75))
        assertEquals(240, payableOvertimeMinutes(249))
    }

    @Test
    fun everyPayableValueIsAMultipleOfFifteen() {
        // Tidak boleh ada nilai terbayar yang bukan kelipatan 15 — itu yang membuat
        // slip gaji bisa diperiksa ulang dengan kalkulator biasa.
        for (raw in 0..600) {
            val p = payableOvertimeMinutes(raw)
            assertEquals(0, p % OVERTIME_ROUNDING_MINUTES, "raw=$raw menghasilkan $p")
        }
    }

    @Test
    fun payableNeverExceedsRaw() {
        // Pembulatan ke bawah, jadi tidak pernah membayar lebih dari yang dikerjakan.
        for (raw in 0..600) {
            val p = payableOvertimeMinutes(raw)
            assertEquals(true, p <= raw, "raw=$raw dibayar $p — lebih besar dari yang dikerjakan")
        }
    }

    @Test
    fun theThirtyMinuteCliffIsExplicit() {
        assertEquals(0, payableOvertimeMinutes(29))
        assertEquals(0, payableOvertimeMinutes(30), "30 tidak MELAMPAUI 30")
        assertEquals(30, payableOvertimeMinutes(31), "tebing: 1 menit -> 30 menit upah")
    }

    @Test
    fun disablingThresholdLetsRoundingWorkAlone() {
        // Jalan keluar dari tebing, kalau nanti dipilih: ambang 0, pembulatan 15 tetap.
        assertEquals(15, payableOvertimeMinutes(29, minimumMinutes = 0))
        assertEquals(30, payableOvertimeMinutes(30, minimumMinutes = 0))
        assertEquals(0, payableOvertimeMinutes(14, minimumMinutes = 0))
    }

    @Test
    fun roundingCanBeDisabledForPerMinutePolicy() {
        assertEquals(31, payableOvertimeMinutes(31, roundingMinutes = 0))
        assertEquals(31, payableOvertimeMinutes(31, roundingMinutes = 1))
    }

    // MARK: - Terpasang di Riwayat Kerja

    private val day = 20_000
    private fun at(min: Int) = (day * 24L * 60L + min - 7 * 60) * 60_000L
    private val window = AttendanceReferenceWindow(
        startMillis = at(7 * 60 + 30), endMillis = at(16 * 60 + 30), isExplicitPlan = false,
    )

    private fun wd(total: Int, overtime: Int) = WorkDay(
        id = "d", driverUid = "u", dateEpochDay = day,
        clockInMillis = at(7 * 60 + 30), clockOutMillis = at(17 * 60),
        totalMinutes = total, overtimeMinutes = overtime,
    )

    @Test
    fun workHistoryShowsPayableAndKeepsRawForAudit() {
        // Catatan resmi 44 menit -> terbayar 30. Angka mentah tetap ada untuk audit.
        val m = computeWorkHistoryDayMinutes(wd(total = 524, overtime = 44), window, breakMinutes = 60)
        assertEquals(30, m.overtimeMinutes, "yang tampil adalah yang terbayar")
        assertEquals(44, m.rawOvertimeMinutes, "mentah tidak dibuang")
        assertEquals(494, m.normalMinutes, "524 - 30")
    }

    @Test
    fun normalPlusOvertimeStillEqualsTotal() {
        // Partisi harus tetap utuh: menit yang terpangkas pembulatan jatuh ke normal,
        // tidak hilang. Bar normal-vs-lembur di Agenda bergantung pada ini.
        for ((total, ot) in listOf(524 to 44, 510 to 30, 540 to 60, 480 to 0, 555 to 75)) {
            val m = computeWorkHistoryDayMinutes(wd(total, ot), window, breakMinutes = 60)
            assertEquals(total, m.normalMinutes + m.overtimeMinutes, "total=$total raw=$ot")
        }
    }
}
