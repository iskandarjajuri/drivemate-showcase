package com.drivemate.showcase.feature.expat.status

import androidx.compose.ui.graphics.Color
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.ui.AppPalette
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExpatApprovalPillTest {

    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    /** Rasio kontras WCAG (luminansi relatif). */
    private fun contrast(a: Color, b: Color): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun setiapStatus_punyaIkonBerbeda_bukanHanyaWarna() {
        val icons = ApprovalStatus.entries.map { expatApprovalIcon(it) }
        assertEquals(ApprovalStatus.entries.size, icons.toSet().size, "ikon status harus unik: $icons")
    }

    @Test
    fun pemetaanIkon_sesuaiStatus() {
        assertEquals(ExpatApprovalIcon.PENDING, expatApprovalIcon(ApprovalStatus.PENDING))
        assertEquals(ExpatApprovalIcon.APPROVED, expatApprovalIcon(ApprovalStatus.APPROVED))
        assertEquals(ExpatApprovalIcon.REJECTED, expatApprovalIcon(ApprovalStatus.REJECTED))
    }

    private fun checkContrast(dark: Boolean) {
        AppPalette.apply(dark)
        val mode = if (dark) "gelap" else "terang"
        for (status in ApprovalStatus.entries) {
            val c = expatApprovalColors(status)
            val r = contrast(c.content, c.container)
            // teks >= 4,5:1; ikon memakai warna yang sama sehingga otomatis >= 3:1 (batas non-teks WCAG)
            assertTrue(r >= 4.5, "teks $status $mode kontras $r < 4,5")
            assertTrue(r >= 3.0, "ikon $status $mode kontras $r < 3,0")
        }
    }

    @Test
    fun kontras_temaTerang() = checkContrast(dark = false)

    @Test
    fun kontras_temaGelap() {
        try {
            checkContrast(dark = true)
        } finally {
            AppPalette.apply(false)
        }
    }

    @Test
    fun latarPil_berbedaPerStatus_ditambahIkon() {
        AppPalette.apply(false)
        val containers = ApprovalStatus.entries.map { expatApprovalColors(it).container }
        assertEquals(containers.size, containers.toSet().size)
        assertNotEquals(
            expatApprovalColors(ApprovalStatus.APPROVED).container,
            expatApprovalColors(ApprovalStatus.REJECTED).container
        )
    }
}
