package com.drivemate.showcase

import com.drivemate.showcase.core.util.workdayDocId
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkdayDocIdContractTest {

    @Test
    fun buildsCanonicalUidEpochDayFormat() {
        val docId = workdayDocId(
            driverUid = "driver-uid-123",
            dateEpochDay = 20_123
        )

        assertEquals("driver-uid-123_20123", docId)
    }

    @Test
    fun preservesLargeEpochDayValues() {
        val docId = workdayDocId(
            driverUid = "driver-uid-123",
            dateEpochDay = 99_999
        )

        assertEquals("driver-uid-123_99999", docId)
    }
}
