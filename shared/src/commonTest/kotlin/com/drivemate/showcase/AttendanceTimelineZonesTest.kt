package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.computeAttendanceTimelineZones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceTimelineZonesTest {

    private val referenceWindow = AttendanceReferenceWindow(
        startMillis = 1_000_000L,
        endMillis = 2_000_000L,
        isExplicitPlan = true
    )

    @Test
    fun no_overtime_when_clock_in_within_window_and_now_before_end() {
        val zones = computeAttendanceTimelineZones(
            referenceWindow = referenceWindow,
            clockInMillis = 1_100_000L,
            nowOrClockOutMillis = 1_800_000L
        )

        assertFalse(zones.hasEarlyOvertime)
        assertFalse(zones.hasLateOvertime)
        assertEquals(0, zones.earlyOvertimeMinutes)
        assertEquals(0, zones.lateOvertimeMinutes)
    }

    @Test
    fun early_overtime_only_when_clock_in_before_window_start() {
        val clockIn = referenceWindow.startMillis - (15 * 60_000L)

        val zones = computeAttendanceTimelineZones(
            referenceWindow = referenceWindow,
            clockInMillis = clockIn,
            nowOrClockOutMillis = 1_800_000L
        )

        assertTrue(zones.hasEarlyOvertime)
        assertFalse(zones.hasLateOvertime)
        assertEquals(15, zones.earlyOvertimeMinutes)
        assertEquals(0, zones.lateOvertimeMinutes)
    }

    @Test
    fun late_overtime_only_when_now_after_window_end() {
        val now = referenceWindow.endMillis + (40 * 60_000L)

        val zones = computeAttendanceTimelineZones(
            referenceWindow = referenceWindow,
            clockInMillis = 1_100_000L,
            nowOrClockOutMillis = now
        )

        assertFalse(zones.hasEarlyOvertime)
        assertTrue(zones.hasLateOvertime)
        assertEquals(0, zones.earlyOvertimeMinutes)
        assertEquals(40, zones.lateOvertimeMinutes)
    }

    @Test
    fun both_early_and_late_overtime_when_clock_in_before_start_and_now_after_end() {
        val clockIn = referenceWindow.startMillis - (10 * 60_000L)
        val now = referenceWindow.endMillis + (25 * 60_000L)

        val zones = computeAttendanceTimelineZones(
            referenceWindow = referenceWindow,
            clockInMillis = clockIn,
            nowOrClockOutMillis = now
        )

        assertTrue(zones.hasEarlyOvertime)
        assertTrue(zones.hasLateOvertime)
        assertEquals(10, zones.earlyOvertimeMinutes)
        assertEquals(25, zones.lateOvertimeMinutes)
    }

    @Test
    fun no_early_overtime_when_clock_in_is_null() {
        val zones = computeAttendanceTimelineZones(
            referenceWindow = referenceWindow,
            clockInMillis = null,
            nowOrClockOutMillis = 1_800_000L
        )

        assertFalse(zones.hasEarlyOvertime)
    }
}
