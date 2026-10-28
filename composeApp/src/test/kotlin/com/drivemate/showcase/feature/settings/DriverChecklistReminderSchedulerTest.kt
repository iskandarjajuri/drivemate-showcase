package com.drivemate.showcase.feature.settings

import com.drivemate.showcase.core.util.TimeUtil
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DriverChecklistReminderSchedulerTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val epochDay = TimeUtil.epochDayOf(TimeUtil.nowEpochMillis(), zone)

    @Test
    fun computeInitialDelayMillis_targetLaterToday_delaysUntilThatTimeToday() {
        val now = TimeUtil.parseHm("06:00", epochDay, zone)
        val expectedTarget = TimeUtil.parseHm("07:00", epochDay, zone)

        val delay = DriverChecklistReminderScheduler.computeInitialDelayMillis(
            hour = 7,
            minute = 0,
            zone = zone,
            nowMillis = now
        )

        assertEquals(expectedTarget - now, delay)
    }

    @Test
    fun computeInitialDelayMillis_targetAlreadyPassedToday_delaysUntilTomorrow() {
        val now = TimeUtil.parseHm("08:00", epochDay, zone)
        val expectedTarget = TimeUtil.parseHm("07:00", epochDay + 1, zone)

        val delay = DriverChecklistReminderScheduler.computeInitialDelayMillis(
            hour = 7,
            minute = 0,
            zone = zone,
            nowMillis = now
        )

        assertEquals(expectedTarget - now, delay)
    }

    @Test
    fun computeInitialDelayMillis_exactlyAtTarget_delaysUntilTomorrow() {
        // now == today's target is treated as "already passed" (not "later
        // today"), otherwise a worker that runs at exactly HH:mm would loop
        // back to firing immediately again instead of waiting a full day.
        val now = TimeUtil.parseHm("07:00", epochDay, zone)
        val expectedTarget = TimeUtil.parseHm("07:00", epochDay + 1, zone)

        val delay = DriverChecklistReminderScheduler.computeInitialDelayMillis(
            hour = 7,
            minute = 0,
            zone = zone,
            nowMillis = now
        )

        assertEquals(expectedTarget - now, delay)
    }
}
