package com.drivemate.showcase.feature.driver.calendar

import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.WorkDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DriverCalendarAdapterTest {

    @Test
    fun usesPersistedWorkdayTotalsForWeekendWorkday() {
        val sunday = LocalDate(2024, 1, 14).toEpochDays().toInt()
        val workday = baseWorkday(
            dateEpochDay = sunday,
            totalMinutes = 480,
            overtimeMinutes = 45,
            planSource = PlanSource.DEFAULT
        )

        val summary = computeDriverSummary(workday)

        assertEquals(480, summary.netMinutes)
        assertEquals(45, summary.overtimeMinutes)
        assertEquals(DriverScheduleHint.Weekday, summary.scheduleHint)
    }

    @Test
    fun marksHolidayHintWhenPlanSourceWeekend() {
        val sunday = LocalDate(2024, 1, 14).toEpochDays().toInt()
        val workday = baseWorkday(
            dateEpochDay = sunday,
            totalMinutes = 0,
            overtimeMinutes = 0,
            planSource = PlanSource.WEEKEND
        )

        val summary = computeDriverSummary(workday)

        assertEquals(DriverScheduleHint.Holiday, summary.scheduleHint)
    }

    @Test
    fun marksInProgressWhenClockOutMissing() {
        val monday = LocalDate(2024, 1, 15).toEpochDays().toInt()
        val inProgress = baseWorkday(
            dateEpochDay = monday,
            clockOutMillis = 0L,
            totalMinutes = 0,
            overtimeMinutes = 0,
            planSource = PlanSource.DEFAULT
        )
        val completed = baseWorkday(
            dateEpochDay = monday,
            clockOutMillis = 2_000L,
            totalMinutes = 60,
            overtimeMinutes = 0,
            planSource = PlanSource.DEFAULT
        )

        assertTrue(computeDriverSummary(inProgress).isInProgress)
        assertFalse(computeDriverSummary(completed).isInProgress)
    }

    private fun baseWorkday(
        dateEpochDay: Int,
        clockOutMillis: Long = 2_000L,
        totalMinutes: Int,
        overtimeMinutes: Int,
        planSource: PlanSource
    ): WorkDay {
        return WorkDay(
            id = "wd_$dateEpochDay",
            driverUid = "driver-1",
            dateEpochDay = dateEpochDay,
            clockInMillis = 1_000L,
            clockOutMillis = clockOutMillis,
            totalMinutes = totalMinutes,
            overtimeMinutes = overtimeMinutes,
            planSource = planSource
        )
    }
}
