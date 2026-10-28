package com.drivemate.showcase

import android.app.Application
import com.drivemate.showcase.attendance.AttendanceDailyReconciliationScheduler
import com.drivemate.showcase.core.di.AndroidSharedModule
import com.drivemate.showcase.core.di.commonModule
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.util.AppResult
import com.drivemate.showcase.data.firestore.FirestoreService
import com.drivemate.showcase.di.androidModule
import com.drivemate.showcase.domain.repository.AuthRepository
import com.drivemate.showcase.util.DriverLabelDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Entry-point utama Android.
 *
 * - Inisialisasi dependency injection (Koin)
 * - Menyediakan context Android ke seluruh modul
 * - Log startup agar mudah tracing lifecycle awal.
 * - Healthcheck backend (Firestore) sekali setelah Koin start.
 */
class DriveMateApplication : Application() {

    // Scope ringan untuk tugas non-blocking (healthcheck, dsb.)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startupHealthCheck(firestore: FirestoreService) {
        appScope.launch {
            val tag = "DM/Health"
            DMLog.i(tag, "Startup health-check → ping Firestore (uid-scoped)")
            val t0 = System.currentTimeMillis()

            // Ambil UID kalau ada session; fallback ke "" supaya aman bila race kondisi
            val uid = runCatching { firestore.currentUserUidOrThrow() }.getOrElse { "" }

            when (val r1 = firestore.pingServer(uid)) {
                is AppResult.Success -> {
                    val dt = System.currentTimeMillis() - t0
                    DMLog.i(tag, "Firestore online ✅ (${dt}ms)")
                }
                is AppResult.Failure -> {
                    DMLog.w(tag, "Ping #1 failed: ${r1.throwable.message} — retrying in 1s…")
                    delay(1000)
                    when (val r2 = firestore.pingServer(uid)) {
                        is AppResult.Success -> {
                            val dt = System.currentTimeMillis() - t0
                            DMLog.i(tag, "Firestore online after retry ✅ (${dt}ms total)")
                        }
                        is AppResult.Failure -> {
                            DMLog.e(tag, "Backend offline at startup ❌: ${r2.throwable.message}", r2.throwable)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        DriverLabelDefaults.initialize(getString(R.string.driver_name_fallback))
        com.drivemate.showcase.messaging.NotificationChannels.ensureAll(this)

        // 🔹 Mulai Koin DI
        startKoin {
            androidContext(this@DriveMateApplication)
            modules(
                commonModule,
                AndroidSharedModule,
                androidModule
            )
        }

        AttendanceDailyReconciliationScheduler.schedule(this)

        // 🔍 Verifikasi wiring DI + Healthcheck backend (sekali saat startup)
        // Setelah startKoin { ... } dan koin resolve
        try {
            val koin = org.koin.java.KoinJavaComponent.getKoin()

            // Wajib: pastikan wiring DI
            koin.get<FirestoreService>()
            koin.get<com.drivemate.showcase.domain.repository.AttendanceRepository>()
            koin.get<com.drivemate.showcase.domain.repository.LocationRepository>()
            DMLog.i("DI", "✅ Koin resolved FirestoreService + AttendanceRepository + LocationRepository")

            // 🔒 Jangan ping Firestore sebelum ada user (hindari referensi langsung FirebaseAuth di composeApp)
            // Panggil suspend function dari coroutine agar aman.
            appScope.launch {
                val repo = koin.get<AuthRepository>()
                val hasSession: Boolean = try {
                    when (val cu = repo.currentUser()) {
                        is AppResult.Success -> cu.data != null
                        is AppResult.Failure -> false
                    }
                } catch (_: Throwable) {
                    false
                }

                if (hasSession) {
                    val firestore = koin.get<FirestoreService>()
                    startupHealthCheck(firestore)
                } else {
                    DMLog.i("DM/Health", "Skip Firestore ping at startup (no session yet)")
                }
            }
        } catch (t: Throwable) {
            DMLog.e("DI", "❌ Koin resolve error", t)
        }
    }
}
