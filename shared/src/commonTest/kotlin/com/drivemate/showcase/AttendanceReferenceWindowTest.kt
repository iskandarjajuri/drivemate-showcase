package com.drivemate.showcase

import com.drivemate.showcase.core.model.DriverTodaySchedule
import com.drivemate.showcase.core.model.resolveAttendanceReferenceWindow
import com.drivemate.showcase.core.util.TimeUtil
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceReferenceWindowTest {

    private val zone = TimeZone.UTC
    private val epochDay = 20_000

    @Test
    fun explicit_plan_passes_through_planned_millis_unchanged() {
        val plannedStart = TimeUtil.parseHm("06:00", epochDay.toLong(), zone)
        val plannedEnd = TimeUtil.parseHm("15:00", epochDay.toLong(), zone)
        val schedule = DriverTodaySchedule(
            plannedStartMillis = plannedStart,
            plannedEndMillis = plannedEnd,
            planSource = null
        )

        val window = resolveAttendanceReferenceWindow(schedule, epochDay, zone)

        assertEquals(plannedStart, window.startMillis)
        assertEquals(plannedEnd, window.endMillis)
        assertTrue(window.isExplicitPlan)
    }

    @Test
    fun no_plan_falls_back_to_shared_default_work_window() {
        val schedule = DriverTodaySchedule(
            plannedStartMillis = null,
            plannedEndMillis = null,
            planSource = null
        )

        val window = resolveAttendanceReferenceWindow(schedule, epochDay, zone)

        assertEquals(TimeUtil.parseHm("07:30", epochDay.toLong(), zone), window.startMillis)
        assertEquals(TimeUtil.parseHm("16:30", epochDay.toLong(), zone), window.endMillis)
        assertFalse(window.isExplicitPlan)
    }

    @Test
    fun partial_plan_is_not_considered_explicit() {
        val plannedStart = TimeUtil.parseHm("06:00", epochDay.toLong(), zone)
        val schedule = DriverTodaySchedule(
            plannedStartMillis = plannedStart,
            plannedEndMillis = null,
            planSource = null
        )

        val window = resolveAttendanceReferenceWindow(schedule, epochDay, zone)

        assertEquals(plannedStart, window.startMillis)
        assertEquals(TimeUtil.parseHm("16:30", epochDay.toLong(), zone), window.endMillis)
        assertFalse(window.isExplicitPlan)
    }
}
