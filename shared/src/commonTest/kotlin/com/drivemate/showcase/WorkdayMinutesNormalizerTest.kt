package com.drivemate.showcase

import com.drivemate.showcase.core.util.normalizePersistedTotalMinutes
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkdayMinutesNormalizerTest {

    @Test
    fun zeroTotalMinutesIsPreserved() {
        val normalized = normalizePersistedTotalMinutes(
            totalMinutesRaw = 0L,
            overtimeMinutesRaw = 120L
        )

        assertEquals(0, normalized)
    }

    @Test
    fun nullTotalMinutesFallsBackToBasePlusOvertime() {
        val normalized = normalizePersistedTotalMinutes(
            totalMinutesRaw = null,
            overtimeMinutesRaw = 30L
        )

        assertEquals(510, normalized)
    }

    @Test
    fun negativeTotalMinutesFallsBackSafely() {
        val normalized = normalizePersistedTotalMinutes(
            totalMinutesRaw = -20L,
            overtimeMinutesRaw = -15L
        )

        assertEquals(480, normalized)
    }
}
