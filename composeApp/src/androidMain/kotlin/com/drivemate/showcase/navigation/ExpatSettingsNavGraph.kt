package com.drivemate.showcase.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.onFailure
import com.drivemate.showcase.core.util.onSuccess
import com.drivemate.showcase.feature.common.SessionGuard
import com.drivemate.showcase.feature.expat.ExpatHomeViewModel
import com.drivemate.showcase.feature.expat.notifications.ExpatNotificationsViewModel
import com.drivemate.showcase.feature.expat.settings.ExpatProfileScreen
import com.drivemate.showcase.feature.expat.settings.ExpatSettingsScreen
import com.drivemate.showcase.feature.expat.settings.ExpatSettingsViewModel
import com.drivemate.showcase.feature.expat.settings.expatManagedDriverCount
import com.drivemate.showcase.feature.session.SessionViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Hitungan notifikasi belum dibaca untuk lonceng di header tab Expat. Memakai instans VM pada scope entri
 * ExpatHome (sama dengan Beranda dan bar), jadi tidak ada pemuatan tambahan.
 */
@Composable
internal fun rememberExpatNotificationUnread(navController: NavHostController, entry: NavBackStackEntry): Int {
    val homeEntry = remember(entry) { navController.getBackStackEntry(NavRoutes.ExpatHome.route) }
    val vm: ExpatNotificationsViewModel = koinViewModel(viewModelStoreOwner = homeEntry)
    val state by vm.state.collectAsState()
    return state.unreadCount
}

/** Notifikasi dibuka dari lonceng sebagai rute biasa (bukan tab): Kembali mengembalikan ke tab asal. */
internal fun NavHostController.openExpatNotifications() {
    navigate(NavRoutes.ExpatNotifications.route) { launchSingleTop = true }
}

/** Pesan dibuka sebagai tab (sama dengan tab bar). */
internal fun NavHostController.openExpatMessages() {
    navigateToBottomTab(NavRoutes.ExpatChats.route, NavRoutes.ExpatHome.route)
}

/** Rute tab Pengaturan dan sub-layar Profil peran Expat. */
internal fun NavGraphBuilder.expatSettingsRoutes(navController: NavHostController) {
    composable(NavRoutes.ExpatSettings.route) { entry ->
        val vm: ExpatSettingsViewModel = koinViewModel()
        val svm: SessionViewModel = koinViewModel()
        val scope = rememberCoroutineScope()
        val unread = rememberExpatNotificationUnread(navController, entry)
        SessionGuard(navController, required = Role.EXPAT) {
            ExpatSettingsScreen(
                vm = vm,
                notificationUnread = unread,
                onOpenNotifications = { navController.openExpatNotifications() },
                onOpenProfile = { navController.navigate(NavRoutes.ExpatProfile.route) },
                onContactAdmin = { navController.openExpatMessages() },
                onOpenAccountRisk = { navController.navigate(NavRoutes.AccountSettings.route) },
                onLogout = {
                    scope.launch {
                        DMLog.i("DM/AuthUI", "Logout clicked from expat_settings")
                        svm.logout()
                            .onSuccess {
                                DMLog.i("DM/Nav", "Logout success → Login")
                                navController.navigate(NavRoutes.Login.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            .onFailure { err -> DMLog.e("DM/Auth", "Logout failed: ${err::class.simpleName}") }
                    }
                }
            )
        }
    }

    composable(NavRoutes.ExpatProfile.route) { entry ->
        val vm: ExpatSettingsViewModel = koinViewModel()
        // Jumlah driver dari data Beranda yang sudah dimuat (scope entri ExpatHome), tanpa panggilan baru.
        val homeEntry = remember(entry) { navController.getBackStackEntry(NavRoutes.ExpatHome.route) }
        val homeVm: ExpatHomeViewModel = koinViewModel(viewModelStoreOwner = homeEntry)
        val drivers by homeVm.drivers.collectAsState()
        val loading by homeVm.loading.collectAsState()
        val error by homeVm.error.collectAsState()
        SessionGuard(navController, required = Role.EXPAT) {
            ExpatProfileScreen(
                vm = vm,
                managedDriverCount = expatManagedDriverCount(
                    loading = loading,
                    hasError = error != null,
                    driverCount = drivers.size
                ),
                onBack = { navController.popBackStack() },
                onContactAdmin = { navController.openExpatMessages() }
            )
        }
    }
}
