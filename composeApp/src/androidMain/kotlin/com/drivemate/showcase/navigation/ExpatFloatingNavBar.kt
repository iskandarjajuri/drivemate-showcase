package com.drivemate.showcase.navigation

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.drivemate.showcase.R
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.feature.expat.ExpatReviewCelebrationStore
import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppRoleColors
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.dsv2.rememberDmTabPopScale
import kotlinx.datetime.TimeZone
import org.koin.androidx.compose.koinViewModel

/** Batas skala font untuk label tab Expat (lima tab sempit). Di bawah batas ini label mengikuti pengaturan sistem. */
internal const val EXPAT_NAV_LABEL_MAX_FONT_SCALE = 1.2f

private val RingStroke = 2.5.dp
private val RingSize = 22.dp

/** Ukuran terkecil label tab (sp, sebelum skala font) saat autoSize menyusutkan label panjang. */
internal const val EXPAT_NAV_LABEL_MIN_FONT_SP = 8

/** Fungsi murni: skala font efektif label tab = skala sistem, dibatasi [EXPAT_NAV_LABEL_MAX_FONT_SCALE]. */
internal fun expatNavLabelFontScale(systemFontScale: Float): Float =
    minOf(systemFontScale, EXPAT_NAV_LABEL_MAX_FONT_SCALE)

private enum class ExpatNavTab(
    val route: String,
    val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    HOME(NavRoutes.ExpatHome.route, R.string.expat_nav_tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    REVIEW(NavRoutes.ExpatReviewQueue.route, R.string.expat_nav_tab_review, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    DRIVERS(NavRoutes.ExpatDrivers.route, R.string.expat_nav_tab_drivers, Icons.Filled.People, Icons.Outlined.People),
    CHATS(NavRoutes.ExpatChats.route, R.string.expat_nav_tab_chats, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    SETTINGS(NavRoutes.ExpatSettings.route, R.string.expat_nav_tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

/** Rute tab bar Expat; dipakai `isFloatingNavRoute` untuk menampilkan bar. */
internal val expatFloatingNavRoutes = ExpatNavTab.entries.map { it.route }.toSet()

/** Test tag bar Expat (konvensi `dm_<peran>_...` seperti Driver/Admin). */
object ExpatNavTags {
    const val FLOATING_NAV_BAR = "dm_expat_floating_nav_bar"
    fun floatingNavItemTag(route: String): String = "dm_expat_nav_$route"
}

/**
 * Nav bar mengambang khusus Expat (menggantikan [RoleBottomBar] untuk peran EXPAT):
 * 5 tab (Beranda, Tinjau, Driver, Pesan, Pengaturan), badge, dan menciut saat gulir lewat
 * [FloatingNavBarShell] yang sama dengan Driver/Admin. Notifikasi BUKAN tab: dibuka dari lonceng
 * di header layar. Badge Pesan memakai [ChatUnreadViewModel] pada entri ExpatHome (satu
 * instans dengan layar Beranda). Cincin + badge Tinjau diturunkan dari angka Beranda yang sudah
 * dimuat (menunggu dan ditinjau), tanpa query baru. Hitungan notifikasi tetap diisi di sini lewat
 * [ExpatNotificationInputsEffect] agar benar pada setiap rute tab.
 */
@Composable
fun ExpatFloatingNavBar(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    if (currentRoute == null || currentRoute !in expatFloatingNavRoutes) return

    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(NavRoutes.ExpatHome.route)
    }
    val chatUnreadVm: com.drivemate.showcase.feature.chat.ChatUnreadViewModel =
        koinViewModel(viewModelStoreOwner = parentEntry)
    val chatUnread by chatUnreadVm.unread.collectAsState()
    // Badge Tinjau = total hari kerja menunggu dari ringkasan Beranda; badge Notifikasi = hitungan belum dibaca
    // VM notifikasi (diisi di sini agar benar sebelum tab Notifikasi dibuka). Instans sama dengan layar (scope ExpatHome).
    val homeVm: com.drivemate.showcase.feature.expat.ExpatHomeViewModel =
        koinViewModel(viewModelStoreOwner = parentEntry)
    val notificationsVm: com.drivemate.showcase.feature.expat.notifications.ExpatNotificationsViewModel =
        koinViewModel(viewModelStoreOwner = parentEntry)
    com.drivemate.showcase.feature.expat.notifications.ExpatNotificationInputsEffect(
        homeVm = homeVm,
        notificationsVm = notificationsVm,
        chatUnread = chatUnread
    )
    val header by homeVm.header.collectAsState()
    val derived by homeVm.derived.collectAsState()
    val drivers by homeVm.drivers.collectAsState()
    val loadError by homeVm.error.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Data tepercaya hanya bila daftar driver sudah ada dan tidak gagal; memuat/gagal tidak boleh tampil "selesai".
    val ring = ExpatReviewRingState(
        pending = header.pendingTotal,
        reviewed = derived.reviewedTotal,
        dataReady = drivers.isNotEmpty() && loadError == null
    )

    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val celebrationStore = remember { ExpatReviewCelebrationStore(context) }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(ring.isComplete) {
        if (!ring.isComplete) return@LaunchedEffect
        val todayEpochDay = TimeUtil.todayEpochDay(TimeZone.of("Asia/Jakarta"))
        if (celebrationStore.shouldCelebrateToday(todayEpochDay, isComplete = true)) {
            celebrationStore.markCelebrated(todayEpochDay)
            celebrate = true
            if (!reduceMotion) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    ExpatFloatingNavBarContent(
        currentRoute = currentRoute,
        reviewRing = ring,
        chatUnread = chatUnread,
        celebrate = celebrate,
        onCelebrationPlayed = { celebrate = false },
        onTabSelected = { route ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            navController.navigateToBottomTab(route, NavRoutes.ExpatHome.route)
        },
        modifier = modifier
    )
}

/** Presentational, stateless -- bisa dites langsung dengan `createComposeRule()`. */
@Composable
internal fun ExpatFloatingNavBarContent(
    currentRoute: String,
    onTabSelected: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Cincin dan badge tab Tinjau (menunggu / ditinjau). */
    reviewRing: ExpatReviewRingState = ExpatReviewRingState(pending = 0, reviewed = 0, dataReady = false),
    /** Total pesan chat belum dibaca (0 = tanpa badge). */
    chatUnread: Int = 0,
    /** Putar animasi centang sekali (semua sudah ditinjau); dipanggil balik setelah selesai. */
    celebrate: Boolean = false,
    onCelebrationPlayed: () -> Unit = {}
) {
    val selectedIndex = ExpatNavTab.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    FloatingNavBarShell(
        itemCount = ExpatNavTab.entries.size,
        selectedIndex = selectedIndex,
        barTestTag = ExpatNavTags.FLOATING_NAV_BAR,
        // Bar adalah saudara (bukan turunan) akar layar, jadi tag-nya perlu flag resource-id sendiri.
        modifier = modifier.semantics { testTagsAsResourceId = true }
    ) { index, selected, interactionSource ->
        val tab = ExpatNavTab.entries[index]
        val label = stringResource(tab.labelRes)
        val count = if (tab == ExpatNavTab.CHATS) chatUnread else 0
        val itemDescription = when {
            tab == ExpatNavTab.REVIEW -> when (reviewRing.descriptionKind) {
                ExpatReviewDescriptionKind.LABEL_ONLY -> label
                ExpatReviewDescriptionKind.PROGRESS -> stringResource(
                    R.string.expat_nav_review_cd_progress_format,
                    label,
                    reviewRing.pendingCount,
                    reviewRing.reviewedCount,
                    reviewRing.total
                )
                ExpatReviewDescriptionKind.COMPLETE -> stringResource(
                    R.string.expat_nav_review_cd_complete_format,
                    label,
                    reviewRing.total
                )
            }
            count > 0 -> stringResource(
                R.string.expat_nav_badge_cd_format,
                label,
                stringResource(R.string.expat_nav_badge_unread_format, count)
            )
            else -> label
        }

        val popScale = rememberDmTabPopScale(selected, index)
        // Ikon terpilih memakai aksen Expat (#0891B2, cukup 3:1 untuk ikon); label tetap onSurface
        // agar kontras teks >= 4,5:1 (aksen di atas putih hanya ~3,7:1).
        val tint = if (selected) AppRoleColors.expat else MaterialTheme.colorScheme.onSurfaceVariant
        val labelColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .dmClickable(
                    onClick = { if (!selected) onTabSelected(tab.route) },
                    kind = DmPressKind.Tab,
                    interactionSource = interactionSource
                )
                .semantics { contentDescription = itemDescription }
                .testTag(ExpatNavTags.floatingNavItemTag(tab.route)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (tab == ExpatNavTab.REVIEW) {
                ExpatReviewNavIcon(
                    ring = reviewRing,
                    selected = selected,
                    celebrate = celebrate,
                    onCelebrationPlayed = onCelebrationPlayed,
                    popScale = popScale
                )
            } else {
                BadgedBox(
                    badge = {
                        formatNotificationBadge(count)?.let { Badge { Text(it) } }
                    }
                ) {
                    Icon(
                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.scale(popScale)
                    )
                }
            }
            if (!LocalFloatingNavCollapsed.current) {
                // Lima tab sempit: skala font label dibatasi agar label Jepang tidak terpotong pada font 2,0x.
                // Label penuh tetap dibacakan TalkBack lewat contentDescription tab.
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, expatNavLabelFontScale(density.fontScale))
                ) {
                    // autoSize: label terpanjang (EN "Notifications") menyusut sampai muat, bukan terpotong "Notificatio…".
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = EXPAT_NAV_LABEL_MIN_FONT_SP.sp,
                            maxFontSize = MaterialTheme.typography.labelSmall.fontSize
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = labelColor
                    )
                }
            }
        }
    }
}

/**
 * Ikon tab Tinjau: cincin progres (ditinjau / total) + badge sisa menunggu. Selesai (data tepercaya, tak ada
 * yang menunggu): hijau sukses + centang, skala sekali dan haptik sukses (sekali per hari, diputuskan pemanggil).
 * Statusnya tidak hanya warna: badge angka, ikon centang penuh, dan deskripsi aksesibilitas gabungan.
 * Semua gerak mengikuti [LocalReduceMotion] (snap).
 */
@Composable
private fun ExpatReviewNavIcon(
    ring: ExpatReviewRingState,
    selected: Boolean,
    celebrate: Boolean,
    onCelebrationPlayed: () -> Unit,
    popScale: Float
) {
    val reduceMotion = LocalReduceMotion.current
    val checkScale by animateFloatAsState(
        targetValue = if (celebrate) 1.3f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "expatReviewCheckScale",
        finishedListener = { if (celebrate) onCelebrationPlayed() }
    )
    val progressAnim by animateFloatAsState(
        targetValue = ring.progress,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "expatReviewRingProgress"
    )
    val ringColor = if (ring.isComplete) AppPalette.success else AppRoleColors.expat
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val iconTint = when {
        ring.isComplete -> AppPalette.success
        selected -> AppRoleColors.expat
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    BadgedBox(
        badge = {
            ring.badgeText?.let { Badge { Text(it) } }
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
                imageVector = if (ring.isComplete || selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(16.dp)
                    .scale(popScale * (if (ring.isComplete) checkScale else 1f))
            )
        }
    }
}
