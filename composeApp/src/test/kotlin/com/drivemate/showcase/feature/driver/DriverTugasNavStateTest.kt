package com.drivemate.showcase.feature.driver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriverTugasNavStateTest {

    @Test
    fun state_whenNothingDone_hasZeroProgressAndTwoRemaining() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = false,
            safetyDone = false
        )

        val state = status.toTugasNavState()

        assertEquals(0, state.doneCount)
        assertEquals(2, state.remainingCount)
        assertEquals(0f, state.progress)
        assertFalse(state.isComplete)
    }

    @Test
    fun state_whenOneDone_hasHalfProgressAndOneRemaining() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = true,
            safetyDone = false
        )

        val state = status.toTugasNavState()

        assertEquals(1, state.doneCount)
        assertEquals(1, state.remainingCount)
        assertEquals(0.5f, state.progress)
        assertFalse(state.isComplete)
    }

    @Test
    fun state_whenBothDone_isCompleteWithZeroRemaining() {
        val status = DriverDailyStatus(
            sopAccepted = true,
            tappedIn = true,
            checklistDone = true,
            safetyDone = true
        )

        val state = status.toTugasNavState()

        assertEquals(2, state.doneCount)
        assertEquals(0, state.remainingCount)
        assertEquals(1f, state.progress)
        assertTrue(state.isComplete)
    }
}
