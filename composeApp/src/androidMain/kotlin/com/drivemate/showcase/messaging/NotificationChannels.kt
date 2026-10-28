package com.drivemate.showcase.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.drivemate.showcase.R

/**
 * Channel notifikasi aplikasi. Didaftarkan saat startup (bukan saat pesan datang) supaya push
 * `notification` yang ditampilkan SISTEM di background memakai channel yang benar.
 */
object NotificationChannels {
    const val CHAT = "chat"
    const val WORK = "work"
    const val GENERAL = "fcm_default"

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun register(id: String, nameRes: Int, importance: Int) {
            if (manager.getNotificationChannel(id) != null) return
            manager.createNotificationChannel(NotificationChannel(id, context.getString(nameRes), importance))
        }
        register(CHAT, R.string.notification_channel_chat, NotificationManager.IMPORTANCE_HIGH)
        register(WORK, R.string.notification_channel_work, NotificationManager.IMPORTANCE_HIGH)
        register(GENERAL, R.string.fcm_notification_channel, NotificationManager.IMPORTANCE_DEFAULT)
    }
}
