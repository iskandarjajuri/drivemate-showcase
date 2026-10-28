package com.drivemate.showcase.navigation

import android.net.Uri

/**
 * Route aplikasi.
 * Layer 2: definisikan route dulu.
 * Layer 3: isi screen sesuai fitur.
 */
sealed class NavRoutes(val route: String) {
    data object Splash    : NavRoutes("splash")          // ⬅️ NEW
    data object Login : NavRoutes("login")

    /**
     * Layar Akun — lintas peran, bukan milik satu peran (Blok 5).
     *
     * Setiap peran punya akun, dan gerbang toko aplikasi menuntut jalur penghapusannya
     * mudah ditemukan; membuat tiga route per-peran hanya akan menggandakan layar yang sama.
     */
    data object AccountSettings : NavRoutes("account_settings")
    data object DriverHome : NavRoutes("driver_home")
    data object DriverAttendance : NavRoutes("driver_attendance")
    data object DriverChecklist : NavRoutes("driver_checklist")
    data object DriverSafety : NavRoutes("driver_safety")
    data object DriverSop : NavRoutes("driver_sop")
    data object DriverHistory : NavRoutes("driver_history")
    data object DriverAnnouncements : NavRoutes("driver_announcements")
    data object DriverChats : NavRoutes("driver_chats")
    data object DriverWorkdayDetail : NavRoutes("driver_workday_detail/{workdayId}") {
        fun build(workdayId: String): String = "driver_workday_detail/$workdayId"
    }
    data object DriverTugas : NavRoutes("driver_tugas")
    data object DriverNotifications : NavRoutes("driver_notifications")
    data object DriverSettings : NavRoutes("driver_settings")
    data object DriverLocationUsage : NavRoutes("driver_location_usage")
    data object ExpatHome : NavRoutes("expat_home")
    data object ExpatReviewQueue : NavRoutes("expat_review_queue")
    data object ExpatDrivers : NavRoutes("expat_drivers")
    data object ExpatNotifications : NavRoutes("expat_notifications")
    data object ExpatSettings : NavRoutes("expat_settings")
    data object ExpatProfile : NavRoutes("expat_profile")
    data object ExpatAnnouncements : NavRoutes("expat_announcements")
    data object ExpatChats : NavRoutes("expat_chats")
    data object ExpatDriverDetail : NavRoutes("expat_driver_detail/{driverUid}") {
        fun build(driverUid: String): String = "expat_driver_detail/$driverUid"
    }
    data object ChatThread : NavRoutes("chat_thread/{partnerUid}/{partnerName}") {
        fun build(partnerUid: String, partnerName: String): String =
            "chat_thread/${Uri.encode(partnerUid)}/${Uri.encode(partnerName)}"
    }

    data object ExpatReview : NavRoutes("expat_review/{driverUid}") {
        // driverUid must be Firebase Auth UID.
        fun build(driverUid: String): String =
            "expat_review/$driverUid"
    }
    data object ExpatWorkdayDetail : NavRoutes("expat_workday_detail/{workdayId}") {
        fun build(workdayId: String): String =
            "expat_workday_detail/$workdayId"
    }
}
