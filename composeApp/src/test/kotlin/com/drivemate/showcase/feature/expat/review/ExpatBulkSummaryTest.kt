package com.drivemate.showcase.feature.expat.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpatBulkSummaryTest {
    @Test
    fun seleksiKosong_nolDanTanpaRentang() {
        val s = summarizeBulkSelection(emptyList())
        assertEquals(0, s.workdayCount)
        assertEquals(0, s.driverCount)
        assertNull(s.firstEpochDay)
        assertNull(s.lastEpochDay)
    }

    @Test
    fun hitungHariKerja_driverUnik_danRentang() {
        val s = summarizeBulkSelection(
            listOf("u1" to 20000, "u1" to 20003, "u2" to 20001, "u3" to 19998, "u2" to 20002)
        )
        assertEquals(5, s.workdayCount)
        assertEquals(3, s.driverCount)
        assertEquals(19998, s.firstEpochDay)
        assertEquals(20003, s.lastEpochDay)
    }

    @Test
    fun satuHariSatuDriver_rentangSama() {
        val s = summarizeBulkSelection(listOf("u1" to 20010))
        assertEquals(1, s.workdayCount)
        assertEquals(1, s.driverCount)
        assertEquals(s.firstEpochDay, s.lastEpochDay)
    }
}
