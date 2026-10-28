package com.drivemate.showcase.feature.driver.notifications

import android.content.Context
import androidx.core.content.edit

private const val PREF_NAME = "driver_notification_read"

/** Penanda baca LOKAL per akun (tanpa koleksi baru, tanpa tulis ke server). */
class DriverNotificationReadStore(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun lastSeenAnnouncementAt(uid: String): Long = prefs.getLong("ann_$uid", 0L)

    fun lastSeenApprovalAt(uid: String): Long = prefs.getLong("appr_$uid", 0L)

    fun markAllSeen(uid: String, atMillis: Long) {
        prefs.edit {
            putLong("ann_$uid", atMillis)
            putLong("appr_$uid", atMillis)
        }
    }
}
