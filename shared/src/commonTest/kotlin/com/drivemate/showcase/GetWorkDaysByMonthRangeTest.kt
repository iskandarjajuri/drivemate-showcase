package com.drivemate.showcase

import com.drivemate.showcase.domain.usecase.report.monthRangeEpochDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class GetWorkDaysByMonthRangeTest {

    @Test
    fun februaryRangeEndsOnFebruaryLastDay() {
        val (startEpoch, endEpoch) = monthRangeEpochDays(year = 2026, month = 2)

        assertEquals(LocalDate(2026, 2, 1).toEpochDays().toInt(), startEpoch)
        assertEquals(LocalDate(2026, 2, 28).toEpochDays().toInt(), endEpoch)
    }

    @Test
    fun januaryRangeDoesNotIncludeFebruaryFirstDay() {
        val (startEpoch, endEpoch) = monthRangeEpochDays(year = 2026, month = 1)

        assertEquals(LocalDate(2026, 1, 1).toEpochDays().toInt(), startEpoch)
        assertEquals(LocalDate(2026, 1, 31).toEpochDays().toInt(), endEpoch)
    }

    @Test
    fun decemberRangeEndsBeforeJanuaryFirstOnYearBoundary() {
        val (startEpoch, endEpoch) = monthRangeEpochDays(year = 2025, month = 12)

        assertEquals(LocalDate(2025, 12, 1).toEpochDays().toInt(), startEpoch)
        assertEquals(LocalDate(2025, 12, 31).toEpochDays().toInt(), endEpoch)
    }
}
