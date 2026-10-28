package com.drivemate.showcase.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.ui.dsv2.Dsv2BottomBar
import com.drivemate.showcase.ui.dsv2.Dsv2BottomNavItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home

// Driver dan Admin TIDAK lagi lewat Dsv2BottomBar -- lihat cabang Role.DRIVER
// di bawah dan AdminFloatingNavBar (nav mengambang custom, bukan M3
// NavigationBar stock). Expat tetap memakai Dsv2BottomBar satu item ini.
// Sejarah untuk konteks git blame: driver awalnya 5 tab flat lewat
// Dsv2BottomBar yang sama dengan Admin/Expat, dengan pola "Checklist kendaraan"
// digantikan "Pesan" (Checklist dijangkau lewat tile Aksi Cepat di Home, bukan
// lagi tab). feat/driver-floating-nav melanjutkan pola yang sama itu: slot tab
// kelima diganti "Tugas" (hub baru yang menaungi SOP + Checklist + Safety).
// Admin berpindah ke AdminFloatingNavBar (5 tab + Lainnya) di PR nav shell.
private val expatNavItems = listOf(
    Dsv2BottomNavItem(NavRoutes.ExpatHome.route, R.string.nav_expat_overview, Icons.Outlined.Home)
)

private val expatRoutes = expatNavItems.map { it.route }.toSet()

@Composable
fun RoleBottomBar(
    navController: NavHostController,
    role: Role?
) {
    if (role == Role.DRIVER) {
        DriverFloatingNavBar(navController = navController)
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val items = when (role) {
        Role.EXPAT -> expatNavItems
        Role.ADMIN, Role.DRIVER, null -> emptyList()
    }

    val visibleRoutes = when (role) {
        Role.EXPAT -> expatRoutes
        Role.ADMIN, Role.DRIVER, null -> emptySet()
    }

    if (currentRoute == null || currentRoute !in visibleRoutes || items.isEmpty()) {
        return
    }

    Dsv2BottomBar(
        items = items,
        currentRoute = currentRoute,
        onItemSelected = { item ->
            if (currentRoute != item.route) {
                navController.navigate(item.route) {
                    launchSingleTop = true
                }
            }
        }
    )
}
