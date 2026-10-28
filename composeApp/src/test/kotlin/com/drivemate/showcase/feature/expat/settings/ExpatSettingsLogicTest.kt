package com.drivemate.showcase.feature.expat.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpatSettingsLogicTest {

    @Test
    fun `inisial dua kata = huruf pertama kata pertama dan terakhir`() {
        assertEquals("BS", expatInitials("Budi Santoso"))
        assertEquals("HT", expatInitials("hiroshi  tanaka"))
        assertEquals("AC", expatInitials("Ana Bela Cahya"))
    }

    @Test
    fun `inisial satu kata dan kosong`() {
        assertEquals("E", expatInitials("Expat1"))
        assertEquals("?", expatInitials(null))
        assertEquals("?", expatInitials("   "))
    }

    @Test
    fun `inisial nama Jepang memakai karakter penuh`() {
        assertEquals("田", expatInitials("田中"))
        assertEquals("田太", expatInitials("田中 太郎"))
    }

    @Test
    fun `inisial karakter di luar BMP tidak terpotong`() {
        assertEquals("𝐀", expatInitials("𝐀bc"))
    }

    @Test
    fun `jumlah driver tidak menampilkan nol palsu saat memuat atau gagal`() {
        assertNull(expatManagedDriverCount(loading = true, hasError = false, driverCount = 0))
        assertNull(expatManagedDriverCount(loading = false, hasError = true, driverCount = 5))
        assertEquals(5, expatManagedDriverCount(loading = false, hasError = false, driverCount = 5))
        assertEquals(5, expatManagedDriverCount(loading = true, hasError = false, driverCount = 5))
        assertEquals(0, expatManagedDriverCount(loading = false, hasError = false, driverCount = 0))
    }

    @Test
    fun `kode bahasa dinormalkan, in jadi id dan tak dikenal jadi ikuti sistem`() {
        assertEquals("id", normalizeExpatLanguageTag("in"))
        assertEquals("id", normalizeExpatLanguageTag("id"))
        assertEquals("en", normalizeExpatLanguageTag("EN"))
        assertEquals("ja", normalizeExpatLanguageTag("ja"))
        assertNull(normalizeExpatLanguageTag("fr"))
        assertNull(normalizeExpatLanguageTag(null))
    }
}
