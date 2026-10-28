package com.drivemate.showcase.feature.driver

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriverHomeCompletionBannerTest {

    @Test
    fun shouldShow_isTrue_whenNeverShownBefore() {
        assertTrue(DriverHomeCompletionBanner.shouldShow(lastShownEpochDay = null, todayEpochDay = 20_345))
    }

    @Test
    fun shouldShow_isFalse_whenAlreadyShownToday() {
        assertFalse(DriverHomeCompletionBanner.shouldShow(lastShownEpochDay = 20_345, todayEpochDay = 20_345))
    }

    @Test
    fun shouldShow_isTrue_whenLastShownWasAPreviousDay() {
        assertTrue(DriverHomeCompletionBanner.shouldShow(lastShownEpochDay = 20_344, todayEpochDay = 20_345))
    }
}
