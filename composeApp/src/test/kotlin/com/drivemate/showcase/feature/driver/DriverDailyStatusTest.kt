package com.drivemate.showcase.feature.driver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriverDailyStatusTest {

    @Test
    fun nextTask_isSop_whenNothingDoneYet() {
        val status = DriverDailyStatus(
            sopAccepted = false,
            tappedIn = false,
            checklistDone = false,
            safetyDone = false
        )
        assertEquals(DriverTask.SOP, status.nextTask())
    }

    @Test
    fun nextTask_isTapIn_whenOnlySopDone() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = false,
            checklistDone = false,
            safetyDone = false
        )
        assertEquals(DriverTask.TAP_IN, status.nextTask())
    }

    @Test
    fun nextTask_isVehicleChecklist_whenSopAndTapInDone() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = false,
            safetyDone = false
        )
        assertEquals(DriverTask.VEHICLE_CHECKLIST, status.nextTask())
    }

    @Test
    fun nextTask_isSafetyDeclaration_whenOnlySafetyRemains() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = true,
            safetyDone = false
        )
        assertEquals(DriverTask.SAFETY_DECLARATION, status.nextTask())
    }

    @Test
    fun nextTask_isNull_whenEverythingDone() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = true,
            safetyDone = true
        )
        assertNull(status.nextTask())
    }

    @Test
    fun nextTask_ignoresTapIn_whenSopNotYetAccepted() {
        // sopAccepted=false wins even if the driver somehow already tapped in --
        // SOP is first in the fixed order regardless of what else is done.
        val status = DriverDailyStatus(
            sopAccepted = false,
            tappedIn = true,
            checklistDone = true,
            safetyDone = true
        )
        assertEquals(DriverTask.SOP, status.nextTask())
    }

    @Test
    fun doneCount_countsOnlyChecklistAndSafety_ignoringSopAndTapIn() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = true,
            safetyDone = false
        )
        assertEquals(1, status.doneCount)
    }

    @Test
    fun doneCount_isTwo_whenBothChecklistAndSafetyDone() {
        val status = DriverDailyStatus(
            sopAccepted = false,
            tappedIn = false,
            checklistDone = true,
            safetyDone = true
        )
        assertEquals(2, status.doneCount)
    }
}
