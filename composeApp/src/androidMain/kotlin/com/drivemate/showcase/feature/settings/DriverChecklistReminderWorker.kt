package com.drivemate.showcase.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drivemate.showcase.MainActivity
import com.drivemate.showcase.R
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.messaging.NotificationChannels
import com.drivemate.showcase.messaging.PUSH_TYPE_WORK
import com.drivemate.showcase.domain.usecase.safetychecklist.GetChecklistStatusUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import org.koin.java.KoinJavaComponent.getKoin
import java.util.concurrent.TimeUnit

/**
 * Instantiated by WorkManager, not Koin's factory graph -- same manual
 * `getKoin()` pattern as [com.drivemate.showcase.attendance.AttendanceDailyReconciliationWorker].
 */
class DriverChecklistReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val TAG = "DM/ChecklistReminderWorker"
        const val NOTIF_ID = 3201
    }

    override suspend fun doWork(): Result {
        return try {
            val koin = getKoin()
            val authRepository: AuthRepository = koin.get()
            val getChecklistStatus: GetChecklistStatusUseCase = koin.get()
            val prefs: DriverSettingsPreferences = koin.get()

            val settings = prefs.values.first()
            val currentUser = when (val res = authRepository.currentUser()) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> null
            }
            val hasDriverSession = currentUser != null && currentUser.role == Role.DRIVER

            val checklistDoneToday: Boolean? = if (hasDriverSession) {
                val zone = TimeZone.of("Asia/Jakarta")
                val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()
                when (val res = getChecklistStatus(currentUser.uid, dateEpochDay)) {
                    is AppResult.Success -> res.data
                    is AppResult.Failure -> null
                }
            } else {
                null
            }

            val shouldNotify = DriverChecklistReminderDecision.shouldSchedule(
                hasDriverSession = hasDriverSession,
                reminderEnabled = settings.checklistReminderEnabled,
                checklistDoneToday = checklistDoneToday
            )
            DMLog.i(TAG, "run_end hasDriverSession=$hasDriverSession checklistDoneToday=$checklistDoneToday notify=$shouldNotify")
            if (shouldNotify) {
                showNotification(applicationContext)
            }
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            DMLog.e(TAG, "doWork failed: ${t.message}", t)
            Result.success()
        }
    }

    private fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            DMLog.w(TAG, "showNotification skipped: POST_NOTIFICATIONS not granted")
            return
        }
        NotificationChannels.ensureAll(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        // Ketuk -> tab Tugas (rute push pekerjaan `task`); channel `work` bersama push pekerjaan.
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", PUSH_TYPE_WORK)
            putExtra("kind", "task")
            putExtra("refId", "checklist")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.WORK)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(R.string.driver_settings_reminder_notification_title))
            .setContentText(context.getString(R.string.driver_settings_reminder_notification_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID, notification)
    }
}

object DriverChecklistReminderScheduler {
    const val UNIQUE_WORK_NAME = "driver_checklist_reminder_periodic"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val delay = computeInitialDelayMillis(hour, minute)
        val request = PeriodicWorkRequestBuilder<DriverChecklistReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Pure and testable: how long until the next occurrence of hour:minute in zone, from nowMillis. */
    internal fun computeInitialDelayMillis(
        hour: Int,
        minute: Int,
        zone: TimeZone = TimeZone.of("Asia/Jakarta"),
        nowMillis: Long = TimeUtil.nowEpochMillis()
    ): Long {
        val hm = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        val todayEpochDay = TimeUtil.epochDayOf(nowMillis, zone)
        val todayTarget = TimeUtil.parseHm(hm, todayEpochDay, zone)
        val target = if (todayTarget > nowMillis) todayTarget else TimeUtil.parseHm(hm, todayEpochDay + 1, zone)
        return target - nowMillis
    }
}
