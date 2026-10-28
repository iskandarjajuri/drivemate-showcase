package com.drivemate.showcase.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class StatusContrastTest {

    private fun lum(c: Color): Double {
        fun ch(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val hi = maxOf(lum(a), lum(b))
        val lo = minOf(lum(a), lum(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAa(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 4.5, "$name kontras $r < 4.5")
    }

    private fun checkMode(dark: Boolean) {
        AppPalette.apply(dark)
        val mode = if (dark) "gelap" else "terang"
        assertAa("sukses $mode", AppPalette.onSuccessContainer, AppPalette.successContainerSolid)
        assertAa("peringatan $mode", AppPalette.onWarningContainer, AppPalette.warningContainerSolid)
        assertAa("error $mode", AppPalette.onErrorContainer, AppPalette.errorContainerSolid)
        assertAa("info $mode", AppPalette.onInfoContainer, AppPalette.infoContainerSolid)
        assertAa("tombol hero", AppPalette.heroButtonText, AppPalette.heroButtonContainer)
        assertAa("tombol primer atas $mode", AppPalette.onPrimary, AppPalette.primary)
        assertAa("tombol primer bawah $mode", AppPalette.onPrimary, AppPalette.primaryHover)
    }

    @Test
    fun statusPairs_meetAa_inLightMode() = checkMode(dark = false)

    @Test
    fun statusPairs_meetAa_inDarkMode() {
        try {
            checkMode(dark = true)
        } finally {
            AppPalette.apply(false)
        }
    }

    @Test
    fun heroButton_isDistinctFromHeroBackground() {
        AppPalette.apply(false)
        val heroStart = Color(0xFF2563EB)
        assertTrue(ratio(AppPalette.heroButtonContainer, heroStart) >= 3.0)
    }
}
