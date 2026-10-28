package com.drivemate.showcase.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.drivemate.showcase.R
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.feature.driver.DriverDailyStatus
import com.drivemate.showcase.feature.driver.DriverMaestroTags
import com.drivemate.showcase.feature.driver.DriverTugasCelebrationStore
import com.drivemate.showcase.feature.driver.DriverTugasNavState
import com.drivemate.showcase.feature.driver.DriverUiState
import com.drivemate.showcase.feature.driver.DriverViewModel
import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge
import com.drivemate.showcase.feature.driver.notifications.unseenWorkUpdates
import com.drivemate.showcase.feature.driver.toTugasNavState
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.DmPressLayerAlpha
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.dsv2.rememberDmTabPopScale
import com.drivemate.showcase.ui.LocalReduceMotion
import kotlinx.datetime.TimeZone
import org.koin.androidx.compose.koinViewModel

private enum class DriverNavTab(
    val route: String,
    val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    HOME(NavRoutes.DriverHome.route, R.string.nav_driver_home, Icons.Filled.Home, Icons.Outlined.Home),
    PRESENSI(NavRoutes.DriverAttendance.route, R.string.nav_driver_attendance, Icons.Filled.Schedule, Icons.Outlined.Schedule),
    TUGAS(NavRoutes.DriverTugas.route, R.string.nav_driver_tasks, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    PESAN(NavRoutes.DriverChats.route, R.string.nav_driver_messages, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    RIWAYAT(NavRoutes.DriverHistory.route, R.string.nav_driver_history, Icons.Filled.History, Icons.Outlined.History)
}

internal val driverFloatingNavRoutes = DriverNavTab.entries.map { it.route }.toSet()

private val IndicatorWidth = 56.dp
private val IndicatorHeight = 32.dp
private val BarHeight = 64.dp
private val CollapsedBarHeight = 52.dp
private val CollapsedHorizontalMargin = 34.dp
private val BarCornerRadius = 24.dp
private val BarMargin = 12.dp
private val RingStroke = 2.5.dp
private val RingSize = 22.dp

/**
 * Driver-only floating bottom nav (menggantikan [RoleBottomBar]/`Dsv2BottomBar`
 * untuk role DRIVER). Admin memakai AdminFloatingNavBar (5 tab + Lainnya),
 * Expat tetap memakai [RoleBottomBar] -- lihat pemanggil di
 * `DriveMateRootScaffold.kt`.
 *
 * Membagi instance [DriverViewModel] yang SAMA dengan Driver Home/Presensi/Tugas
 * (`viewModelStoreOwner = getBackStackEntry(DriverHome)`, pola yang sama dipakai
 * `AppNavGraph.kt` untuk DriverAttendance/DriverTugas) -- bukan instance baru dari
 * `koinViewModel()` polos. Bar ini hidup di luar back-stack entry manapun (di
 * `DriveMateRootScaffold`, yang membungkus seluruh NavHost), jadi tanpa ini ia akan
 * membuat listener Firestore keduanya sendiri dan progress ring Tugas tidak akan
 * bergerak setelah checklist/safety disubmit sampai layar di-restart.
 */
@Composable
fun DriverFloatingNavBar(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    if (currentRoute == null || currentRoute !in driverFloatingNavRoutes) return

    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(NavRoutes.DriverHome.route)
    }
    val vm: DriverViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
    // Sama dengan instans di entri DriverHome (dipakai juga Notifikasi): satu sumber badge Pesan.
    val chatUnreadVm: com.drivemate.showcase.feature.chat.ChatUnreadViewModel =
        koinViewModel(viewModelStoreOwner = parentEntry)
    val chatUnread by chatUnreadVm.unread.collectAsState()
    // Pembaruan pekerjaan belum dilihat (pengumuman + keputusan persetujuan) dari VM Notifikasi yang sama (entri DriverHome).
    val notificationsVm: com.drivemate.showcase.feature.driver.notifications.DriverNotificationsViewModel =
        koinViewModel(viewModelStoreOwner = parentEntry)
    val notificationsState by notificationsVm.state.collectAsState()
    val updatesUnseen = notificationsState.items.unseenWorkUpdates()
    val uiState by vm.state.collectAsState()
    val homeState by vm.homeState.collectAsState()

    val dailyStatus = DriverDailyStatus(
        sopAccepted = homeState.sopAccepted == true,
        tappedIn = uiState is DriverUiState.Working,
        checklistDone = homeState.checklistSubmitted == true,
        safetyDone = homeState.safetySubmitted == true
    )
    val tugasState = dailyStatus.toTugasNavState()
    val tappedIn = uiState is DriverUiState.Working

    val reduceMotion = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val celebrationStore = remember { DriverTugasCelebrationStore(context) }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(tugasState.isComplete) {
        if (!tugasState.isComplete) return@LaunchedEffect
        val todayEpochDay = TimeUtil.todayEpochDay(TimeZone.of("Asia/Jakarta"))
        if (celebrationStore.shouldCelebrateToday(todayEpochDay, isComplete = true)) {
            celebrationStore.markCelebrated(todayEpochDay)
            celebrate = true
            if (!reduceMotion) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    DriverFloatingNavBarContent(
        currentRoute = currentRoute,
        tugasState = tugasState,
        tappedIn = tappedIn,
        chatUnread = chatUnread,
        updatesUnseen = updatesUnseen,
        celebrate = celebrate,
        onCelebrationPlayed = { celebrate = false },
        onTabSelected = { route ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            navController.navigateToBottomTab(route, NavRoutes.DriverHome.route)
        },
        modifier = modifier
    )
}

/**
 * Presentational, stateless -- tidak menyentuh Koin/NavController, jadi bisa
 * dites langsung dengan `createComposeRule()` seperti komponen driver lain
 * (lihat `DriverFlowObservabilityTest`). [DriverFloatingNavBar] di atas adalah
 * satu-satunya pemanggil di app; wiring ViewModel/NavController-nya ada di sana.
 */
@Composable
fun DriverFloatingNavBarContent(
    currentRoute: String,
    tugasState: DriverTugasNavState,
    tappedIn: Boolean,
    celebrate: Boolean,
    onCelebrationPlayed: () -> Unit,
    onTabSelected: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Total pesan chat belum dibaca; badge di tab Pesan (0 = tanpa badge). */
    chatUnread: Int = 0,
    /** Pembaruan pekerjaan belum dilihat (pengumuman/keputusan persetujuan); badge di tab Beranda. */
    updatesUnseen: Int = 0
) {
    val reduceMotion = LocalReduceMotion.current
    val selectedIndex = DriverNavTab.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    FloatingNavBarShell(
        itemCount = DriverNavTab.entries.size,
        selectedIndex = selectedIndex,
        barTestTag = DriverMaestroTags.FLOATING_NAV_BAR,
        modifier = modifier
    ) { index, selected, interactionSource ->
        val tab = DriverNavTab.entries[index]
                    val label = stringResource(tab.labelRes)
                    val activeSuffix = stringResource(R.string.driver_nav_presensi_active_suffix)
                    val tugasProgress = stringResource(
                        R.string.driver_tugas_progress_format,
                        tugasState.doneCount,
                        tugasState.totalCount
                    )
                    val itemDescription = when (tab) {
                        DriverNavTab.TUGAS -> "$label, $tugasProgress"
                        DriverNavTab.PRESENSI -> if (tappedIn) "$label, $activeSuffix" else label
                        DriverNavTab.HOME -> if (updatesUnseen > 0) {
                            "$label, " + pluralStringResource(R.plurals.driver_nav_updates_unseen, updatesUnseen, updatesUnseen)
                        } else label
                        DriverNavTab.PESAN -> if (chatUnread > 0) {
                            "$label, " + pluralStringResource(R.plurals.chat_unread_messages, chatUnread, chatUnread)
                        } else label
                        else -> label
                    }

                    val popScale = rememberDmTabPopScale(selected, index)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .dmClickable(
                                onClick = {
                                    if (!selected) {
                                        onTabSelected(tab.route)
                                    }
                                },
                                kind = DmPressKind.Tab,
                                interactionSource = interactionSource
                            )
                            .semantics { contentDescription = itemDescription }
                            .testTag(DriverMaestroTags.floatingNavItemTag(tab.route)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (tab) {
                            DriverNavTab.TUGAS -> TugasNavIcon(
                                state = tugasState,
                                selected = selected,
                                celebrate = celebrate,
                                reduceMotion = reduceMotion,
                                onCelebrationPlayed = onCelebrationPlayed
                            )
                            DriverNavTab.PRESENSI -> PresensiNavIcon(
                                icon = if (selected) tab.filledIcon else tab.outlinedIcon,
                                selected = selected,
                                tappedIn = tappedIn,
                                modifier = Modifier.scale(popScale)
                            )
                            DriverNavTab.HOME -> BadgedBox(
                                badge = {
                                    formatNotificationBadge(updatesUnseen)?.let { Badge { Text(it) } }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.scale(popScale)
                                )
                            }
                            DriverNavTab.PESAN -> BadgedBox(
                                badge = {
                                    formatNotificationBadge(chatUnread)?.let { Badge { Text(it) } }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.scale(popScale)
                                )
                            }
                            else -> Icon(
                                imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.scale(popScale)
                            )
                        }
                        if (!LocalFloatingNavCollapsed.current) Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
        }
    }

/**
 * Cangkang nav bar mengambang yang dipakai bersama (role-agnostic): surface
 * membulat dengan margin, indikator beranimasi (hormat terhadap reduced
 * motion), dan baris item ber-berat sama. Dipakai oleh
 * [DriverFloatingNavBarContent] (driver) dan AdminFloatingNavBarContent (admin)
 * supaya kedua role punya satu implementasi chrome yang sama -- bukan fork.
 *
 * Tiap item menerima [MutableInteractionSource]-nya sendiri: skala 0,9 tab
 * diberikan dmClickable (DmPressKind.Tab), dan state layer tekan digambar DI
 * SINI pada pil indikator (bukan pada kolom item) sesuai spesifikasi UX v3.
 */
@Composable
internal fun FloatingNavBarShell(
    itemCount: Int,
    selectedIndex: Int,
    barTestTag: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(index: Int, selected: Boolean, interactionSource: MutableInteractionSource) -> Unit
) {
    require(itemCount > 0) { "itemCount harus > 0" }
    val reduceMotion = LocalReduceMotion.current

    // Satu interaction source per item, dibuat di sini supaya pil bisa membaca
    // status "ditekan" dari item terpilih.
    val interactionSources = remember { List(itemCount) { MutableInteractionSource() } }
    val pillSource = interactionSources.getOrNull(selectedIndex)
    val pillPressed = pillSource?.collectIsPressedAsState()?.value ?: false

    val pillColor = MaterialTheme.colorScheme.primaryContainer
    val pillLayerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = DmPressLayerAlpha)

    val collapsed = LocalFloatingNavCollapsed.current
    val spec = if (reduceMotion) snap<Dp>() else tween<Dp>(durationMillis = 280, easing = FastOutSlowInEasing)
    val barHeight by animateDpAsState(if (collapsed) CollapsedBarHeight else BarHeight, spec, label = "navBarHeight")
    val hMargin by animateDpAsState(if (collapsed) CollapsedHorizontalMargin else BarMargin, spec, label = "navBarMargin")
    val scaleSpec = if (reduceMotion) snap<Float>() else tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    val barScale by animateFloatAsState(if (collapsed && !reduceMotion) 0.9f else 1f, scaleSpec, label = "navBarScale")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = hMargin, vertical = BarMargin)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .height(barHeight)
            .testTag(barTestTag),
        shape = RoundedCornerShape(BarCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / itemCount
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex + (itemWidth - IndicatorWidth) / 2,
                animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.7f),
                label = "navIndicatorOffset"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorOffset)
                    .size(width = IndicatorWidth, height = IndicatorHeight)
                    .drawBehind {
                        drawRoundRect(
                            color = pillColor,
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                        // State layer bundar opasitas 10% saat tab terpilih ditekan.
                        if (pillPressed) {
                            drawCircle(color = pillLayerColor, radius = size.minDimension / 2f)
                        }
                    }
            )

            Row(modifier = Modifier.fillMaxSize()) {
                for (index in 0 until itemCount) {
                    content(index, index == selectedIndex, interactionSources[index])
                }
            }
        }
    }
}

@Composable
private fun PresensiNavIcon(
    icon: ImageVector,
    selected: Boolean,
    tappedIn: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (tappedIn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .background(AppPalette.success, CircleShape)
            )
        }
    }
}

@Composable
private fun TugasNavIcon(
    state: DriverTugasNavState,
    selected: Boolean,
    celebrate: Boolean,
    reduceMotion: Boolean,
    onCelebrationPlayed: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (celebrate) 1.3f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tugasCheckScale",
        finishedListener = { if (celebrate) onCelebrationPlayed() }
    )
    val ringColor = if (state.isComplete) AppPalette.success else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val progressAnim by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "tugasRingProgress"
    )

    BadgedBox(
        badge = {
            if (!state.isComplete && state.remainingCount > 0) {
                Badge { Text(state.remainingCount.toString()) }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .size(RingSize)
                .drawBehind {
                    val stroke = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round)
                    drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                    drawArc(color = ringColor, startAngle = -90f, sweepAngle = 360f * progressAnim, useCenter = false, style = stroke)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.isComplete) Icons.Filled.CheckCircle else Icons.AutoMirrored.Outlined.Assignment,
                contentDescription = null,
                tint = if (state.isComplete) {
                    AppPalette.success
                } else if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(16.dp)
                    .scale(if (state.isComplete) scale else 1f)
            )
        }
    }
}
