package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMEmptyState
import com.drivemate.showcase.core.model.Announcement
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverAnnouncementsScreen(
    vm: DriverAnnouncementsViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val errorRes = state.errorRes

    Scaffold(
        // Layar ini merender `when {}` langsung di dalam Scaffold, tanpa container root
        // tunggal untuk ditandai — jadi tag dipasang di Scaffold-nya: nol perubahan struktur
        // layout, dan seluruh subtree tetap mengekspos tag-nya ke accessibility tree.
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(DriverMaestroTags.ANNOUNCEMENTS_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.announcement_feed_title),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.announcements.isEmpty() -> {
                DriverAnnouncementsSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm)
                )
            }
            errorRes != null && state.announcements.isEmpty() -> {
                DriverErrorState(
                    message = stringResource(errorRes),
                    onRetry = vm::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                ) {
                    item { DriverNetworkBanner(modifier = Modifier.fillMaxWidth()) }

                    if (state.announcements.isEmpty()) {
                        item {
                            DMEmptyState(
                                title = stringResource(R.string.announcement_empty_title),
                                message = stringResource(R.string.announcement_empty_message)
                            )
                        }
                    } else {
                        items(state.announcements, key = { it.id }) { announcement ->
                            DriverAnnouncementItem(
                                announcement = announcement,
                                isRead = announcement.id in state.readIds,
                                isExpanded = announcement.id == state.expandedId,
                                onToggle = { vm.toggleExpand(announcement.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverAnnouncementItem(
    announcement: Announcement,
    isRead: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val zone = TimeZone.of("Asia/Jakarta")
    val epochDay = TimeUtil.epochDayOf(announcement.createdAtMillis, zone).toInt()
    val dateText = TimeUtil.formatYmdFromEpochDay(epochDay, zone)
    val timeText = TimeUtil.formatHm(announcement.createdAtMillis, zone)
    val metaText = announcement.createdByName?.takeIf { it.isNotBlank() }?.let { author ->
        stringResource(
            R.string.announcement_meta_published_by,
            author,
            stringResource(R.string.announcement_meta_timestamp, dateText, timeText)
        )
    } ?: stringResource(R.string.announcement_meta_timestamp, dateText, timeText)

    DriverCard(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isRead) {
                        DriverStatusPill(
                            text = stringResource(R.string.driver_announcements_new),
                            kind = Dsv2StatusKind.Pending
                        )
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(AppSpacing.sm))
                HorizontalDivider()
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DriverAnnouncementsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        repeat(5) {
            DriverCard(modifier = Modifier.fillMaxWidth()) {
                SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(AppSpacing.lg))
                Spacer(Modifier.height(AppSpacing.xs))
                SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.sm))
                Spacer(Modifier.height(AppSpacing.xs))
                SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.md))
                Spacer(Modifier.height(AppSpacing.xxs))
                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(AppSpacing.md))
            }
        }
    }
}
