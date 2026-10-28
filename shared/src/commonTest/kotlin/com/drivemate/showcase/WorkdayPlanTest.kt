package com.drivemate.showcase

import com.drivemate.showcase.core.model.DeviationFlag
import com.drivemate.showcase.core.model.computeDeviationFlags
import com.drivemate.showcase.core.model.shouldHoldReview
import com.drivemate.showcase.core.util.TimeUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class WorkdayPlanTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val epochDay = LocalDate(2024, 1, 8).toEpochDays() // Monday
    private val plannedStart = TimeUtil.parseHm("06:00", epochDay, zone)
    private val plannedEnd = TimeUtil.parseHm("18:30", epochDay, zone)

    @Test
    fun weekdayWithinGrace_noFlags() {
        val clockIn = plannedStart - (10 * 60 * 1000L)
        val clockOut = plannedEnd + (5 * 60 * 1000L)
        val flags = computeDeviationFlags(
            isWeekend = false,
            planStartMillis = plannedStart,
            planEndMillis = plannedEnd,
            clockInMillis = clockIn,
            clockOutMillis = clockOut
        )
        assertTrue(flags.isEmpty())
    }

    @Test
    fun weekdayEarlyUnplanned_flagged() {
        val clockIn = plannedStart - (40 * 60 * 1000L)
        val flags = computeDeviationFlags(
            isWeekend = false,
            planStartMillis = plannedStart,
            planEndMillis = plannedEnd,
            clockInMillis = clockIn,
            clockOutMillis = plannedEnd
        )
        assertTrue(flags.contains(DeviationFlag.EARLY_UNPLANNED))
    }

    @Test
    fun weekdayEarlyUnplanned_holdReview() {
        val clockIn = plannedStart - (45 * 60 * 1000L)
        val flags = computeDeviationFlags(
            isWeekend = false,
            planStartMillis = plannedStart,
            planEndMillis = plannedEnd,
            clockInMillis = clockIn,
            clockOutMillis = plannedEnd
        )
        assertTrue(flags.contains(DeviationFlag.EARLY_UNPLANNED))
        assertTrue(shouldHoldReview(flags))
    }

    @Test
    fun weekdayEarlyWithExplicitPlan_noFlagNoHold() {
        val clockIn = plannedStart - (45 * 60 * 1000L)
        val explicitPlanStart = plannedStart - (60 * 60 * 1000L)
        val flags = computeDeviationFlags(
            isWeekend = false,
            planStartMillis = explicitPlanStart,
            planEndMillis = plannedEnd,
            clockInMillis = clockIn,
            clockOutMillis = plannedEnd
        )
        assertTrue(flags.isEmpty())
        assertTrue(!shouldHoldReview(flags))
    }

    @Test
    fun weekdayLateUnplanned_flagged() {
        val clockOut = plannedEnd + (40 * 60 * 1000L)
        val flags = computeDeviationFlags(
            isWeekend = false,
            planStartMillis = plannedStart,
            planEndMillis = plannedEnd,
            clockInMillis = plannedStart,
            clockOutMillis = clockOut
        )
        assertTrue(flags.contains(DeviationFlag.LATE_UNPLANNED))
    }

    @Test
    fun weekendUnplanned_flagged() {
        val flags = computeDeviationFlags(
            isWeekend = true,
            planStartMillis = null,
            planEndMillis = null,
            clockInMillis = plannedStart,
            clockOutMillis = plannedEnd
        )
        assertEquals(listOf(DeviationFlag.WEEKEND_UNPLANNED), flags)
        assertTrue(!shouldHoldReview(flags))
    }

    @Test
    fun weekendWithExplicitPlan_noWeekendFlagNoHold() {
        val flags = computeDeviationFlags(
            isWeekend = true,
            planStartMillis = plannedStart,
            planEndMillis = plannedEnd,
            clockInMillis = plannedStart,
            clockOutMillis = plannedEnd
        )
        assertTrue(flags.isEmpty())
        assertTrue(!shouldHoldReview(flags))
    }

    @Test
    fun inProgressWeekday_noEndFlag() {
        val clockIn = plannedStart - (40 * 60 * 1000L)
        val flags = computeDeviationFlags(
            isWeekend = false,
            planStartMillis = plannedStart,
            planEndMillis = plannedEnd,
            clockInMillis = clockIn,
            clockOutMillis = null
        )
        assertEquals(listOf(DeviationFlag.EARLY_UNPLANNED), flags)
    }
}
