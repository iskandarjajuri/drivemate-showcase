package com.drivemate.showcase.domain.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Aturan ini sebelumnya `private` di `AdminHomeScreen.kt` Android dengan nol test, dan iOS
 * belum punya sapaan sama sekali. Test ini adalah satu-satunya cara menguji ambang batasnya
 * untuk jalur yang dipakai iOS, karena tidak ada toolchain Swift di mesin ini.
 */
class AdminGreetingTest {

    @Test
    fun `ambang batas sama persis dengan versi Android sebelumnya`() {
        assertEquals(GreetingPeriod.MORNING, resolveGreetingPeriod(5))
        assertEquals(GreetingPeriod.MORNING, resolveGreetingPeriod(10))
        assertEquals(GreetingPeriod.AFTERNOON, resolveGreetingPeriod(11))
        assertEquals(GreetingPeriod.AFTERNOON, resolveGreetingPeriod(14))
        assertEquals(GreetingPeriod.EVENING, resolveGreetingPeriod(15))
        assertEquals(GreetingPeriod.EVENING, resolveGreetingPeriod(18))
        assertEquals(GreetingPeriod.NIGHT, resolveGreetingPeriod(19))
        assertEquals(GreetingPeriod.NIGHT, resolveGreetingPeriod(23))
        assertEquals(GreetingPeriod.NIGHT, resolveGreetingPeriod(0))
        assertEquals(GreetingPeriod.NIGHT, resolveGreetingPeriod(4))
    }

    @Test
    fun `setiap jam 0-23 menghasilkan tepat satu bagian hari`() {
        // Menjaga tidak ada lubang di antara rentang: jam yang tidak tercakup akan diam-diam
        // jatuh ke NIGHT dan terlihat seperti keputusan, padahal kelalaian.
        val counts = (0..23).groupingBy { resolveGreetingPeriod(it) }.eachCount()
        assertEquals(6, counts[GreetingPeriod.MORNING])
        assertEquals(4, counts[GreetingPeriod.AFTERNOON])
        assertEquals(4, counts[GreetingPeriod.EVENING])
        assertEquals(10, counts[GreetingPeriod.NIGHT])
        assertEquals(24, counts.values.sum())
    }

    @Test
    fun `jam di luar jangkauan jatuh ke NIGHT sama seperti sebelumnya`() {
        // Tidak bisa terjadi dari API jam sistem, tapi perilakunya dipaku supaya pemindahan
        // ke commonMain tidak diam-diam mengubahnya.
        assertEquals(GreetingPeriod.NIGHT, resolveGreetingPeriod(-1))
        assertEquals(GreetingPeriod.NIGHT, resolveGreetingPeriod(24))
    }

    @Test
    fun `token raw bolak-balik utuh`() {
        // Token inilah yang menyeberang ke Swift; kalau tidak bolak-balik, iOS akan jatuh ke
        // sapaan default tanpa error apa pun.
        GreetingPeriod.entries.forEach { period ->
            assertEquals(period, GreetingPeriod.fromRaw(period.toRaw()))
        }
        assertNull(GreetingPeriod.fromRaw("siang"))
        assertNull(GreetingPeriod.fromRaw(null))
    }
}
