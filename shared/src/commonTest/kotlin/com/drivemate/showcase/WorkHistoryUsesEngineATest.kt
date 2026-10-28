package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.domain.calendar.computeWorkHistoryDayMinutes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Blok 0.2 bagian (b) — Riwayat Kerja driver berhenti mengambil angka lembur dari zona.
 *
 * Sebelumnya `computeWorkHistoryDayMinutes` menjumlahkan
 * `zones.earlyOvertimeMinutes + zones.lateOvertimeMinutes` (Mesin B). Akibatnya driver yang
 * hanya **menggeser** shiftnya — masuk 07:00, keluar 16:00, tetap 9 jam — melihat 30 menit
 * lembur di Riwayat Kerja, sementara catatan resmi di `workdays.overtimeMinutes` mencatat 0.
 *
 * Sekarang angkanya diambil dari catatan resmi itu, yang ditulis Cloud Function memakai
 * Mesin A (`functions/src/index.ts:332-333`). Satu angka, ditulis sekali, dibaca di mana-mana.
 */
class WorkHistoryUsesEngineATest {

    private val day = 20_000
    private val dayStart = day * 24L * 60L * 60L * 1000L

    private fun at(minuteOfDay: Int) = dayStart + minuteOfDay * 60_000L

    /** Jendela 07:30–16:30. */
    private val window = AttendanceReferenceWindow(
        startMillis = at(7 * 60 + 30),
        endMillis = at(16 * 60 + 30),
        isExplicitPlan = false,
    )

    private fun workDay(
        clockInMin: Int,
        clockOutMin: Int,
        totalMinutes: Int,
        overtimeMinutes: Int,
    ) = WorkDay(
        id = "d",
        driverUid = "u",
        dateEpochDay = day,
        clockInMillis = at(clockInMin),
        clockOutMillis = at(clockOutMin),
        totalMinutes = totalMinutes,
        overtimeMinutes = overtimeMinutes,
    )

    @Test
    fun shiftedShiftShowsZeroOvertimeNotThirty() {
        // Masuk 07:00, keluar 16:00 — tetap 9 jam, hanya digeser 30 menit lebih awal.
        // Catatan resmi: net 480, lembur 0. Mesin B dulu bilang 30.
        val m = computeWorkHistoryDayMinutes(
            workDay(clockInMin = 7 * 60, clockOutMin = 16 * 60, totalMinutes = 480, overtimeMinutes = 0),
            window,
            breakMinutes = 60,
        )
        assertEquals(0, m.overtimeMinutes, "shift yang digeser bukan lembur")
        assertEquals(480, m.normalMinutes)
    }

    @Test
    fun lengthenedShiftKeepsItsOvertime() {
        // Masuk 07:00, keluar 17:00 — benar-benar 10 jam. Lembur 60 menit, tetap tampil.
        val m = computeWorkHistoryDayMinutes(
            workDay(clockInMin = 7 * 60, clockOutMin = 17 * 60, totalMinutes = 540, overtimeMinutes = 60),
            window,
            breakMinutes = 60,
        )
        assertEquals(60, m.overtimeMinutes)
        assertEquals(480, m.normalMinutes)
    }

    @Test
    fun readsTheOfficialRecordNotARecomputation() {
        // Kalau catatan resmi bilang 45 menit, itulah yang tampil — bukan hasil hitung ulang.
        // Inilah yang menjamin driver dan admin melihat angka yang sama.
        val m = computeWorkHistoryDayMinutes(
            workDay(clockInMin = 7 * 60 + 30, clockOutMin = 17 * 60 + 15, totalMinutes = 525, overtimeMinutes = 45),
            window,
            breakMinutes = 60,
        )
        assertEquals(45, m.overtimeMinutes)
        assertEquals(480, m.normalMinutes)
    }

    @Test
    fun normalPlusOvertimeEqualsTotal() {
        for ((total, ot) in listOf(480 to 0, 540 to 60, 525 to 45, 300 to 0)) {
            val m = computeWorkHistoryDayMinutes(
                workDay(7 * 60 + 30, 17 * 60, total, ot), window, breakMinutes = 60,
            )
            assertEquals(total, m.normalMinutes + m.overtimeMinutes, "total=$total ot=$ot")
        }
    }

    @Test
    fun unprocessedDocumentFallsBackToEngineA_notZones() {
        // Dokumen yang belum pernah diproses Cloud Function menyimpan overtimeMinutes = 0
        // padahal harinya jelas melampaui jadwal. Fallback-nya Mesin A, bukan zona.
        // 07:30->18:00 = 630 bruto, −60 istirahat = 570 bersih, terjadwal 480 -> lembur 90.
        val m = computeWorkHistoryDayMinutes(
            workDay(clockInMin = 7 * 60 + 30, clockOutMin = 18 * 60, totalMinutes = 570, overtimeMinutes = 0),
            window,
            breakMinutes = 60,
        )
        assertEquals(90, m.overtimeMinutes, "fallback Mesin A untuk dokumen belum diproses")
        assertEquals(480, m.normalMinutes)
    }

    @Test
    fun unprocessedShiftedShiftStaysZero() {
        // Penting: fallback TIDAK boleh menghidupkan kembali perilaku Mesin B. Shift yang
        // digeser tetap 0 walau dokumennya belum diproses.
        val m = computeWorkHistoryDayMinutes(
            workDay(clockInMin = 7 * 60, clockOutMin = 16 * 60, totalMinutes = 480, overtimeMinutes = 0),
            window,
            breakMinutes = 60,
        )
        assertEquals(0, m.overtimeMinutes)
    }

    @Test
    fun openDayHasNoMinutes() {
        val open = WorkDay(
            id = "d", driverUid = "u", dateEpochDay = day,
            clockInMillis = at(7 * 60 + 30), clockOutMillis = 0L,
            totalMinutes = 0, overtimeMinutes = 0,
        )
        val m = computeWorkHistoryDayMinutes(open, window, breakMinutes = 60)
        assertEquals(0, m.normalMinutes)
        assertEquals(0, m.overtimeMinutes)
    }

    @Test
    fun impossibleStoredOvertimeIsClamped() {
        // Lembur tidak boleh melebihi total. Nilai rusak di dokumen tidak boleh membuat
        // normalMinutes negatif.
        val m = computeWorkHistoryDayMinutes(
            workDay(7 * 60 + 30, 16 * 60 + 30, totalMinutes = 480, overtimeMinutes = 9999),
            window, breakMinutes = 60,
        )
        assertEquals(480, m.overtimeMinutes)
        assertEquals(0, m.normalMinutes)
    }
}
