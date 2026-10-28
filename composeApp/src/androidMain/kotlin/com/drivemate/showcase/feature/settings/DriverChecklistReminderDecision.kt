package com.drivemate.showcase.feature.settings

/** Pure decision for whether the daily checklist reminder worker should notify. */
object DriverChecklistReminderDecision {
    fun shouldSchedule(
        hasDriverSession: Boolean,
        reminderEnabled: Boolean,
        checklistDoneToday: Boolean?
    ): Boolean {
        if (!hasDriverSession) return false
        if (!reminderEnabled) return false
        // null (status fetch failed) is treated as "not done" -- a possibly
        // redundant reminder is safer than silently skipping a safety check.
        return checklistDoneToday != true
    }
}
