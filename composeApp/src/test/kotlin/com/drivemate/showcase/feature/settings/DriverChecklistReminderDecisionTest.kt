package com.drivemate.showcase.feature.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriverChecklistReminderDecisionTest {

    @Test
    fun shouldSchedule_isFalse_whenNoDriverSession() {
        assertFalse(
            DriverChecklistReminderDecision.shouldSchedule(
                hasDriverSession = false,
                reminderEnabled = true,
                checklistDoneToday = false
            )
        )
    }

    @Test
    fun shouldSchedule_isFalse_whenReminderDisabled() {
        assertFalse(
            DriverChecklistReminderDecision.shouldSchedule(
                hasDriverSession = true,
                reminderEnabled = false,
                checklistDoneToday = false
            )
        )
    }

    @Test
    fun shouldSchedule_isFalse_whenChecklistAlreadyDone() {
        assertFalse(
            DriverChecklistReminderDecision.shouldSchedule(
                hasDriverSession = true,
                reminderEnabled = true,
                checklistDoneToday = true
            )
        )
    }

    @Test
    fun shouldSchedule_isTrue_whenChecklistNotYetDone() {
        assertTrue(
            DriverChecklistReminderDecision.shouldSchedule(
                hasDriverSession = true,
                reminderEnabled = true,
                checklistDoneToday = false
            )
        )
    }

    @Test
    fun shouldSchedule_isTrue_whenStatusUnknown_failSafeToRemind() {
        // Status fetch failed (or any other reason it's unknown) while a driver
        // session exists -- better a possibly-redundant reminder than a silently
        // skipped one for a safety checklist.
        assertTrue(
            DriverChecklistReminderDecision.shouldSchedule(
                hasDriverSession = true,
                reminderEnabled = true,
                checklistDoneToday = null
            )
        )
    }
}
