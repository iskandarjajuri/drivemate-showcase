package com.drivemate.showcase.feature.announcement

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMEmptyState
import com.drivemate.showcase.components.DMErrorState
import com.drivemate.showcase.components.DMLoadingState
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.navigation.floatingNavContentPadding
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.ds.DmCard
import com.drivemate.showcase.ui.ds.DriveMateTopBar
import com.drivemate.showcase.ui.dsv2.DmIconButton
import com.drivemate.showcase.ui.ds.FeatureWindow
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.patterns.DmConfirmDialog
import kotlinx.datetime.TimeZone

@Composable
fun AnnouncementFeedScreen(
    vm: AnnouncementsViewModel,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    Scaffold(
        topBar = {
            DriveMateTopBar(
                title = stringResource(R.string.announcement_feed_title),
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(AnnouncementMaestroTags.FEED_ROOT)
                .padding(padding),
            // Inset nav bernilai 0.dp di luar rute tab, jadi tampilan Driver/Admin tidak berubah.
            contentPadding = floatingNavContentPadding(
                PaddingValues(
                    horizontal = AppSpacing.md,
                    vertical = AppSpacing.md
                )
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            announcementFeedItems(
                state = state,
                emptyTitleRes = R.string.announcement_empty_title,
                emptyMessageRes = R.string.announcement_empty_message,
                onRetry = vm::refresh
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.announcementFeedItems(
    state: AnnouncementFeedUiState,
    @StringRes emptyTitleRes: Int,
    @StringRes emptyMessageRes: Int,
    onRetry: () -> Unit,
    onEdit: ((Announcement) -> Unit)? = null,
    onDelete: ((Announcement) -> Unit)? = null
) {
    when {
        state.isLoading && state.announcements.isEmpty() -> {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DMLoadingState(message = stringResource(R.string.state_loading_message))
                }
            }
        }

        state.errorRes != null && state.announcements.isEmpty() -> {
            item {
                DMErrorState(
                    message = stringResource(state.errorRes),
                    onAction = onRetry
                )
            }
        }

        state.announcements.isEmpty() -> {
            item {
                DMEmptyState(
                    title = stringResource(emptyTitleRes),
                    message = stringResource(emptyMessageRes)
                )
            }
        }

        else -> {
            if (state.errorRes != null) {
                item {
                    InlineAlert(
                        kind = InlineAlertKind.Error,
                        message = stringResource(state.errorRes),
                        actionText = stringResource(R.string.announcement_action_retry),
                        onAction = onRetry
                    )
                }
            }
            items(state.announcements, key = { it.id }) { announcement ->
                AnnouncementCard(
                    announcement = announcement,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onEdit: ((Announcement) -> Unit)? = null,
    onDelete: ((Announcement) -> Unit)? = null
) {
    val zone = TimeZone.of("Asia/Jakarta")
    val dateText = TimeUtil.formatYmdFromEpochDay(
        epochDay = TimeUtil.epochDayOf(announcement.createdAtMillis, zone).toInt(),
        zone = zone
    )
    val timeText = TimeUtil.formatHm(announcement.createdAtMillis, zone)
    val timestampText = stringResource(R.string.announcement_meta_timestamp, dateText, timeText)
    val metaText = announcement.createdByName?.takeIf { it.isNotBlank() }?.let { author ->
        stringResource(R.string.announcement_meta_published_by, author, timestampText)
    } ?: timestampText

    DmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = announcement.body,
                style = MaterialTheme.typography.bodyMedium
            )
            if (onEdit != null || onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onEdit != null) {
                        DmIconButton(
                            onClick = { onEdit(announcement) },
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.announcement_edit_action)
                        )
                    }
                    if (onDelete != null) {
                        DmIconButton(
                            onClick = { onDelete(announcement) },
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.announcement_delete_action)
                        )
                    }
                }
            }
        }
    }
}
