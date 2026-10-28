package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.domain.calendar.OVERTIME_MINIMUM_MINUTES
import com.drivemate.showcase.domain.calendar.computeDayWorkBreakdown
import com.drivemate.showcase.domain.calendar.payableOvertimeMinutes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Blok 0.3 — ambang 30 menit dan aturan pembulatan, dimodelkan EKSPLISIT.
 *
 * Ambang ini **belum diterapkan** ke angka mana pun yang tampil; ia dimodelkan dan diuji
 * supaya keputusan menerapkannya bisa diambil dengan angka di depan mata, bukan ditebak.
 * Menerapkannya MENGURANGI lembur terbayar untuk hari di bawah 30 menit, dan itu keputusan
 * kebijakan pengupahan, bukan keputusan teknis.
 *
 * Angka MENTAH tidak pernah dibuang: `overtimeMinutes` tetap apa adanya, `payableOvertimeMinutes`
 * yang menerapkan ambang. Sengketa upah harus bisa direkonstruksi.
 */
class OvertimeThresholdAndRoundingTest {

    private val day = 20_000L
    private fun at(min: Int) = (day * 24 * 60 + min - 7 * 60) * 60_000L
    private val window = AttendanceReferenceWindow(
        startMillis = at(7 * 60 + 30), endMillis = at(16 * 60 + 30), isExplicitPlan = false,
    )

    @Test
    fun thresholdIsThirtyMinutes() {
        assertEquals(30, OVERTIME_MINIMUM_MINUTES)
    }

    @Test
    fun exceedsThirtyIsPayableThenRoundedDownToFifteen() {
        // "Melampaui 30 menit" -> terbayar. Ambang menggerbang, tidak memotong: 31 menit
        // TIDAK menjadi 1. Tapi sejak keputusan 7 Sep 2026 hasilnya dibulatkan ke bawah ke
        // kelipatan 15, jadi 31 -> 30. Detailnya di OvertimeFifteenMinuteRoundingTest.
        assertEquals(30, payableOvertimeMinutes(31))
        assertEquals(60, payableOvertimeMinutes(60))
        assertEquals(240, payableOvertimeMinutes(240))
    }

    @Test
    fun exactlyThirtyIsNotPayable() {
        // "Melampaui" berarti lebih dari, bukan sekurang-kurangnya. 30 tepat tidak melampaui 30.
        assertEquals(0, payableOvertimeMinutes(30))
    }

    @Test
    fun belowThirtyIsNotPayable() {
        assertEquals(0, payableOvertimeMinutes(1))
        assertEquals(0, payableOvertimeMinutes(29))
        assertEquals(0, payableOvertimeMinutes(0))
    }

    @Test
    fun negativeIsZeroNotNegative() {
        assertEquals(0, payableOvertimeMinutes(-5))
    }

    @Test
    fun thresholdIsConfigurableForStricterCompanyPolicy() {
        // Perusahaan boleh lebih ketat dari regulasi, tidak pernah lebih longgar
        // (aturan yang sama dipakai Blok 3.1). Ambang 0 = bayar semuanya.
        // Dengan pembulatan 15 menit aktif, 10 menit membulat ke bawah menjadi 0 walau
        // ambangnya dimatikan — pembulatan dan ambang adalah dua gerbang terpisah.
        assertEquals(0, payableOvertimeMinutes(10, minimumMinutes = 0))
        assertEquals(10, payableOvertimeMinutes(10, minimumMinutes = 0, roundingMinutes = 0))
        assertEquals(0, payableOvertimeMinutes(10, minimumMinutes = 60))
    }

    @Test
    fun rawOvertimeIsNeverDiscarded() {
        // Angka mentah tetap ada berdampingan dengan yang terbayar. Sengketa upah harus
        // bisa direkonstruksi, dan itu mustahil kalau yang di bawah ambang dihapus.
        val b = computeDayWorkBreakdown(
            clockInMillis = at(7 * 60 + 30), clockOutMillis = at(16 * 60 + 50),
            window = window, breakMinutes = 60,
        )
        assertEquals(20, b.overtimeMinutes, "mentah tetap 20")
        assertEquals(0, b.payableOvertimeMinutes, "20 tidak melampaui 30 -> nol terbayar")
    }

    @Test
    fun payableEqualsRawWhenAboveThreshold() {
        val b = computeDayWorkBreakdown(
            clockInMillis = at(7 * 60 + 30), clockOutMillis = at(17 * 60 + 30),
            window = window, breakMinutes = 60,
        )
        assertEquals(60, b.overtimeMinutes)
        assertEquals(60, b.payableOvertimeMinutes)
    }

    // MARK: - Pembulatan: floor, satu-satunya aturan

    @Test
    fun roundingIsFloorEverywhere() {
        // SSOT.md:60-62 memilih floor. Detik sisa DIBUANG, tidak pernah dibulatkan naik.
        for (extraSeconds in listOf(1, 30, 59)) {
            val b = computeDayWorkBreakdown(
                clockInMillis = at(7 * 60 + 30),
                clockOutMillis = at(17 * 60 + 30) + extraSeconds * 1000L,
                window = window, breakMinutes = 60,
            )
            assertEquals(60, b.overtimeMinutes, "+${extraSeconds}s tidak boleh menaikkan menit")
        }
    }

    @Test
    fun fullMinuteAdvancesTheCount() {
        val b = computeDayWorkBreakdown(
            clockInMillis = at(7 * 60 + 30),
            clockOutMillis = at(17 * 60 + 30) + 60_000L,
            window = window, breakMinutes = 60,
        )
        assertEquals(61, b.overtimeMinutes)
    }
}
