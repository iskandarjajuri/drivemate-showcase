package com.drivemate.showcase.feature.driver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriverTugasItemsTest {
    private fun status(sop: Boolean, checklist: Boolean, safety: Boolean) =
        DriverDailyStatus(sopAccepted = sop, tappedIn = false, checklistDone = checklist, safetyDone = safety)

    @Test
    fun sopBelumDiterima_munculDanDihitungWajib() {
        val s = status(sop = false, checklist = false, safety = false)
        assertEquals(listOf(DriverTask.SOP, DriverTask.VEHICLE_CHECKLIST, DriverTask.SAFETY_DECLARATION), s.tugasItems().map { it.task })
        assertEquals(3, s.requiredRemaining())
    }

    @Test
    fun sopSudahDiterima_tidakMuncul() {
        val s = status(sop = true, checklist = true, safety = false)
        assertEquals(listOf(DriverTask.VEHICLE_CHECKLIST, DriverTask.SAFETY_DECLARATION), s.tugasItems().map { it.task })
        assertEquals(1, s.requiredRemaining())
    }

    @Test
    fun semuaSelesai_nolWajib() {
        val s = status(sop = true, checklist = true, safety = true)
        assertEquals(0, s.requiredRemaining())
        assertTrue(s.tugasItems().all { it.done })
    }
}
