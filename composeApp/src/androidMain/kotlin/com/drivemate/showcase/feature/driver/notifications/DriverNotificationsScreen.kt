package com.drivemate.showcase.feature.driver.notifications

import androidx.compose.runtime.DisposableEffect
import android.provider.Settings
import android.content.Intent
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.driver.DriverMaestroTags
import com.drivemate.showcase.feature.driver.DriverEmptyState
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.dmClickable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn

@Composable
fun DriverNotificationsScreen(
    vm: DriverNotificationsViewModel,
    onBack: () -> Unit,
    onOpenTugas: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChats: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Izin POST_NOTIFICATIONS ditolak / notifikasi dimatikan: tampilkan petunjuk ke pengaturan sistem.
    // Dihitung ulang saat layar kembali RESUMED (pengguna baru mengubahnya di pengaturan).
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var filter by rememberSaveable { mutableStateOf(DriverNotificationFilter.ALL) }
    val visible = remember(state.items, filter) { state.items.filteredBy(filter) }

    val startOfToday = remember(state.items) {
        val zone = TimeZone.of("Asia/Jakarta")
        Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(zone).date.atStartOfDayIn(zone).toEpochMilliseconds()
    }
    val today = visible.filter { it.isToday(startOfToday) }
    val earlier = visible.filterNot { it.isToday(startOfToday) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_notifications_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = vm::markAllRead, enabled = state.unreadCount > 0) {
                        Text(stringResource(R.string.driver_notifications_mark_read))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(DriverMaestroTags.NOTIFICATIONS_LIST),
            contentPadding = PaddingValues(Dsv2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
        ) {
            if (!notificationsEnabled) {
                item {
                    com.drivemate.showcase.ui.ds.InlineAlert(
                        kind = com.drivemate.showcase.ui.ds.InlineAlertKind.Warning,
                        message = stringResource(R.string.driver_notifications_disabled_hint),
                        actionText = stringResource(R.string.driver_notifications_open_settings),
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)) {
                    FilterChipFor(DriverNotificationFilter.ALL, R.string.driver_notifications_filter_all, filter) { filter = it }
                    FilterChipFor(DriverNotificationFilter.TASKS, R.string.driver_notifications_filter_tasks, filter) { filter = it }
                    FilterChipFor(DriverNotificationFilter.INFO, R.string.driver_notifications_filter_info, filter) { filter = it }
                }
            }
            if (visible.isEmpty()) {
                item {
                    DriverEmptyState(
                        title = stringResource(R.string.driver_notifications_empty_title),
                        message = stringResource(R.string.driver_notifications_empty_message)
                    )
                }
            }
            section(today, R.string.driver_notifications_today, onOpenTugas, onOpenAnnouncements, onOpenHistory, onOpenChats)
            section(earlier, R.string.driver_notifications_earlier, onOpenTugas, onOpenAnnouncements, onOpenHistory, onOpenChats)
            item {
                // Jalur ke daftar lengkap; tag lama ANNOUNCEMENTS_ENTRY dipertahankan (flow Maestro).
                TextButton(
                    onClick = onOpenAnnouncements,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DriverMaestroTags.ANNOUNCEMENTS_ENTRY)
                ) {
                    Text(stringResource(R.string.driver_notifications_see_all_announcements))
                }
            }
        }
    }
}

@Composable
private fun FilterChipFor(
    value: DriverNotificationFilter,
    labelRes: Int,
    selected: DriverNotificationFilter,
    onSelect: (DriverNotificationFilter) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) }
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    list: List<DriverNotification>,
    titleRes: Int,
    onOpenTugas: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenHistory: () -> Unit,
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
                DriverNotificationKind.TASK -> onOpenTugas
                DriverNotificationKind.ANNOUNCEMENT -> onOpenAnnouncements
                DriverNotificationKind.APPROVAL -> onOpenHistory
                DriverNotificationKind.MESSAGE -> onOpenChats
            }
        )
    }
}

@Composable
private fun NotificationRow(n: DriverNotification, onClick: () -> Unit) {
    val text = when (n.kind) {
        DriverNotificationKind.TASK -> stringResource(
            when (n.task) {
                DriverPendingTask.SOP -> R.string.driver_notifications_task_sop
                DriverPendingTask.CHECKLIST -> R.string.driver_notifications_task_checklist
                DriverPendingTask.SAFETY, null -> R.string.driver_notifications_task_safety
            }
        )
        DriverNotificationKind.ANNOUNCEMENT -> n.text
        DriverNotificationKind.MESSAGE -> pluralStringResource(R.plurals.chat_unread_messages, n.count, n.count)
        DriverNotificationKind.APPROVAL -> stringResource(
            if (n.rejected) R.string.driver_notifications_rejected_format
            else R.string.driver_notifications_approved_format,
            n.text
        )
    }
    val (icon, base) = rowVisual(n)
    val unreadLabel = stringResource(R.string.driver_notifications_unread)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .dmClickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = if (n.unread) "$unreadLabel. $text" else text
            }
            .padding(vertical = Dsv2Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
    ) {
        Dsv2Badge3D(icon = icon, start = base, end = base.copy(alpha = 0.78f), size = 44.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (n.unread) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

private fun rowVisual(n: DriverNotification): Pair<ImageVector, Color> = when (n.kind) {
    DriverNotificationKind.TASK -> Icons.AutoMirrored.Outlined.Assignment to AppPalette.warning
    DriverNotificationKind.ANNOUNCEMENT -> Icons.Outlined.Campaign to AppPalette.info
    DriverNotificationKind.MESSAGE -> Icons.AutoMirrored.Outlined.Chat to AppPalette.primary
    DriverNotificationKind.APPROVAL ->
        if (n.rejected) Icons.Outlined.ErrorOutline to AppPalette.error
        else Icons.Outlined.CheckCircle to AppPalette.success
}
