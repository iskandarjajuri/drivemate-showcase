package com.drivemate.showcase.navigation

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.Role
import com.drivemate.showcase.core.util.onFailure
import com.drivemate.showcase.core.util.onSuccess
import com.drivemate.showcase.domain.repository.WorkCalendarRepository
import com.drivemate.showcase.feature.auth.AuthScreen
import com.drivemate.showcase.feature.auth.AuthUiState
import com.drivemate.showcase.feature.auth.AuthViewModel
import com.drivemate.showcase.feature.driver.DriverAttendanceScreen
import com.drivemate.showcase.feature.splash.SplashScreen
import com.drivemate.showcase.location.getLastKnownLatLngOrNull
import com.drivemate.showcase.tracking.DriverTrackingController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.core.parameter.parametersOf

private data class AttendanceActions(
    val onTapIn: () -> Unit,
    val onTapOut: () -> Unit
)

/** SavedStateHandle key for the one-shot "checklist just submitted" result. */
private const val DRIVER_CHECKLIST_RESULT_KEY = "driver_result"

private fun NavHostController.navigateDriverHomeClearingToStart() {
    navigate(NavRoutes.DriverHome.route) {
        val startRoute = graph.startDestinationRoute
        if (startRoute != null) {
            popUpTo(startRoute) { inclusive = false }
        } else {
            popUpTo(graph.findStartDestination().id) { inclusive = false }
        }
        launchSingleTop = true
        restoreState = false
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController
) {
    DMLog.i("DM/Nav", "NavHost start at route=${NavRoutes.Splash.route}")
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route
    ) {
        // ===== SPLASH (router by session.role) =====
        composable(NavRoutes.Splash.route) {
            // Readiness-gated instead of a fixed 3s delay: the splash now leaves
            // as soon as the session stream has emitted (plus an anti-flash
            // floor), so a warm start is fast and a slow one is never cut short.
            val svm: com.drivemate.showcase.feature.session.SessionViewModel =
                koinViewModel()
            val isSessionReady by svm.isReady.collectAsState()

            SplashScreen(
                isReady = isSessionReady,
                onFinished = {
                    DMLog.i("DM/Nav", "Splash → ${NavRoutes.Login.route}")
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ===== LOGIN =====
        composable(NavRoutes.Login.route) {
            DMLog.d("DM/Nav", "Entered route=${NavRoutes.Login.route}")

            val vm: AuthViewModel = koinViewModel()
            val uiState by vm.state.collectAsState()
            val svm: com.drivemate.showcase.feature.session.SessionViewModel = koinViewModel()
            val isReady by svm.isReady.collectAsState(initial = false)
            val session by svm.session.collectAsState()
            var didNavigate by remember { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current

            // --- React ke perubahan state
            LaunchedEffect(uiState) {
                when (val s = uiState) {
                    is AuthUiState.Success -> {
                        // Biarkan Splash (session router) yang navigate setelah role ter-resolve
                        DMLog.i("DM/Auth", "Auth success → routing handled by Splash")
                        vm.resetToIdle()
                    }
                    is AuthUiState.Error -> {
                        DMLog.e("DM/Auth", "Login error: ${s.error.reason}")
                    }
                    is AuthUiState.Loading -> {
                        DMLog.d("DM/Auth", "Login loading…")
                    }
                    AuthUiState.Idle -> Unit
                }
            }

            // --- Observe session changes while on login route; navigate when role resolved
            LaunchedEffect(isReady, session) {
                val current = session

                when {
                    !isReady -> {
                        DMLog.d("DM/Nav", "Login route: auth not ready, skip nav")
                        return@LaunchedEffect
                    }
                    current == null -> {
                        didNavigate = false // reset gate when signed out
                        DMLog.d("DM/Nav", "Login route: no session, stay on login")
                        return@LaunchedEffect
                    }
                    current.role == null -> {
                        DMLog.d("DM/Nav", "Login route: session role unresolved, waiting")
                        return@LaunchedEffect
                    }
                    didNavigate -> {
                        DMLog.d("DM/Nav", "Login route: already navigated, skip")
                        return@LaunchedEffect
                    }
                }

                val role = current.role!!  // aman karena sudah di-guard di atas

                val dest = when (role) {
                    Role.DRIVER -> NavRoutes.DriverHome.route
                    Role.EXPAT  -> NavRoutes.ExpatHome.route
                    Role.ADMIN  -> {
                        // The showcase ships the Driver and Expat experiences only.
                        // An admin session is signed out instead of being routed anywhere.
                        DMLog.i("DM/Nav", "Admin role is not part of the showcase → sign out")
                        android.widget.Toast.makeText(
                            context,
                            context.getString(com.drivemate.showcase.R.string.auth_role_not_supported),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        svm.logout()
                        return@LaunchedEffect
                    }
                }

                DMLog.i(
                    "DM/Nav",
                    "Login route observes session → uid=${current.uid}, role=${current.role} → navigate $dest"
                )
                didNavigate = true
                navController.navigate(dest) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }

            AuthScreen(
                state = uiState,
                onLogin = { email, pass ->
                    DMLog.d("DM/Auth", "onLogin(email=$email)")
                    vm.login(email, pass)
                },
                prefillEmail = null,
                onClearError = { vm.clearError() }
            )
        }

        // Blok 5. TANPA SessionGuard per-peran: layar ini milik semua peran, dan
        // gerbang tokonya justru menuntut ia bisa dicapai siapa pun yang sudah login.
        composable(NavRoutes.AccountSettings.route) {
            val vm: com.drivemate.showcase.feature.account.AccountSettingsViewModel =
                koinViewModel()
            val svm: com.drivemate.showcase.feature.session.SessionViewModel =
                koinViewModel()
            val scope = rememberCoroutineScope()
            com.drivemate.showcase.feature.account.AccountSettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                // SSOT §1 memindahkan keluar ke layar Akun. Penanganannya sengaja disalin dari
                // pola yang sudah dipakai tiga route Home, BUKAN diekstrak jadi satu helper:
                // mengekstraknya berarti menyentuh wiring Home driver dan expat sekaligus, dan
                // alur driver dikunci sebagai baseline. Duplikasi keempat ini dicatat sebagai
                // temuan terpisah di body PR — bukan diperbaiki sekalian.
                onLogout = {
                    scope.launch {
                        DMLog.i("DM/AuthUI", "Logout clicked from account_settings")
                        svm.logout()
                            .onSuccess {
                                DMLog.i("DM/Nav", "Logout success → Login")
                                navController.navigate(NavRoutes.Login.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            .onFailure { err ->
                                DMLog.e("DM/Auth", "Logout failed: ${err.message}")
                            }
                    }
                }
            )
        }

        // ===== DRIVER =====
        composable(NavRoutes.DriverHome.route) { backStackEntry ->
            val vm: com.drivemate.showcase.feature.driver.DriverViewModel = koinViewModel()
            val uiState by vm.state.collectAsState()
            val trackingState by vm.trackingState.collectAsState()
            val homeState by vm.homeState.collectAsState()
            val svm: com.drivemate.showcase.feature.session.SessionViewModel = koinViewModel()
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val session by svm.session.collectAsState()
            // A one-shot read-and-remove, NOT collectAsState() on a live StateFlow:
            // observing the SavedStateHandle reactively meant the screen's own
            // "consume" write (setting it back to null) fed back in as a new
            // emission, cancelling the LaunchedEffect that was still inside
            // showSnackbar -- the snackbar appeared for at most one frame.
            // Plain remember (see rememberConsumedLongResult's own doc):
            // rememberSaveable was tried here too, but Navigation Compose
            // persists rememberSaveable state per back-stack entry across
            // navigate-away-and-back, so it kept restoring the already-
            // consumed value -- the snackbar reappeared every time the driver
            // returned to Home (e.g. opening the checklist from its own
            // action and pressing back).
            val checklistSubmittedAtMillis = rememberConsumedLongResult(
                backStackEntry.savedStateHandle,
                DRIVER_CHECKLIST_RESULT_KEY
            )
            val attendanceActions = rememberAttendanceActions(vm = vm, context = context)
            // Satu sumber lencana lonceng + layar Notifikasi: ViewModel ini (scope entri DriverHome).
            val notifVm: com.drivemate.showcase.feature.driver.notifications.DriverNotificationsViewModel = koinViewModel()
            val notificationState by notifVm.state.collectAsState()
            // Pesan chat belum dibaca (instans sama dengan badge nav bar; scope entri DriverHome).
            val chatUnreadVm: com.drivemate.showcase.feature.chat.ChatUnreadViewModel = koinViewModel()
            val chatUnread by chatUnreadVm.unread.collectAsState()
            LaunchedEffect(session?.uid, homeState.sopAccepted, homeState.checklistSubmitted, homeState.safetySubmitted, homeState.recentWorkDays, chatUnread) {
                notifVm.setInputs(
                    com.drivemate.showcase.feature.driver.notifications.DriverNotificationInputs(
                        uid = session?.uid,
                        pendingTasks = com.drivemate.showcase.feature.driver.notifications.pendingDriverTasks(
                            homeState.sopAccepted, homeState.checklistSubmitted, homeState.safetySubmitted
                        ),
                        workDays = homeState.recentWorkDays,
                        chatUnread = chatUnread
                    )
                )
            }
            // Localised role names (EN/ID/JA) instead of hardcoded English.
            val roleLabel = when (session?.role) {
                Role.ADMIN -> androidx.compose.ui.res.stringResource(com.drivemate.showcase.R.string.chat_role_admin)
                Role.EXPAT -> androidx.compose.ui.res.stringResource(com.drivemate.showcase.R.string.chat_role_expat)
                Role.DRIVER -> androidx.compose.ui.res.stringResource(com.drivemate.showcase.R.string.chat_role_driver)
                null -> null
            }

            // Foto profil: dimuat sekali per akun (ProfilePhotoStore), jadi Home tidak menanyakan
            // ulang setiap kali kembali dari Settings.
            val photoVm: com.drivemate.showcase.feature.settings.photo.ProfilePhotoViewModel = koinViewModel()
            val profilePhotoJpeg by photoVm.photoJpeg.collectAsState()
            val profilePhoto = com.drivemate.showcase.ui.rememberProfilePhotoBitmap(profilePhotoJpeg)

            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverHomeScreen(
                    profilePhoto = profilePhoto,
                    state = uiState,
                    trackingState = trackingState,
                    driverName = homeState.driverName,
                    driverEmail = session?.email,
                    driverRole = roleLabel,
                    sopAccepted = homeState.sopAccepted,
                    checklistSubmitted = homeState.checklistSubmitted,
                    safetySubmitted = homeState.safetySubmitted,
                    homeStateLoading = homeState.loading,
                    plannedStartMillis = homeState.plannedStartMillis,
                    plannedEndMillis = homeState.plannedEndMillis,
                    planSource = homeState.planSource,
                    recentWorkDays = homeState.recentWorkDays,
                    checklistSubmittedAtMillis = checklistSubmittedAtMillis,
                    onRefresh = { vm.manualRefresh() },
                    onOpenAttendance = { navController.navigate(NavRoutes.DriverAttendance.route) },
                    onOpenChecklist = { navController.navigate(NavRoutes.DriverChecklist.route) },
                    onOpenSafety = { navController.navigate(NavRoutes.DriverSafety.route) },
                    onOpenSop = { navController.navigate(NavRoutes.DriverSop.route) },
                    onOpenHistory = { navController.navigate(NavRoutes.DriverHistory.route) },
                    onOpenChat = { navController.navigate(NavRoutes.DriverChats.route) },
                    notificationCount = notificationState.unreadCount,
                    onOpenNotifications = { navController.navigate(NavRoutes.DriverNotifications.route) },
                    onOpenWorkdayDetail = { workdayId ->
                        navController.navigate(NavRoutes.DriverWorkdayDetail.build(workdayId))
                    },
                    onTapIn = attendanceActions.onTapIn,
                    onTapOut = attendanceActions.onTapOut,
                    onTrackingSync = { clockedIn, permissionGranted, providerEnabled ->
                        vm.reconcileTracking(context, clockedIn, permissionGranted, providerEnabled)
                    },
                    // Driver-only: gear icon opens the new Settings screen, not
                    // AccountSettings directly (Admin/Expat gear icons still do --
                    // that screen's deletion path stays reachable for drivers via
                    // Profil -> "Hapus akun").
                    onOpenAccount = { navController.navigate(NavRoutes.DriverSettings.route) }
                )
            }
        }

        // ===== DRIVER ATTENDANCE =====
        composable(NavRoutes.DriverAttendance.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(NavRoutes.DriverHome.route)
            }
            val vm: com.drivemate.showcase.feature.driver.DriverViewModel = koinViewModel(
                viewModelStoreOwner = parentEntry
            )
            val uiState by vm.state.collectAsState()
            val homeState by vm.homeState.collectAsState()
            val context = LocalContext.current
            val attendanceActions = rememberAttendanceActions(vm = vm, context = context)

            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                DriverAttendanceScreen(
                    state = uiState,
                    checklistSubmitted = homeState.checklistSubmitted,
                    safetySubmitted = homeState.safetySubmitted,
                    plannedStartMillis = homeState.plannedStartMillis,
                    plannedEndMillis = homeState.plannedEndMillis,
                    planSource = homeState.planSource,
                    onTapIn = attendanceActions.onTapIn,
                    onTapOut = attendanceActions.onTapOut,
                    onBack = { navController.popBackStack() },
                    onBackHome = { navController.navigateDriverHomeClearingToStart() },
                    onRefresh = { vm.manualRefresh() }
                )
            }
        }

        // ===== DRIVER TUGAS (hub) =====
        composable(NavRoutes.DriverTugas.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(NavRoutes.DriverHome.route)
            }
            val vm: com.drivemate.showcase.feature.driver.DriverViewModel = koinViewModel(
                viewModelStoreOwner = parentEntry
            )
            val uiState by vm.state.collectAsState()
            val homeState by vm.homeState.collectAsState()

            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverTugasScreen(
                    state = uiState,
                    sopAccepted = homeState.sopAccepted,
                    checklistSubmitted = homeState.checklistSubmitted,
                    safetySubmitted = homeState.safetySubmitted,
                    homeStateLoading = homeState.loading,
                    onBack = { navController.popBackStack() },
                    onOpenSop = { navController.navigate(NavRoutes.DriverSop.route) },
                    onOpenChecklist = { navController.navigate(NavRoutes.DriverChecklist.route) },
                    onOpenSafety = { navController.navigate(NavRoutes.DriverSafety.route) },
                    onRefresh = { vm.manualRefresh() },
                    onOpenAttendance = { navController.navigate(NavRoutes.DriverAttendance.route) }
                )
            }
        }

        // ===== DRIVER CHECKLIST =====
        composable(NavRoutes.DriverChecklist.route) {
            val vm: com.drivemate.showcase.feature.driver.DriverChecklistViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverChecklistScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onBackHome = { navController.navigateDriverHomeClearingToStart() },
                    onSubmittedReturnHome = { submittedAtMillis ->
                        navController.navigateDriverHomeClearingToStart()
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(DRIVER_CHECKLIST_RESULT_KEY, submittedAtMillis)
                    }
                )
            }
        }

        // ===== DRIVER SAFETY DECLARATION =====
        composable(NavRoutes.DriverSafety.route) {
            val vm: com.drivemate.showcase.feature.driver.DriverSafetyDeclarationViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverSafetyDeclarationScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onBackHome = { navController.navigateDriverHomeClearingToStart() }
                )
            }
        }

        // ===== DRIVER SOP =====
        composable(NavRoutes.DriverSop.route) {
            val vm: com.drivemate.showcase.feature.driver.DriverSopViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverSopScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ===== DRIVER SETTINGS =====
        composable(NavRoutes.DriverSettings.route) {
            val vm: com.drivemate.showcase.feature.settings.DriverSettingsViewModel = koinViewModel()
            val svm: com.drivemate.showcase.feature.session.SessionViewModel = koinViewModel()
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.settings.DriverSettingsScreen(
                    vm = vm,
                    photoVm = koinViewModel(),
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        scope.launch {
                            DriverTrackingController.stop(context)
                            svm.logout()
                                .onSuccess {
                                    navController.navigate(NavRoutes.Login.route) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                .onFailure { err ->
                                    DMLog.e("DM/Auth", "Logout failed: ${err.message}")
                                }
                        }
                    },
                    onOpenLocationUsage = { navController.navigate(NavRoutes.DriverLocationUsage.route) },
                    onOpenSop = { navController.navigate(NavRoutes.DriverSop.route) },
                    onOpenManageAccount = { navController.navigate(NavRoutes.AccountSettings.route) }
                )
            }
        }

        composable(NavRoutes.DriverLocationUsage.route) {
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.settings.DriverLocationUsageScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ===== DRIVER WORK HISTORY =====
        composable(NavRoutes.DriverHistory.route) {
            // Redesain Agenda View — docs/design/work-history-agenda-view.md.
            // Detail hari kini expand INLINE di list, jadi tidak lagi menavigasi ke
            // DriverWorkdayDetail dari layar ini.
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverWorkHistoryAgendaScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(NavRoutes.DriverAnnouncements.route) {
            val vm: com.drivemate.showcase.feature.driver.DriverAnnouncementsViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverAnnouncementsScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(NavRoutes.DriverNotifications.route) {
            // Instans yang SAMA dengan lencana lonceng di Home (scope entri DriverHome).
            val homeEntry = remember(it) { navController.getBackStackEntry(NavRoutes.DriverHome.route) }
            val vm: com.drivemate.showcase.feature.driver.notifications.DriverNotificationsViewModel =
                koinViewModel(viewModelStoreOwner = homeEntry)
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.notifications.DriverNotificationsScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenTugas = { navController.navigate(NavRoutes.DriverTugas.route) },
                    onOpenAnnouncements = { navController.navigate(NavRoutes.DriverAnnouncements.route) },
                    onOpenHistory = { navController.navigate(NavRoutes.DriverHistory.route) },
                    onOpenChats = { navController.navigate(NavRoutes.DriverChats.route) }
                )
            }
        }

        composable(NavRoutes.DriverChats.route) {
            val vm: com.drivemate.showcase.feature.chat.ChatInboxViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.chat.ChatInboxScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenThread = { uid, name ->
                        navController.navigate(NavRoutes.ChatThread.build(partnerUid = uid, partnerName = name))
                    }
                )
            }
        }

        composable(
            route = NavRoutes.DriverWorkdayDetail.route,
            arguments = listOf(
                navArgument("workdayId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val workdayId = backStackEntry.arguments?.getString("workdayId") ?: return@composable
            val vm: com.drivemate.showcase.feature.driver.DriverWorkdayDetailViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.DRIVER) {
                com.drivemate.showcase.feature.driver.DriverWorkdayDetailScreen(
                    vm = vm,
                    workdayId = workdayId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ===== EXPAT HOME =====
        composable(NavRoutes.ExpatHome.route) {
            val svm: com.drivemate.showcase.feature.session.SessionViewModel = koinViewModel()
            val vm: com.drivemate.showcase.feature.expat.ExpatHomeViewModel = koinViewModel()
            val chatUnreadVm: com.drivemate.showcase.feature.chat.ChatUnreadViewModel = koinViewModel()
            val chatUnread by chatUnreadVm.unread.collectAsState()
            val notificationsVm: com.drivemate.showcase.feature.expat.notifications.ExpatNotificationsViewModel =
                koinViewModel()
            val notificationUnread by notificationsVm.state.collectAsState()
            val scope = rememberCoroutineScope()

            com.drivemate.showcase.feature.expat.notifications.ExpatNotificationInputsEffect(
                homeVm = vm,
                notificationsVm = notificationsVm,
                chatUnread = chatUnread
            )
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.ExpatHomeScreen(
                    vm = vm,
                    onOpenAccount = { navController.navigateToBottomTab(NavRoutes.ExpatSettings.route, NavRoutes.ExpatHome.route) },
                    onLogout = {
                        scope.launch {
                            DMLog.i("DM/AuthUI", "Logout clicked from expat_home (nav handler)")
                            svm.logout()
                                .onSuccess {
                                    DMLog.i("DM/Nav", "Logout success → Login")
                                    DMLog.i("DM/AuthUI", "Logout completed, navigating to login (expat_home)")
                                    navController.navigate(NavRoutes.Login.route) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                .onFailure { err ->
                                    DMLog.e("DM/Auth", "Logout failed: ${err.message}")
                                }
                        }
                    },
                    onOpenMonthlyReview = { driverUid ->
                        // Tanpa uid di log: rute dan argumennya memuat driverUid.
                        DMLog.i("DM/Nav", "Navigate ExpatReview")
                        navController.navigate(NavRoutes.ExpatReview.build(driverUid))
                    },
                    onOpenAnnouncements = {
                        navController.navigate(NavRoutes.ExpatAnnouncements.route)
                    },
                    onOpenChat = {
                        navController.navigate(NavRoutes.ExpatChats.route)
                    },
                    onOpenReviewQueue = {
                        navController.navigateToBottomTab(NavRoutes.ExpatReviewQueue.route, NavRoutes.ExpatHome.route)
                    },
                    onOpenDrivers = {
                        navController.navigateToBottomTab(NavRoutes.ExpatDrivers.route, NavRoutes.ExpatHome.route)
                    },
                    chatUnread = chatUnread,
                    notificationUnread = notificationUnread.unreadCount,
                    latestAnnouncementTitle = notificationUnread.items
                        .firstOrNull { it.kind == com.drivemate.showcase.feature.expat.notifications.ExpatNotificationKind.ANNOUNCEMENT }
                        ?.text,
                    onOpenNotifications = { navController.openExpatNotifications() }
                )
            }
        }

        composable(NavRoutes.ExpatReviewQueue.route) { entry ->
            val vm: com.drivemate.showcase.feature.expat.queue.ExpatReviewQueueViewModel = koinViewModel()
            val notificationUnread = rememberExpatNotificationUnread(navController, entry)
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.queue.ExpatReviewQueueScreen(
                    vm = vm,
                    onOpenReview = { driverUid -> navController.navigate(NavRoutes.ExpatReview.build(driverUid)) },
                    notificationUnread = notificationUnread,
                    onOpenNotifications = { navController.openExpatNotifications() }
                )
            }
        }

        composable(NavRoutes.ExpatDrivers.route) {
            // Data driver memakai instans yang sama dengan Beranda (scope entri ExpatHome).
            val homeEntry = remember(it) { navController.getBackStackEntry(NavRoutes.ExpatHome.route) }
            val homeVm: com.drivemate.showcase.feature.expat.ExpatHomeViewModel =
                koinViewModel(viewModelStoreOwner = homeEntry)
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.queue.ExpatDriversScreen(
                    vm = homeVm,
                    onOpenDriver = { driverUid -> navController.navigate(NavRoutes.ExpatReview.build(driverUid)) },
                    notificationUnread = rememberExpatNotificationUnread(navController, it),
                    onOpenNotifications = { navController.openExpatNotifications() }
                )
            }
        }

        composable(NavRoutes.ExpatNotifications.route) {
            // Sumber data memakai instans yang sama dengan Beranda/badge Pesan (scope entri ExpatHome):
            // tidak ada panggilan jaringan baru selain pengumuman yang sudah dipakai layar lain.
            val homeEntry = remember(it) { navController.getBackStackEntry(NavRoutes.ExpatHome.route) }
            val vm: com.drivemate.showcase.feature.expat.notifications.ExpatNotificationsViewModel =
                koinViewModel(viewModelStoreOwner = homeEntry)
            val homeVm: com.drivemate.showcase.feature.expat.ExpatHomeViewModel =
                koinViewModel(viewModelStoreOwner = homeEntry)
            val chatUnreadVm: com.drivemate.showcase.feature.chat.ChatUnreadViewModel =
                koinViewModel(viewModelStoreOwner = homeEntry)
            val chatUnread by chatUnreadVm.unread.collectAsState()
            LaunchedEffect(Unit) {
                if (homeVm.drivers.value.isEmpty()) homeVm.refresh()
            }
            // Pengisian input juga dilakukan bar navigasi (selalu tampil di rute tab); di sini tetap dipasang
            // supaya layar ini benar walau dibuka tanpa bar.
            com.drivemate.showcase.feature.expat.notifications.ExpatNotificationInputsEffect(
                homeVm = homeVm,
                notificationsVm = vm,
                chatUnread = chatUnread
            )
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.notifications.ExpatNotificationsScreen(
                    vm = vm,
                    onRetry = { homeVm.refresh() },
                    onOpenReview = { driverUid ->
                        navController.navigate(NavRoutes.ExpatReview.build(driverUid))
                    },
                    onOpenAnnouncements = { navController.navigate(NavRoutes.ExpatAnnouncements.route) },
                    onOpenChats = { navController.navigate(NavRoutes.ExpatChats.route) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Tab Pengaturan + sub-layar Profil (berkas terpisah agar AppNavGraph tidak membengkak).
        expatSettingsRoutes(navController)

        composable(NavRoutes.ExpatAnnouncements.route) {
            val vm: com.drivemate.showcase.feature.announcement.AnnouncementsViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.announcement.AnnouncementFeedScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(NavRoutes.ExpatChats.route) { entry ->
            val vm: com.drivemate.showcase.feature.chat.ChatInboxViewModel = koinViewModel()
            val notificationUnread = rememberExpatNotificationUnread(navController, entry)
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.chat.ChatInboxScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenThread = { uid, name ->
                        navController.navigate(NavRoutes.ChatThread.build(partnerUid = uid, partnerName = name))
                    },
                    topBarActions = {
                        // Slot topBar terpisah dari konten bertag: lonceng membawa flag resource-id sendiri untuk Maestro.
                        com.drivemate.showcase.feature.expat.ExpatBellAction(
                            unread = notificationUnread,
                            onClick = { navController.openExpatNotifications() },
                            exposeResourceId = true
                        )
                    }
                )
            }
        }

        composable(
            route = NavRoutes.ChatThread.route,
            arguments = listOf(
                navArgument("partnerUid") { type = NavType.StringType },
                navArgument("partnerName") { type = NavType.StringType }
            )
        ) { entry ->
            val partnerUid = entry.arguments?.getString("partnerUid") ?: return@composable
            val partnerName = android.net.Uri.decode(entry.arguments?.getString("partnerName").orEmpty())
            val vm: com.drivemate.showcase.feature.chat.ChatThreadViewModel =
                koinViewModel(parameters = { parametersOf(partnerUid, partnerName) })
            com.drivemate.showcase.feature.common.SessionGuard(navController) {
                com.drivemate.showcase.feature.chat.ChatThreadScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ===== EXPAT DRIVER DETAIL =====
        composable(
            route = NavRoutes.ExpatDriverDetail.route,
            arguments = listOf(
                navArgument("driverUid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverUid = backStackEntry.arguments?.getString("driverUid") ?: return@composable
            val vm: com.drivemate.showcase.feature.expat.detail.ExpatDriverDetailViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.detail.ExpatDriverDetailScreen(
                    vm = vm,
                    driverUid = driverUid,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ===== EXPAT REVIEW (compat) =====
        composable(
            route = NavRoutes.ExpatReview.route,
            arguments = listOf(
                // driverUid must be Firebase Auth UID (UID-first contract).
                navArgument("driverUid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverUid = backStackEntry.arguments?.getString("driverUid") ?: return@composable
            val vm: com.drivemate.showcase.feature.expat.review.ExpatMonthlyReviewViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.review.ExpatMonthlyReviewScreen(
                    vm = vm,
                    driverUid = driverUid,
                    onBack = { navController.popBackStack() },
                    onOpenWorkdayDetail = { id ->
                        navController.navigate(NavRoutes.ExpatWorkdayDetail.build(id))
                    }
                )
            }
        }

        composable(
            route = NavRoutes.ExpatWorkdayDetail.route,
            arguments = listOf(navArgument("workdayId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workdayId = backStackEntry.arguments?.getString("workdayId") ?: return@composable
            val vm: com.drivemate.showcase.feature.expat.review.ExpatWorkdayDetailViewModel = koinViewModel()
            com.drivemate.showcase.feature.common.SessionGuard(navController, required = Role.EXPAT) {
                com.drivemate.showcase.feature.expat.review.ExpatWorkdayDetailScreen(
                    vm = vm,
                    workdayId = workdayId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}


@Composable
private fun rememberAttendanceActions(
    vm: com.drivemate.showcase.feature.driver.DriverViewModel,
    context: android.content.Context
): AttendanceActions {
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        DMLog.d("DM/Perm", "Location permission result: granted=$granted")
        scope.launch {
            val seed = if (granted) getLastKnownLatLngOrNull(context) else null
            vm.doTapIn(seed, permissionGranted = granted)
        }
    }
    val tapOutPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        DMLog.d("DM/Perm", "Location permission result (tap out): granted=$granted")
        scope.launch {
            val seed = if (granted) getLastKnownLatLngOrNull(context) else null
            vm.doTapOut(seed, permissionGranted = granted)
        }
    }

    val onTapIn: () -> Unit = {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            scope.launch {
                val seed = getLastKnownLatLngOrNull(context)
                vm.doTapIn(seed, permissionGranted = true)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val onTapOut: () -> Unit = {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            scope.launch {
                val seed = getLastKnownLatLngOrNull(context)
                vm.doTapOut(seed, permissionGranted = true)
            }
        } else {
            tapOutPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    return AttendanceActions(onTapIn = onTapIn, onTapOut = onTapOut)
}
