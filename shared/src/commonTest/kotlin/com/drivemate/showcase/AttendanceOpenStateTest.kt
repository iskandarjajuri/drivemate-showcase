package com.drivemate.showcase

import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.TodayAttendance
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.attendanceState
import com.drivemate.showcase.core.model.displayOvertimeMinutesOrNull
import com.drivemate.showcase.core.model.displayTotalMinutesOrNull
import com.drivemate.showcase.core.util.TimeUtil
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttendanceOpenStateTest {

    @Test
    fun todayAttendanceStateIsOpenWhenClockInExistsWithoutClockOut() {
        val model = TodayAttendance(
            driverUid = "driver-1",
            epochDay = 20_100L,
            clockInMillis = 1_700_000_000_000L,
            clockOutMillis = null,
            updatedAtMillis = 1_700_000_000_000L
        )
        assertEquals(AttendanceDailyState.OPEN, model.state)
    }

    @Test
    fun workdayOpenStateHidesTotalsUntilClosed() {
        val openWorkday = WorkDay(
            id = "driver-1_20100",
            driverUid = "driver-1",
            dateEpochDay = 20_100,
            clockInMillis = 1_700_000_000_000L,
            clockOutMillis = 0L,
            totalMinutes = 0,
            overtimeMinutes = 0
        )
        assertEquals(AttendanceDailyState.OPEN, openWorkday.attendanceState())
        assertNull(openWorkday.displayTotalMinutesOrNull())
        assertNull(openWorkday.displayOvertimeMinutesOrNull())
    }

    @Test
    fun workdayClosedStateKeepsComputedTotals() {
        val closedWorkday = WorkDay(
            id = "driver-1_20101",
            driverUid = "driver-1",
            dateEpochDay = 20_101,
            clockInMillis = 1_700_000_000_000L,
            clockOutMillis = 1_700_002_880_000L,
            totalMinutes = 480,
            overtimeMinutes = 35
        )
        assertEquals(AttendanceDailyState.CLOSED, closedWorkday.attendanceState())
        assertEquals(480, closedWorkday.displayTotalMinutesOrNull())
        assertEquals(35, closedWorkday.displayOvertimeMinutesOrNull())
    }

    @Test
    fun epochDayDerivationRemainsStableForJakartaBoundary() {
        val zone = TimeZone.of("Asia/Jakarta")
        val day = 20_200L
        val endOfDayMillis = TimeUtil.parseHm("23:59", day, zone)
        val nextDayStartMillis = TimeUtil.parseHm("00:00", day + 1L, zone)

        assertEquals(day, TimeUtil.epochDayFromMillis(endOfDayMillis, zone))
        assertEquals(day + 1L, TimeUtil.epochDayFromMillis(nextDayStartMillis, zone))
    }
}
