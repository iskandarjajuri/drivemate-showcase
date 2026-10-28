package com.drivemate.showcase.feature.expat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.drivemate.showcase.R
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.feature.driver.notifications.formatNotificationBadge
import com.drivemate.showcase.feature.expat.home.ExpatAttentionCard
import com.drivemate.showcase.feature.expat.home.ExpatHeaderAvatar
import com.drivemate.showcase.feature.expat.home.ExpatHeroCard
import com.drivemate.showcase.feature.expat.home.ExpatQuickTiles
import com.drivemate.showcase.feature.expat.home.ExpatSummaryStrip
import com.drivemate.showcase.feature.expat.home.expatStaggerIn
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.navigation.floatingNavContentPadding
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.ds.DashboardTile
import com.drivemate.showcase.ui.ds.DmLink
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.DmElevatedCard
import com.drivemate.showcase.ui.dsv2.DmIconButton
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.ui.dsv2.DmTonalCard
import com.drivemate.showcase.ui.dsv2.Dsv2PremiumPrimaryButton
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.domain.admin.GreetingPeriod
import com.drivemate.showcase.domain.admin.resolveGreetingPeriod
import kotlinx.datetime.TimeZone

@Composable
fun ExpatHomeScreen(
    vm: ExpatHomeViewModel,
    /** Tidak lagi dipakai di beranda: keluar akun kini lewat pengaturan akun. */
    @Suppress("UNUSED_PARAMETER") onLogout: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenMonthlyReview: (driverUid: String) -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenChat: () -> Unit,
    onContactAdmin: () -> Unit = {},
    /** Total pesan chat belum dibaca; badge di tile Chat. */
    chatUnread: Int = 0,
    onOpenNotifications: () -> Unit = {},
    /** Notifikasi belum dibaca untuk badge lonceng di header. */
    notificationUnread: Int = 0,
    /** Judul pengumuman terbaru (null = belum ada); dari sumber yang sama dengan tab Notifikasi. */
    latestAnnouncementTitle: String? = null,
    /** Tile "Tinjau" membuka tab Tinjau; null = tile tidak melakukan apa-apa (mis. pratinjau/tes). */
    onOpenReviewQueue: (() -> Unit)? = null,
    /** Tile "Driver" membuka tab Driver. */
    onOpenDrivers: (() -> Unit)? = null
) {
    val loading by vm.loading.collectAsState()
    val errorRes by vm.error.collectAsState()
    val drivers by vm.drivers.collectAsState()
    val header by vm.header.collectAsState()
    val derived by vm.derived.collectAsState()
    val greetingName by vm.greetingName.collectAsState()
    // Hitung naik angka hero dan animasi masuk berurutan: sekali saja per entri Beranda (bertahan saat pindah tab).
    var countUpPlayed by rememberSaveable { mutableStateOf(false) }
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }

    val placeholderDash = stringResource(R.string.expat_placeholder_dash)
    val zone = TimeZone.of("Asia/Jakarta")
    val lastRefreshText = header.lastRefreshMillis.takeIf { it > 0L }?.let {
        TimeUtil.formatHm(it, zone)
    } ?: placeholderDash

    // Jam dibaca dari formatHm (HH:mm) agar tidak menambah impor waktu di layar.
    val hour = TimeUtil.formatHm(TimeUtil.nowEpochMillis(), zone).take(2).toIntOrNull() ?: 0
    val name = greetingName.orEmpty()
    val greetingPeriod = resolveGreetingPeriod(hour)
    // Nama kosong: sapaan netral tanpa nama (bukan "Selamat pagi, " dengan koma menggantung).
    val greeting = if (name.isBlank()) {
        stringResource(
            when (greetingPeriod) {
                GreetingPeriod.MORNING -> R.string.expat_home_header_greeting_morning_anon
                GreetingPeriod.AFTERNOON -> R.string.expat_home_header_greeting_afternoon_anon
                else -> R.string.expat_home_header_greeting_evening_anon
            }
        )
    } else {
        when (greetingPeriod) {
            GreetingPeriod.MORNING -> stringResource(R.string.expat_home_header_greeting_morning, name)
            GreetingPeriod.AFTERNOON -> stringResource(R.string.expat_home_header_greeting_afternoon, name)
            else -> stringResource(R.string.expat_home_header_greeting_evening, name)
        }
    }

    // Animasi masuk berurutan dimainkan sekali saat data pertama tersedia, lalu dimatikan untuk kunjungan berikutnya.
    val contentAvailable = !(drivers.isEmpty() && (loading || errorRes != null))
    LaunchedEffect(contentAvailable) {
        if (contentAvailable && !entrancePlayed) {
            kotlinx.coroutines.delay(600)
            entrancePlayed = true
        }
    }

    LaunchedEffect(Unit) { vm.loadDrivers() }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        // Flag ini harus di Scaffold, bukan di LazyColumn: lonceng dan avatar ada di slot topBar,
        // subtree yang terpisah dari konten, sehingga tag-nya tak sampai ke uiautomator/Maestro.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = Color.Transparent,
        topBar = {
            DmScreenHeader(
                title = greeting,
                subtitle = stringResource(R.string.expat_summary_subtitle, lastRefreshText),
                titleMaxLines = 3,
                subtitleMaxLines = 2,
                actions = {
                    Box {
                        DmIconButton(
                            onClick = onOpenNotifications,
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = formatNotificationBadge(notificationUnread)?.let {
                                stringResource(R.string.expat_home_header_bell_cd_count_format, notificationUnread)
                            } ?: stringResource(R.string.expat_home_header_bell_cd),
                            modifier = Modifier.testTag(ExpatMaestroTags.HOME_BELL)
                        )
                        formatNotificationBadge(notificationUnread)?.let { text ->
                            Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text(text) }
                        }
                    }
                    ExpatHeaderAvatar(
                        name = greetingName,
                        onClick = onOpenAccount,
                        contentDescription = stringResource(R.string.expat_home_header_avatar_cd),
                        modifier = Modifier.testTag(ExpatMaestroTags.HOME_AVATAR)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(ExpatMaestroTags.HOME_ROOT)
                .padding(padding)
                .padding(horizontal = AppSpacing.md),
            contentPadding = floatingNavContentPadding(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            if (errorRes != null) {
                item {
                    InlineAlert(
                        kind = InlineAlertKind.Error,
                        message = errorRes?.let { stringResource(it) }.orEmpty(),
                        actionText = stringResource(R.string.expat_action_retry),
                        onAction = { vm.loadDrivers() }
                    )
                }
            }

            if (drivers.isEmpty() && !loading && errorRes == null) {
                item {
                    InlineAlert(
                        kind = InlineAlertKind.Info,
                        message = stringResource(R.string.expat_empty_managed_drivers),
                        actionText = stringResource(R.string.expat_action_contact_admin),
                        onAction = onContactAdmin
                    )
                }
            }

            // Belum ada data dan sedang memuat/gagal: kartu di bawah akan menampilkan "semua selesai, 0 menunggu",
            // yang menyesatkan bagi penyetuju. Terbukti di A52s offline sebelum perbaikan ini.
            val dataUnavailable = drivers.isEmpty() && (loading || errorRes != null)
            if (dataUnavailable) {
                if (loading && errorRes == null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            repeat(3) { DmSkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 96.dp)) }
                        }
                    }
                }
            } else {
                item(key = "hero") {
                    ExpatHeroCard(
                        pendingTotal = header.pendingTotal,
                        reviewedTotal = derived.reviewedTotal,
                        countUpPending = !countUpPlayed,
                        onCountUpFinished = { countUpPlayed = true },
                        onStart = derived.topPendingDriver?.let { top -> { onOpenMonthlyReview(top.uid) } },
                        modifier = Modifier.expatStaggerIn(0, play = !entrancePlayed)
                    )
                }

                item(key = "summary") {
                    ExpatSummaryStrip(
                        totalDrivers = header.totalDrivers,
                        pendingTotal = header.pendingTotal,
                        reviewedTotal = derived.reviewedTotal,
                        modifier = Modifier.expatStaggerIn(1, play = !entrancePlayed)
                    )
                }

                item(key = "attention") {
                    ExpatAttentionCard(
                        attention = derived.attention,
                        onOpenDriver = onOpenMonthlyReview,
                        modifier = Modifier.expatStaggerIn(2, play = !entrancePlayed)
                    )
                }

                item(key = "tiles") {
                    ExpatQuickTiles(
                        pendingTotal = header.pendingTotal,
                        chatUnread = chatUnread,
                        onReview = { onOpenReviewQueue?.invoke() },
                        onDrivers = { onOpenDrivers?.invoke() },
                        onMessages = onOpenChat,
                        onAnnouncements = onOpenAnnouncements,
                        modifier = Modifier.expatStaggerIn(3, play = !entrancePlayed)
                    )
                }
            }

            item {
                // Hanya tautan: pengumuman terbaru tidak dimuat di beranda (tanpa panggilan baru).
                DmElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ExpatMaestroTags.HOME_ANNOUNCEMENT)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.expat_home_announcement_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        DmLink(
                            text = stringResource(R.string.expat_home_announcement_see_all),
                            onClick = onOpenAnnouncements
                        )
                    }
                    Text(
                        text = latestAnnouncementTitle ?: stringResource(R.string.expat_home_announcement_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (latestAnnouncementTitle != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
            }

        }
    }
    }
}
