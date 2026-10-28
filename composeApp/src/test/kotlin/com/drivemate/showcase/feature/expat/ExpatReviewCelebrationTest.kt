package com.drivemate.showcase.feature.expat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Perayaan "semua ditinjau" hanya sekali per hari dan hanya saat benar-benar selesai. */
class ExpatReviewCelebrationTest {

    @Test
    fun `belum pernah dirayakan dan selesai = rayakan`() {
        assertTrue(ExpatReviewCelebration.shouldCelebrate(null, todayEpochDay = 20_000, isComplete = true))
    }

    @Test
    fun `sudah dirayakan hari ini = jangan ulangi`() {
        assertFalse(ExpatReviewCelebration.shouldCelebrate(20_000, todayEpochDay = 20_000, isComplete = true))
    }

    @Test
    fun `dirayakan kemarin = rayakan lagi hari ini`() {
        assertTrue(ExpatReviewCelebration.shouldCelebrate(19_999, todayEpochDay = 20_000, isComplete = true))
    }

    @Test
    fun `belum selesai = tidak pernah dirayakan`() {
        assertFalse(ExpatReviewCelebration.shouldCelebrate(null, todayEpochDay = 20_000, isComplete = false))
    }
}
