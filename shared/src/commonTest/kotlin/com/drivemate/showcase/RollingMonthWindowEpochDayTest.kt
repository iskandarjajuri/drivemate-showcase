package com.drivemate.showcase

import com.drivemate.showcase.core.util.TimeUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class RollingMonthWindowEpochDayTest {

    @Test
    fun threeMonthWindow_usesCalendarMonthSubtraction() {
        val endEpochDay = LocalDate(2026, 4, 7).toEpochDays().toInt()

        val range = TimeUtil.rollingThreeMonthWindowEpochDays(endEpochDay)

        assertEquals(LocalDate(2026, 1, 7).toEpochDays().toInt(), range.start)
        assertEquals(endEpochDay, range.endInclusive)
    }

    @Test
    fun threeMonthWindow_clampsMonthEndToValidDate() {
        val endEpochDay = LocalDate(2026, 5, 31).toEpochDays().toInt()

        val range = TimeUtil.rollingThreeMonthWindowEpochDays(endEpochDay)

        assertEquals(LocalDate(2026, 2, 28).toEpochDays().toInt(), range.start)
        assertEquals(endEpochDay, range.endInclusive)
    }
}
