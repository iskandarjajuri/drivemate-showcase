package com.drivemate.showcase.navigation

import androidx.navigation.NavHostController

/** Keputusan murni navigasi tab bawah (diuji unit di `BottomTabNavigationTest`). */
internal enum class TabNavigationAction {
    /** Tab tujuan sudah paling atas: tidak melakukan apa-apa. */
    None,

    /** Tujuan = tab Home (titik popUpTo): buang semua layar di atasnya, TANPA memulihkan state tersimpan. */
    PopToHome,

    /** Tab lain: navigate dengan saveState/restoreState. */
    NavigateWithRestore
}

/**
 * Akar bug "tab Home membuka Riwayat": `navigate(home) { popUpTo(home){saveState=true}; restoreState=true }`
 * menyimpan tumpukan di atas Home (mis. History) lalu langsung memulihkannya untuk destinasi yang sama,
 * sehingga layar History tampil. Untuk Home, cukup `popBackStack(home, inclusive=false)`.
 */
internal fun tabNavigationAction(currentRoute: String?, targetRoute: String, homeRoute: String): TabNavigationAction =
    when {
        targetRoute == currentRoute -> TabNavigationAction.None
        targetRoute == homeRoute -> TabNavigationAction.PopToHome
        else -> TabNavigationAction.NavigateWithRestore
    }

internal fun NavHostController.navigateToBottomTab(targetRoute: String, homeRoute: String) {
    when (tabNavigationAction(currentDestination?.route, targetRoute, homeRoute)) {
        TabNavigationAction.None -> Unit
        TabNavigationAction.PopToHome -> {
            if (!popBackStack(homeRoute, inclusive = false)) {
                navigate(homeRoute) {
                    popUpTo(homeRoute) { inclusive = false }
                    launchSingleTop = true
                    restoreState = false
                }
            }
        }
        TabNavigationAction.NavigateWithRestore -> navigate(targetRoute) {
            popUpTo(homeRoute) {
                saveState = true
                inclusive = false
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}
