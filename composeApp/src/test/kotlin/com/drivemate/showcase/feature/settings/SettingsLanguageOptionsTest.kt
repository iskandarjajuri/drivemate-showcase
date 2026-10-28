package com.drivemate.showcase.feature.settings

import com.drivemate.showcase.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsLanguageOptionsTest {

    @Test
    fun `flagResFor mengenali semua tag bahasa yang didukung`() {
        assertEquals(R.drawable.ic_flag_id, SettingsLanguageOptions.flagResFor("id"))
        assertEquals(R.drawable.ic_flag_en, SettingsLanguageOptions.flagResFor("en"))
        assertEquals(R.drawable.ic_flag_ja, SettingsLanguageOptions.flagResFor("ja"))
    }

    @Test
    fun `flagResFor memetakan tag legacy in ke bendera Indonesia`() {
        assertEquals(R.drawable.ic_flag_id, SettingsLanguageOptions.flagResFor("in"))
    }

    @Test
    fun `flagResFor mengembalikan null untuk sistem dan tag tak dikenal`() {
        assertNull(SettingsLanguageOptions.flagResFor(null))
        assertNull(SettingsLanguageOptions.flagResFor("ko"))
        assertNull(SettingsLanguageOptions.flagResFor(""))
    }

    @Test
    fun `normalizeTag mempertahankan tag valid dan membuang tag tak dikenal`() {
        assertEquals("id", SettingsLanguageOptions.normalizeTag("id"))
        assertEquals("en", SettingsLanguageOptions.normalizeTag("en"))
        assertEquals("ja", SettingsLanguageOptions.normalizeTag("ja"))
        assertNull(SettingsLanguageOptions.normalizeTag(null))
        assertNull(SettingsLanguageOptions.normalizeTag("fr"))
    }
}
