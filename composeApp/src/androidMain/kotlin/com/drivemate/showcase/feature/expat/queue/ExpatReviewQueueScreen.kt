package com.drivemate.showcase.feature.expat.queue

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.drivemate.showcase.feature.expat.status.ExpatApprovalPill
import com.drivemate.showcase.feature.expat.ja.expatDateLabel
import com.drivemate.showcase.feature.expat.ja.rememberExpatLocale
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.navigation.floatingNavContentPadding
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmErrorState
import com.drivemate.showcase.feature.expat.ExpatBellAction
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.dsv2.DmFilterChip
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.dmClickable

/** Test tag layar antrean Tinjau Expat (konvensi `dm_expat_...`). */
object ExpatQueueTags {
    const val ROOT = "dm_expat_queue_root"
    const val LIST = "dm_expat_queue_list"
    const val ROW = "dm_expat_queue_row"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpatReviewQueueScreen(
    vm: ExpatReviewQueueViewModel,
    onOpenReview: (driverUid: String) -> Unit,
    notificationUnread: Int = 0,
    onOpenNotifications: (() -> Unit)? = null
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(Unit) { vm.load() }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        // Flag harus di Scaffold: lonceng ada di slot topBar, subtree terpisah dari konten.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = Color.Transparent,
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.expat_queue_title),
                subtitle = stringResource(R.string.expat_queue_subtitle),
                subtitleMaxLines = 3,
                actions = {
                    onOpenNotifications?.let { ExpatBellAction(unread = notificationUnread, onClick = it) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ExpatQueueTags.ROOT)
                .padding(padding)
        ) {
            // FlowRow: pada font besar chip utuh pindah ke baris berikutnya, tidak ada chip yang hilang atau terpotong.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dsv2Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
            ) {
                val counts = state.counts
                listOf(
                    ExpatQueueFilter.PENDING to R.string.expat_queue_filter_pending,
                    ExpatQueueFilter.ALL to R.string.expat_queue_filter_all,
                    ExpatQueueFilter.REJECTED to R.string.expat_queue_filter_rejected
                ).forEach { (filter, labelRes) ->
                    // Angka chip berubah halus saat jumlah berubah; reduce motion = langsung.
                    val shownCount by animateIntAsState(
                        targetValue = counts[filter] ?: 0,
                        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 400),
                        label = "expatQueueChipCount"
                    )
                    DmFilterChip(
                        text = stringResource(
                            R.string.expat_queue_filter_count_format,
                            stringResource(labelRes),
                            shownCount
                        ),
                        selected = state.filter == filter,
                        onClick = { vm.setFilter(filter) }
                    )
                }
            }
            when {
                state.fatalError -> DmErrorState(
                    message = stringResource(R.string.expat_state_error_message),
                    retryText = stringResource(R.string.expat_state_error_retry),
                    onRetry = vm::load
                )
                state.loading && state.allItems.isEmpty() -> QueueSkeleton()
                else -> QueueList(
                    state = state,
                    onRetry = vm::load,
                    onOpenReview = onOpenReview
                )
            }
        }
    }
    }
}

@Composable
private fun QueueSkeleton() {
    val loadingCd = stringResource(R.string.expat_state_loading_cd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dsv2Spacing.md)
            .semantics { contentDescription = loadingCd },
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        repeat(5) { DmSkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 64.dp)) }
    }
}

@Composable
private fun QueueList(
    state: ExpatQueueUiState,
    onRetry: () -> Unit,
    onOpenReview: (String) -> Unit
) {
    val visible = state.visibleItems
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ExpatQueueTags.LIST),
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
        if (visible.isEmpty()) {
            item {
                val (titleRes, messageRes) = when (state.filter) {
                    ExpatQueueFilter.PENDING ->
                        R.string.expat_queue_empty_pending_title to R.string.expat_queue_empty_pending_message
                    ExpatQueueFilter.ALL ->
                        R.string.expat_queue_empty_all_title to R.string.expat_queue_empty_all_message
                    ExpatQueueFilter.REJECTED ->
                        R.string.expat_queue_empty_rejected_title to R.string.expat_queue_empty_rejected_message
                }
                DmEmptyState(
                    title = stringResource(titleRes),
                    message = stringResource(messageRes)
                )
            }
        }
        // Dikelompokkan per tanggal, yang tertua lebih dulu (paling lama menunggu di atas).
        groupQueueByDate(visible).forEach { group ->
            item(key = "day:${group.dateEpochDay}") { QueueDayHeader(group.dateEpochDay, group.items.size) }
            items(group.items, key = { "${it.driverUid}:${it.workdayId}" }) { item ->
                QueueRow(item = item, onClick = { onOpenReview(item.driverUid) })
            }
        }
    }
}

@Composable
private fun QueueRow(item: ExpatQueueItem, onClick: () -> Unit) {
    val date = expatDateLabel(item.dateEpochDay, rememberExpatLocale())
    val statusText = when (item.status) {
        ApprovalStatus.PENDING -> stringResource(R.string.expat_queue_row_status_pending)
        ApprovalStatus.APPROVED -> stringResource(R.string.expat_queue_row_status_approved)
        ApprovalStatus.REJECTED -> stringResource(R.string.expat_queue_row_status_rejected)
    }
    val rowCd = stringResource(R.string.expat_queue_row_cd_format, item.driverName, date, statusText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .dmClickable(onClick = onClick, role = Role.Button)
            .testTag(ExpatQueueTags.ROW)
            .semantics(mergeDescendants = true) { contentDescription = rowCd }
            .padding(vertical = Dsv2Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
    ) {
        ExpatQueueAvatar(item.driverName)
        // Tanggal sudah menjadi judul kelompok; tetap dibacakan TalkBack lewat deskripsi baris.
        Text(
            text = item.driverName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        ExpatApprovalPill(status = item.status, text = statusText)
    }
}

/** Judul kelompok tanggal + jumlah baris di kelompok itu. */
@Composable
private fun QueueDayHeader(dateEpochDay: Int, count: Int) {
    val date = expatDateLabel(dateEpochDay, rememberExpatLocale())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dsv2Spacing.sm)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
