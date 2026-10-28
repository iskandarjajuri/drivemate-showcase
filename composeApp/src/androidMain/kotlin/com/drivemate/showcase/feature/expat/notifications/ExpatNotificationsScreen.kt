package com.drivemate.showcase.feature.expat.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import com.drivemate.showcase.ui.dsv2.DmIconButton
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.navigation.floatingNavContentPadding
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.dmClickable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Test tag layar Notifikasi Expat (konvensi `dm_expat_...`). */
object ExpatNotificationTags {
    const val LIST = "dm_expat_notifications_list"
}

@Composable
fun ExpatNotificationsScreen(
    vm: ExpatNotificationsViewModel,
    onRetry: () -> Unit,
    onOpenReview: (driverUid: String) -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenChats: () -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Notifikasi sistem dimatikan: tampilkan petunjuk ke pengaturan; dihitung ulang saat RESUMED.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startOfToday = remember(state.items) {
        val zone = TimeZone.of("Asia/Jakarta")
        Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(zone).date
            .atStartOfDayIn(zone).toEpochMilliseconds()
    }
    val today = state.items.filter { it.isToday(startOfToday) }
    val earlier = state.items.filterNot { it.isToday(startOfToday) }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.expat_notifications_title),
                onBack = onBack,
                actions = {
                    // Pintu masuk Pengumuman yang jelas di header (menggantikan tautan menggantung di dasar daftar).
                    DmIconButton(
                        onClick = onOpenAnnouncements,
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = stringResource(R.string.expat_notifications_see_all_announcements)
                    )
                    TextButton(onClick = vm::markAllRead, enabled = state.unreadCount > 0) {
                        Text(stringResource(R.string.expat_notifications_mark_all_read))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics { testTagsAsResourceId = true }
                .testTag(ExpatNotificationTags.LIST),
            contentPadding = floatingNavContentPadding(PaddingValues(Dsv2Spacing.md)),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
        ) {
            if (state.partialError) {
                item {
                    InlineAlert(
                        kind = InlineAlertKind.Warning,
                        message = stringResource(R.string.expat_state_partial_error),
                        actionText = stringResource(R.string.expat_state_error_retry),
                        onAction = onRetry
                    )
                }
            }
            if (!notificationsEnabled) {
                item {
                    InlineAlert(
                        kind = InlineAlertKind.Warning,
                        message = stringResource(R.string.expat_notifications_disabled_hint),
                        actionText = stringResource(R.string.expat_notifications_open_settings),
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                }
            }
            if (state.items.isEmpty()) {
                item {
                    DmEmptyState(
                        title = stringResource(R.string.expat_notifications_empty_title),
                        message = stringResource(R.string.expat_notifications_empty_message)
                    )
                }
            }
            section(today, R.string.expat_notifications_today, onOpenReview, onOpenAnnouncements, onOpenChats)
            section(earlier, R.string.expat_notifications_earlier, onOpenReview, onOpenAnnouncements, onOpenChats)
        }
    }
    }
}

private fun LazyListScope.section(
    list: List<ExpatNotification>,
    titleRes: Int,
    onOpenReview: (String) -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenChats: () -> Unit
) {
    if (list.isEmpty()) return
    item(key = "h$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dsv2Spacing.sm)
        )
    }
    items(list, key = { it.id }) { n ->
        NotificationRow(
            n = n,
            onClick = when (n.kind) {
                ExpatNotificationKind.REVIEW -> {
                    { n.driverUid?.let { uid -> onOpenReview(uid) }; Unit }
                }
                ExpatNotificationKind.ANNOUNCEMENT -> onOpenAnnouncements
                ExpatNotificationKind.MESSAGE -> onOpenChats
            }
        )
    }
}

@Composable
private fun NotificationRow(n: ExpatNotification, onClick: () -> Unit) {
    val text = when (n.kind) {
        ExpatNotificationKind.REVIEW -> pluralStringResource(R.plurals.expat_notifications_pending_format, n.count, n.text, n.count)
        ExpatNotificationKind.ANNOUNCEMENT -> stringResource(R.string.expat_notifications_announcement_format, n.text)
        ExpatNotificationKind.MESSAGE -> pluralStringResource(R.plurals.expat_notifications_chat_format, n.count, n.count)
    }
    val (icon, base) = rowVisual(n)
    val unreadLabel = stringResource(R.string.expat_notifications_unread)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            // Belum dibaca: bilah aksen di tepi kiri + teks tebal (bukan hanya titik kecil).
            .drawBehind {
                if (n.unread) {
                    drawRoundRect(
                        color = accent,
                        size = Size(4.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
            .dmClickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = if (n.unread) "$unreadLabel. $text" else text
            }
            .padding(start = Dsv2Spacing.sm, top = Dsv2Spacing.xs, bottom = Dsv2Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
    ) {
        Dsv2Badge3D(icon = icon, start = base, end = base.copy(alpha = 0.78f), size = 44.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 6, // font 2,0x: tiga baris memotong kalimat ("…あ…") di A52s
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun rowVisual(n: ExpatNotification): Pair<ImageVector, Color> = when (n.kind) {
    ExpatNotificationKind.REVIEW -> Icons.Outlined.CheckCircle to AppPalette.warning
    ExpatNotificationKind.ANNOUNCEMENT -> Icons.Outlined.Campaign to AppPalette.info
    ExpatNotificationKind.MESSAGE -> Icons.AutoMirrored.Outlined.Chat to AppPalette.primary
}
