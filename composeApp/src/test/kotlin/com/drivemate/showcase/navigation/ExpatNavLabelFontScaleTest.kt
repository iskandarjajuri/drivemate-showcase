package com.drivemate.showcase.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpatNavLabelFontScaleTest {
    @Test
    fun fontNormalDanKecil_tidakDiubah() {
        assertEquals(1.0f, expatNavLabelFontScale(1.0f))
        assertEquals(0.85f, expatNavLabelFontScale(0.85f))
        assertEquals(1.15f, expatNavLabelFontScale(1.15f))
    }

    @Test
    fun fontBesar_dibatasiDiBatasAtas() {
        assertEquals(EXPAT_NAV_LABEL_MAX_FONT_SCALE, expatNavLabelFontScale(1.3f))
        assertEquals(EXPAT_NAV_LABEL_MAX_FONT_SCALE, expatNavLabelFontScale(2.0f))
    }

    @Test
    fun batasAtas_tidakMematikanPerbesaranSama_sekali() {
        // Pengguna font besar tetap mendapat label yang lebih besar dari default (1,0x), bukan label tetap.
        assert(EXPAT_NAV_LABEL_MAX_FONT_SCALE > 1.0f)
    }
}
