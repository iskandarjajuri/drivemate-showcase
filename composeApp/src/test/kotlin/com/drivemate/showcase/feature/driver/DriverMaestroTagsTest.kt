package com.drivemate.showcase.feature.driver

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverMaestroTagsTest {
    @Test
    fun driverTags_areUnique() {
        val tags = listOf(
            DriverMaestroTags.HOME_ROOT,
            DriverMaestroTags.HOME_CONTENT,
            DriverMaestroTags.HOME_ERROR_STATE,
            DriverMaestroTags.TODAY_SECTION,
            DriverMaestroTags.DAILY_FLOW_SECTION,
            DriverMaestroTags.ATTENDANCE_ENTRY,
            DriverMaestroTags.CHECKLIST_ENTRY,
            DriverMaestroTags.SAFETY_ENTRY,
            DriverMaestroTags.SOP_ENTRY,
            DriverMaestroTags.HISTORY_ENTRY,
            DriverMaestroTags.ANNOUNCEMENTS_ENTRY,
            DriverMaestroTags.CHAT_ENTRY,
            DriverMaestroTags.HOME_ACCOUNT,
            DriverMaestroTags.BELL,
            DriverMaestroTags.BELL_BADGE,
            DriverMaestroTags.NOTIFICATIONS_LIST,
            DriverMaestroTags.ATTENDANCE_ROOT,
            DriverMaestroTags.ATTENDANCE_ERROR_STATE,
            DriverMaestroTags.DAILY_STATUS,
            DriverMaestroTags.TAP_IN_BUTTON,
            DriverMaestroTags.TAP_OUT_BUTTON,
            DriverMaestroTags.ATTENDANCE_BACK_HOME_BUTTON,
            DriverMaestroTags.CHECKLIST_ROOT,
            DriverMaestroTags.CHECKLIST_STATUS_CARD,
            DriverMaestroTags.CHECKLIST_LOCKED_STATE,
            DriverMaestroTags.CHECKLIST_SUBMIT_BUTTON,
            DriverMaestroTags.SAFETY_ROOT,
            DriverMaestroTags.SAFETY_STATUS_CARD,
            DriverMaestroTags.SAFETY_LOCKED_STATE,
            DriverMaestroTags.SAFETY_SUBMIT_BUTTON
        )

        assertEquals(tags.size, tags.toSet().size)
    }
}
