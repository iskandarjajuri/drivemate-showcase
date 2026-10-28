package com.drivemate.showcase

import com.drivemate.showcase.domain.admin.OvertimeLimitLevel
import com.drivemate.showcase.domain.admin.OvertimeLimitScope
import com.drivemate.showcase.domain.admin.OvertimeLimits
import com.drivemate.showcase.domain.admin.REGULATION_DAILY_MAX_MINUTES
import com.drivemate.showcase.domain.admin.REGULATION_WEEKLY_MAX_MINUTES
import com.drivemate.showcase.domain.admin.evaluateOvertimeLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Blok 3.1 — peringatan batas lembur (B1 pada PRD §7). */
class OvertimeLimitsTest {

    private val limits = OvertimeLimits()

    @Test
    fun regulationDefaultsMatchPP35() {
        assertEquals(4 * 60, REGULATION_DAILY_MAX_MINUTES, "4 jam/hari")
        assertEquals(18 * 60, REGULATION_WEEKLY_MAX_MINUTES, "18 jam/pekan berjalan")
        assertEquals(REGULATION_DAILY_MAX_MINUTES, limits.dailyMaxMinutes)
        assertEquals(REGULATION_WEEKLY_MAX_MINUTES, limits.weeklyMaxMinutes)
        assertEquals(75, limits.warningRatioPercent)
    }

    @Test
    fun acceptanceCriteriaFromPrd() {
        // Kriteria terima PRD kata-per-kata: 3 jam 10 menit -> peringatan,
        // 4 jam 5 menit -> pelanggaran.
        val peringatan = evaluateOvertimeLimits(dailyMinutes = 3 * 60 + 10, weeklyMinutes = 0, limits = limits)
            .single { it.scope == OvertimeLimitScope.DAILY }
        assertEquals(OvertimeLimitLevel.WARNING, peringatan.level)
        assertEquals(190, peringatan.actualMinutes)
        assertEquals(240, peringatan.limitMinutes, "batasnya harus ikut disebut, bukan cuma warna")

        val pelanggaran = evaluateOvertimeLimits(dailyMinutes = 4 * 60 + 5, weeklyMinutes = 0, limits = limits)
            .single { it.scope == OvertimeLimitScope.DAILY }
        assertEquals(OvertimeLimitLevel.VIOLATION, pelanggaran.level)
        assertEquals(245, pelanggaran.actualMinutes)
        assertEquals(240, pelanggaran.limitMinutes)
    }

    @Test
    fun boundariesAreExplicit() {
        fun daily(min: Int) = evaluateOvertimeLimits(min, 0, limits)
            .single { it.scope == OvertimeLimitScope.DAILY }.level
        assertEquals(OvertimeLimitLevel.OK, daily(179), "di bawah 75%")
        assertEquals(OvertimeLimitLevel.WARNING, daily(180), "tepat 75% sudah peringatan")
        assertEquals(OvertimeLimitLevel.WARNING, daily(240), "tepat batas belum PELANGGARAN")
        assertEquals(OvertimeLimitLevel.VIOLATION, daily(241), "melampaui batas")
    }

    @Test
    fun weeklyEvaluatedIndependentlyOfDaily() {
        // 17 jam = 1020 menit, masih DI BAWAH batas 1080 -> peringatan, bukan pelanggaran.
        // (Assertion pertama saya menulis VIOLATION di sini; itu salah hitung, dan
        // implementasinya yang benar.)
        val out = evaluateOvertimeLimits(dailyMinutes = 0, weeklyMinutes = 17 * 60, limits = limits)
        val weekly = out.single { it.scope == OvertimeLimitScope.WEEKLY }
        assertEquals(OvertimeLimitLevel.WARNING, weekly.level)
        assertEquals(1020, weekly.actualMinutes)
        assertEquals(1080, weekly.limitMinutes)

        // Harian tetap OK walau pekanan sudah memperingatkan — keduanya independen.
        assertEquals(OvertimeLimitLevel.OK, out.single { it.scope == OvertimeLimitScope.DAILY }.level)
    }

    @Test
    fun weeklyBoundaries() {
        fun weekly(min: Int) = evaluateOvertimeLimits(0, min, limits)
            .single { it.scope == OvertimeLimitScope.WEEKLY }.level
        assertEquals(OvertimeLimitLevel.OK, weekly(809))
        assertEquals(OvertimeLimitLevel.WARNING, weekly(810), "75% dari 1080")
        assertEquals(OvertimeLimitLevel.WARNING, weekly(1080))
        assertEquals(OvertimeLimitLevel.VIOLATION, weekly(1081))
    }

    @Test
    fun companyMayBeStricterNeverLooser() {
        // Aturan produk: perusahaan boleh lebih ketat dari regulasi, TIDAK PERNAH lebih
        // longgar. Konfigurasi yang melonggarkan DI-CLAMP, bukan diterima — kalau tidak,
        // satu salah ketik di konfigurasi membuat aplikasi ikut melanggar regulasi.
        val lebihKetat = OvertimeLimits(dailyMaxMinutes = 120, weeklyMaxMinutes = 600)
        assertEquals(120, lebihKetat.dailyMaxMinutes)
        assertEquals(600, lebihKetat.weeklyMaxMinutes)

        val lebihLonggar = OvertimeLimits(dailyMaxMinutes = 600, weeklyMaxMinutes = 3000)
        assertEquals(REGULATION_DAILY_MAX_MINUTES, lebihLonggar.dailyMaxMinutes, "di-clamp ke regulasi")
        assertEquals(REGULATION_WEEKLY_MAX_MINUTES, lebihLonggar.weeklyMaxMinutes)
    }

    @Test
    fun nonPositiveConfigFallsBackToRegulation() {
        // Nol atau negatif bukan "tanpa lembur", itu konfigurasi rusak. Menerimanya akan
        // menandai SETIAP menit lembur sebagai pelanggaran.
        val rusak = OvertimeLimits(dailyMaxMinutes = 0, weeklyMaxMinutes = -1)
        assertEquals(REGULATION_DAILY_MAX_MINUTES, rusak.dailyMaxMinutes)
        assertEquals(REGULATION_WEEKLY_MAX_MINUTES, rusak.weeklyMaxMinutes)
    }

    @Test
    fun warningRatioIsClampedToSaneRange() {
        assertEquals(75, OvertimeLimits(warningRatioPercent = 0).warningRatioPercent)
        assertEquals(75, OvertimeLimits(warningRatioPercent = 150).warningRatioPercent)
        assertEquals(50, OvertimeLimits(warningRatioPercent = 50).warningRatioPercent)
    }

    @Test
    fun bothScopesAlwaysReturned() {
        // Selalu dua entri: admin perlu tahu keduanya, dan menyembunyikan yang OK membuat
        // "tidak ada masalah harian" tak bisa dibedakan dari "belum dihitung".
        val out = evaluateOvertimeLimits(0, 0, limits)
        assertEquals(2, out.size)
        assertEquals(setOf(OvertimeLimitScope.DAILY, OvertimeLimitScope.WEEKLY), out.map { it.scope }.toSet())
        assertTrue(out.all { it.level == OvertimeLimitLevel.OK })
    }

    @Test
    fun negativeActualIsTreatedAsZero() {
        val out = evaluateOvertimeLimits(-30, -30, limits)
        assertTrue(out.all { it.level == OvertimeLimitLevel.OK })
        assertTrue(out.all { it.actualMinutes == 0 })
    }
}
