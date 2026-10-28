package com.drivemate.showcase.feature.expat

import android.content.Context
import androidx.core.content.edit

private const val PREF_NAME = "expat_review_celebration"
private const val KEY_LAST_CELEBRATED_EPOCH_DAY = "last_celebrated_epoch_day"

/** Perayaan "semua sudah ditinjau" di tab Tinjau: sekali per hari (centang + skala + haptik sukses). */
object ExpatReviewCelebration {
    fun shouldCelebrate(lastCelebratedEpochDay: Long?, todayEpochDay: Long, isComplete: Boolean): Boolean =
        isComplete && lastCelebratedEpochDay != todayEpochDay
}

/**
 * Penyimpan penanda sekali-per-hari. Berkas prefs sendiri (bukan milik Driver) supaya penanda Driver
 * tidak ikut terhapus atau terbaca silang.
 */
class ExpatReviewCelebrationStore(private val context: Context) {

    fun shouldCelebrateToday(todayEpochDay: Long, isComplete: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CELEBRATED_EPOCH_DAY, -1L).takeIf { it != -1L }
        return ExpatReviewCelebration.shouldCelebrate(last, todayEpochDay, isComplete)
    }

    fun markCelebrated(todayEpochDay: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_CELEBRATED_EPOCH_DAY, todayEpochDay)
        }
    }
}
