package com.drivemate.showcase.feature.driver

import android.content.Context
import androidx.core.content.edit

private const val PREF_NAME = "driver_home_completion_banner"
private const val KEY_LAST_SHOWN_EPOCH_DAY = "last_shown_epoch_day"

object DriverHomeCompletionBanner {
    fun shouldShow(lastShownEpochDay: Long?, todayEpochDay: Long): Boolean =
        lastShownEpochDay != todayEpochDay
}

/** SharedPreferences-backed store for [DriverHomeCompletionBanner]'s one-shot-per-day flag. */
class DriverHomeCompletionBannerStore(private val context: Context) {

    fun shouldShowToday(todayEpochDay: Long): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getLong(KEY_LAST_SHOWN_EPOCH_DAY, -1L).takeIf { it != -1L }
        return DriverHomeCompletionBanner.shouldShow(lastShown, todayEpochDay)
    }

    fun markShown(todayEpochDay: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SHOWN_EPOCH_DAY, todayEpochDay)
        }
    }
}
