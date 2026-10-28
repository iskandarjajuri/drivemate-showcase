package com.drivemate.showcase.messaging

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.drivemate.showcase.MainActivity
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Instantiated by the OS, not Koin's ViewModel factory graph — same manual
 * `getKoin()` injection pattern as [com.drivemate.showcase.tracking.LocationTrackingService].
 */
class DriveMateFirebaseMessagingService : FirebaseMessagingService() {

    private companion object {
        const val NOTIF_ID = 3101
        const val TAG = "DM/FcmService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushTokenManager by lazy { getKoin().get<PushTokenManager>() }
    private val authRepo by lazy { getKoin().get<AuthRepository>() }

    // Fires whenever the FCM SDK rotates the token on its own, outside of any
    // login flow — SessionViewModel only registers on a session.uid transition,
    // so this is the only path that keeps an already-logged-in session's token
    // current after a rotation.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            val uid = when (val res = authRepo.currentUser()) {
                is AppResult.Success -> res.data?.uid
                is AppResult.Failure -> null
            }
            if (uid.isNullOrBlank()) {
                DMLog.d(TAG, "onNewToken: no signed-in user, skip write")
                return@launch
            }
            // Token berotasi saat sesi berjalan: daftarkan lewat satu pintu (token tidak pernah dicatat).
            pushTokenManager.onNewToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        NotificationChannels.ensureAll(this)
        val route = parsePushRoute(message.data)
        val title = message.notification?.title ?: getString(R.string.fcm_notification_default_title)
        val body = message.notification?.body
        // Log tanpa isi/judul: hanya jenis.
        DMLog.i(TAG, "onMessageReceived: type=${message.data["type"] ?: "general"}")
        if (route is PushRoute.ChatThread) {
            // FCM tidak menampilkan notifikasi sendiri saat app di foreground: kita yang menampilkan,
            // kecuali thread yang sama sedang terbuka (badge sudah cukup).
            if (shouldShowForegroundChat(OpenChatThread.threadId, route.threadId)) {
                showNotification(
                    channel = NotificationChannels.CHAT,
                    title = title,
                    body = body,
                    tag = route.threadId,
                    id = chatNotificationId(route.threadId),
                    extras = message.data
                )
            }
            return
        }
        if (route is PushRoute.Work) {
            // Pekerjaan driver: ditampilkan sendiri di foreground (FCM tidak menampilkan saat app terbuka).
            showNotification(
                channel = NotificationChannels.WORK,
                title = title,
                body = body,
                tag = "${route.kind}:${route.refId}",
                id = workNotificationId(route.kind, route.refId),
                extras = message.data
            )
            return
        }
        showNotification(NotificationChannels.GENERAL, title, body, tag = null, id = NOTIF_ID, extras = message.data)
    }

    private fun showNotification(
        channel: String,
        title: String,
        body: String?,
        tag: String?,
        id: Int,
        extras: Map<String, String>
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras.forEach { (k, v) -> putExtra(k, v) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channel == NotificationChannels.GENERAL) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // No POST_NOTIFICATIONS runtime-permission check here: NotificationManager
        // itself silently no-ops the call when the permission is missing on API 33+,
        // same as the rest of this codebase's notify() call sites.
        manager.notify(tag, id, notification)
    }
}
