package com.drivemate.showcase.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.drivemate.showcase.messaging.PushRoute
import com.drivemate.showcase.messaging.PushRoutes
import com.drivemate.showcase.messaging.WorkDestination
import com.drivemate.showcase.messaging.destination
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.statusBars
import androidx.navigation.NavHostController
import com.drivemate.showcase.ui.dsv2.LocalScreenHeaderOwnsTopInset
import androidx.navigation.compose.currentBackStackEntryAsState
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.feature.session.SessionViewModel
import com.drivemate.showcase.ui.patterns.DmPostLoginBackground
import com.drivemate.showcase.ui.patterns.DmSystemBarIconAppearance
import com.drivemate.showcase.ui.patterns.rememberDmAuthIsDark
import org.koin.androidx.compose.koinViewModel

/**
 * Zero insets, hoisted so the instance stays stable across recompositions —
 * [Scaffold] does `remember(contentWindowInsets)`, so a fresh `WindowInsets(0)`
 * on every recomposition would invalidate that cache needlessly.
 */
private val NoWindowInsets = WindowInsets(0, 0, 0, 0)

/**
 * Routes that paint their own full-bleed background all the way to the window
 * edges, and therefore must NOT be pre-inset by this Scaffold.
 *
 * This Scaffold wraps the whole NavGraph, so by default every screen is handed
 * an `innerPadding` covering the status and navigation bars. For an ordinary
 * screen that is what you want. For Splash and Login it is exactly the bug: the
 * gradient is drawn by the screen *inside* that already-shrunk box, so it stops
 * short of the bars and the Scaffold's own `containerColor` shows through as a
 * light strip top and bottom.
 *
 * Scoped to these two routes rather than zeroed globally on purpose: 18 of the
 * 35 feature screens do not pad themselves and rely on this Scaffold's inset,
 * so a blanket change would slide their content under the status bar.
 */
private val fullBleedRoutes = setOf(
    NavRoutes.Splash.route,
    NavRoutes.Login.route
)

/**
 * Rute DRIVER yang memakai `DmScreenHeader` (menghitung inset status bar sendiri): root tidak
 * boleh menambah inset atas lagi, supaya tidak terhitung dua kali. Hanya berlaku untuk peran
 * driver -- rute lintas peran (Chat) tetap memakai bar lama bagi admin/expat.
 */
private val driverHeaderOwnedRoutes = setOf(
    NavRoutes.DriverAttendance.route,
    NavRoutes.DriverChecklist.route,
    NavRoutes.DriverSafety.route,
    NavRoutes.DriverSop.route,
    NavRoutes.DriverHistory.route,
    NavRoutes.DriverAnnouncements.route,
    NavRoutes.DriverChats.route,
    NavRoutes.DriverWorkdayDetail.route,
    NavRoutes.DriverTugas.route,
    NavRoutes.DriverNotifications.route,
    NavRoutes.DriverSettings.route,
    NavRoutes.DriverLocationUsage.route,
    NavRoutes.ChatThread.route
)

@Composable
fun DriveMateRootScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val vm: SessionViewModel = koinViewModel()
    val session by vm.session.collectAsState()
    val role = session?.role

    // Requested here (post-login/session-resolved), not in MainActivity.onCreate() —
    // asking before the user even knows what app this is (over Splash/Login) is both
    // poor UX and, on a fresh install, blocks headless QA automation (driver.mjs
    // login, Maestro flows) that waits for a login-screen tag right after pm clear.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op either way — a denial just means no push notifications shown */ }
    LaunchedEffect(session?.uid) {
        if (session?.uid == null) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()

    // Ketukan notifikasi -> buka tujuan push SETELAH sesi siap (bukan di Splash/Login). Dari keadaan
    // killed/belum login rute menunggu sampai login selesai lalu dikonsumsi sekali.
    val pushRoute by PushRoutes.bus.pending.collectAsState()
    val currentRouteForPush = backStackEntry?.destination?.route
    LaunchedEffect(pushRoute, currentRouteForPush, role) {
        val target = pushRoute ?: return@LaunchedEffect
        if (role == null || currentRouteForPush == null) return@LaunchedEffect
        if (currentRouteForPush == NavRoutes.Splash.route || currentRouteForPush == NavRoutes.Login.route) return@LaunchedEffect
        PushRoutes.bus.consume()
        when (target) {
            is PushRoute.ChatThread -> navController.navigate(NavRoutes.ChatThread.build(target.senderUid, target.senderName)) {
                launchSingleTop = true
            }
            is PushRoute.Work -> {
                // Push pekerjaan hanya untuk driver; peran lain mengabaikannya (rute dikonsumsi di atas).
                if (role != Role.DRIVER) return@LaunchedEffect
                when (target.destination()) {
                    WorkDestination.HOME -> navController.navigateToBottomTab(NavRoutes.DriverHome.route, NavRoutes.DriverHome.route)
                    WorkDestination.TASKS -> navController.navigateToBottomTab(NavRoutes.DriverTugas.route, NavRoutes.DriverHome.route)
                    WorkDestination.ANNOUNCEMENTS -> navController.navigate(NavRoutes.DriverAnnouncements.route) { launchSingleTop = true }
                    WorkDestination.HISTORY -> navController.navigateToBottomTab(NavRoutes.DriverHistory.route, NavRoutes.DriverHome.route)
                }
            }
        }
    }
    val currentDestination = backStackEntry?.destination
    val finalAllowList = setOf(
        NavRoutes.DriverHome.route,
        NavRoutes.ExpatHome.route
    )
    val excludedRoutes = setOf(
        NavRoutes.Splash.route,
        NavRoutes.Login.route
    )
    val routeHierarchy = currentDestination
        ?.hierarchy
        ?.mapNotNull { it.route }
        ?.toSet()
        .orEmpty()
    val isHeroEligible = routeHierarchy.any { it in finalAllowList }
    val isHeroExcluded = routeHierarchy.any { it in excludedRoutes }
    val shouldShowHeroNow = isHeroEligible && !isHeroExcluded
    val isFullBleed = routeHierarchy.any { it in fullBleedRoutes }
    val headerOwnsTopInset = role == Role.DRIVER && routeHierarchy.any { it in driverHeaderOwnedRoutes }

    // Splash and Login paint a dark blue gradient behind the system bars, so
    // they need light icons; every other screen sits on a normal surface and
    // follows the theme. Driven from here, in one place, because a per-screen
    // effect races across navigation transitions.
    //
    // Read outside the `||`: short-circuiting would make this composable call
    // conditional on the route, so its remember slot would come and go as the
    // user navigates.
    val isDarkTheme = rememberDmAuthIsDark()
    DmSystemBarIconAppearance(useLightIcons = isFullBleed || isDarkTheme)

    val currentRoute = currentDestination?.route
    val barVisible = isFloatingNavRoute(role, currentRoute)

    // Bar mengecil saat gulir ke bawah; kembali saat gulir ke atas atau pindah rute.
    val density = LocalDensity.current
    val navState = remember(density) {
        FloatingNavBarState(
            collapseThreshold = with(density) { NavCollapseThreshold.toPx() },
            expandThreshold = with(density) { NavExpandThreshold.toPx() }
        )
    }
    LaunchedEffect(currentRoute) { navState.reset() }
    val scrollConnection = remember(navState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                navState.onScroll(-consumed.y)
                return Offset.Zero
            }
        }
    }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = when {
            isFullBleed -> NoWindowInsets
            headerOwnsTopInset -> ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars)
            else -> ScaffoldDefaults.contentWindowInsets
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                DmPostLoginBackground(
                    modifier = Modifier.matchParentSize(),
                    enabled = shouldShowHeroNow
                )
                CompositionLocalProvider(
                    LocalFloatingNavInset provides if (barVisible) FloatingNavReservedHeight else 0.dp,
                    LocalScreenHeaderOwnsTopInset provides headerOwnsTopInset
                ) {
                    content()
                }
            }
            if (barVisible && !imeVisible) {
                val background = MaterialTheme.colorScheme.background
                // Scrim gradien (bukan blur) di belakang bar supaya tepi bar terbaca.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(FloatingNavScrimHeight)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, background)))
                )
                CompositionLocalProvider(LocalFloatingNavCollapsed provides navState.collapsed) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        if (role == Role.EXPAT) {
                            ExpatFloatingNavBar(navController = navController)
                        } else {
                            RoleBottomBar(navController, role)
                        }
                    }
                }
            }
        }
    }
}

private val FloatingNavScrimHeight = 72.dp
