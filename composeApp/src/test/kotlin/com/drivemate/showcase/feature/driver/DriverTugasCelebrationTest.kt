package com.drivemate.showcase.feature.driver

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriverTugasCelebrationTest {

    @Test
    fun shouldCelebrate_isFalse_whenNotComplete() {
        assertFalse(
            DriverTugasCelebration.shouldCelebrate(
                lastCelebratedEpochDay = null,
                todayEpochDay = 20_345,
                isComplete = false
            )
        )
    }

    @Test
    fun shouldCelebrate_isTrue_whenCompleteAndNeverCelebratedBefore() {
        assertTrue(
            DriverTugasCelebration.shouldCelebrate(
                lastCelebratedEpochDay = null,
                todayEpochDay = 20_345,
                isComplete = true
            )
        )
    }

    @Test
    fun shouldCelebrate_isFalse_whenAlreadyCelebratedToday() {
        assertFalse(
            DriverTugasCelebration.shouldCelebrate(
                lastCelebratedEpochDay = 20_345,
                todayEpochDay = 20_345,
                isComplete = true
            )
        )
    }

    @Test
    fun shouldCelebrate_isTrue_whenCompleteAndLastCelebratedWasAPreviousDay() {
        assertTrue(
            DriverTugasCelebration.shouldCelebrate(
                lastCelebratedEpochDay = 20_344,
                todayEpochDay = 20_345,
                isComplete = true
            )
        )
    }
}
