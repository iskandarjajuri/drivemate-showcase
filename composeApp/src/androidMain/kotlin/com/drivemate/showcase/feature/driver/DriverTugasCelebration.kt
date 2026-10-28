package com.drivemate.showcase.feature.driver

import android.content.Context
import androidx.core.content.edit

private const val PREF_NAME = "driver_tugas_celebration"
private const val KEY_LAST_CELEBRATED_EPOCH_DAY = "last_celebrated_epoch_day"

/** One-time-per-day "2/2 selesai" check-mark animation + haptic on the Tugas nav tab. */
object DriverTugasCelebration {
    fun shouldCelebrate(lastCelebratedEpochDay: Long?, todayEpochDay: Long, isComplete: Boolean): Boolean =
        isComplete && lastCelebratedEpochDay != todayEpochDay
}

/** SharedPreferences-backed store for [DriverTugasCelebration]'s one-shot-per-day flag. */
class DriverTugasCelebrationStore(private val context: Context) {

    fun shouldCelebrateToday(todayEpochDay: Long, isComplete: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastCelebrated = prefs.getLong(KEY_LAST_CELEBRATED_EPOCH_DAY, -1L).takeIf { it != -1L }
        return DriverTugasCelebration.shouldCelebrate(lastCelebrated, todayEpochDay, isComplete)
    }

    fun markCelebrated(todayEpochDay: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_CELEBRATED_EPOCH_DAY, todayEpochDay)
        }
    }
}
