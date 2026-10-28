package com.drivemate.showcase

import com.drivemate.showcase.core.util.attendanceDailyDocId
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceDailyDocIdContractTest {

    @Test
    fun buildsCanonicalUidEpochDayFormat() {
        val docId = attendanceDailyDocId(
            driverUid = "driver-uid-123",
            epochDay = 20_123L
        )

        assertEquals("driver-uid-123_20123", docId)
    }

    @Test
    fun preservesLargeEpochDayValues() {
        val docId = attendanceDailyDocId(
            driverUid = "driver-uid-123",
            epochDay = 123_456_789L
        )

        assertEquals("driver-uid-123_123456789", docId)
    }
}
