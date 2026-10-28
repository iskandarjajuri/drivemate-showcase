package com.drivemate.showcase

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.messaging.PushRoutes
import com.drivemate.showcase.messaging.parsePushRoute
import com.drivemate.showcase.navigation.AppNavGraph
import com.drivemate.showcase.navigation.DriveMateRootScaffold
import com.drivemate.showcase.ui.DriveMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

private const val AUTO_TIME_BASED_DARK_MODE = true

/**
 * Activity utama: host Compose + NavGraph.
 * Layer 2: hanya memanggil [AppNavGraph].
 *
 * AppCompatActivity (bukan ComponentActivity polos) sejak fitur bahasa
 * per-app di Pengaturan driver: `Theme.DriveMate` sudah berinduk ke
 * `Theme.Material3.DayNight.NoActionBar` dari MDC, yang sendiri berinduk ke
 * Theme.AppCompat -- jadi ini bukan migrasi tema, murni ganti base class.
 * Diverifikasi langsung di device fisik (install + jalan, screenshot Splash
 * dan Beranda identik dengan sebelumnya) sebelum diputuskan aman.
 * AppCompatActivity juga men-subclass FragmentActivity, prasyarat
 * BiometricPrompt kalau/jika toggle biometrik dibangun nanti.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge must be requested before super.onCreate() installs the
        // content view.
        //
        // On API 35+ this is a no-op — targetSdk 36 already forces edge-to-edge
        // — but minSdk here is 26, and on API 26..34 the OS does NOT enforce it.
        // Without this call the window stops at the system bars on those
        // versions, so Splash/Login could never paint their gradient behind the
        // status and navigation bars there.
        //
        // Both bars start in the `dark` style — meaning light icons — because
        // the app always opens on Splash, whose gradient is dark in either
        // theme. The default `auto` style left the first rendered frame with
        // dark, near-invisible icons for the moment before the per-route
        // controller committed (measured: status-bar luminance max=138 on that
        // frame vs 255 once corrected). From the second frame on, appearance is
        // owned per-route by DriveMateRootScaffold via DmSystemBarIconAppearance.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        DMLog.i("DM/App", "MainActivity.onCreate() — initializing UI")
        offerPushRoute(intent)


        // 🔹 Compose root
        setContent {
            DMLog.i("DM/UI", "Compose content start — building NavGraph")
            val systemDark = isSystemInDarkTheme()
            var isNight by remember { mutableStateOf(isNightTime()) }
            LaunchedEffect(Unit) {
                while (isActive) {
                    isNight = isNightTime()
                    delay(60_000)
                }
            }
            val computedDark = if (AUTO_TIME_BASED_DARK_MODE && isNight) true else systemDark
            DriveMateTheme(darkTheme = computedDark) {
                val navController = rememberNavController()
                DriveMateRootScaffold(navController = navController) {
                    AppNavGraph(navController)
                }
            }
        }

        DMLog.i("DM/App", "MainActivity.onCreate() selesai setup UI")
    }

    // singleTop: ketukan notifikasi saat app di background/foreground datang ke sini (bukan onCreate).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerPushRoute(intent)
    }

    /** Data push (extras dari sistem, atau PendingIntent notifikasi foreground) -> bus rute. */
    private fun offerPushRoute(intent: Intent?) {
        // Extras dari aplikasi lain bisa membawa Parcelable/Serializable asing yang melempar saat dibaca (tinjauan #5):
        // satu Intent buruk tidak boleh menjatuhkan aplikasi.
        try {
            val extras = intent?.extras ?: return
            val data = extras.keySet().associateWith { runCatching { extras.getString(it) }.getOrNull() }
            PushRoutes.bus.offer(parsePushRoute(data))
        } catch (t: Throwable) {
            DMLog.w("DM/App", "extras Intent diabaikan: ${t.javaClass.simpleName}")
        }
    }

    override fun onStart() {
        super.onStart()
        DMLog.d("DM/App", "MainActivity.onStart() dipanggil")
    }

    override fun onStop() {
        super.onStop()
        DMLog.d("DM/App", "MainActivity.onStop() dipanggil")
    }
}

private fun isNightTime(
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    return hour >= 19 || hour <= 5
}
