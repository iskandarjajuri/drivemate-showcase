package com.drivemate.showcase.feature.expat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pemilihan nama sapaan Beranda Expat: nama tersimpan, lalu awalan email, lalu null (sapaan netral). */
class ExpatGreetingTest {

    @Test
    fun `nama tersimpan menang atas email`() {
        assertEquals("Budi Santoso", expatGreetingName("Budi Santoso", "expat1@example.com"))
    }

    @Test
    fun `nama huruf kecil dirapikan saat tampil`() {
        assertEquals("Budi Santoso", expatGreetingName("budi santoso", null))
    }

    @Test
    fun `nama dengan spasi di tepi dipangkas`() {
        assertEquals("Budi", expatGreetingName("  Budi  ", "x@example.com"))
    }

    @Test
    fun `nama kosong atau spasi jatuh ke awalan email`() {
        assertEquals("Expat1", expatGreetingName("", "expat1@example.com"))
        assertEquals("Expat1", expatGreetingName("   ", "expat1@example.com"))
    }

    @Test
    fun `nama null jatuh ke awalan email dengan title case`() {
        assertEquals("Expat1", expatGreetingName(null, "expat1@example.com"))
        assertEquals("Tanaka", expatGreetingName(null, "tanaka@example.com"))
    }

    @Test
    fun `nama placeholder User- tidak dipakai`() {
        assertEquals("Tanaka", expatGreetingName("User-ab12cd", "tanaka@example.com"))
    }

    @Test
    fun `tanpa nama dan tanpa email menghasilkan null agar layar memakai sapaan netral`() {
        assertNull(expatGreetingName(null, null))
        assertNull(expatGreetingName("  ", null))
        assertNull(expatGreetingName("", ""))
        assertNull(expatGreetingName(null, "   "))
        assertNull(expatGreetingName(null, "@example.com"))
    }
}
