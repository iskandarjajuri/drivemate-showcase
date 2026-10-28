package com.drivemate.showcase.ui.ds

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing

enum class TrackingStatusChip { Idle, Moving, Stale, Unknown }

@Composable
fun TrackingStatusChip(
    status: TrackingStatusChip,
    modifier: Modifier = Modifier
) {
    val (container, content, @StringRes labelRes) = when (status) {
        TrackingStatusChip.Idle -> Triple(AppPalette.surfaceVariant, AppPalette.onSurfaceVariant, R.string.work_status_idle)
        TrackingStatusChip.Moving -> Triple(AppPalette.primarySoft, AppPalette.onSurface, R.string.work_status_moving)
        TrackingStatusChip.Stale -> Triple(AppPalette.warningContainer, AppPalette.onSurface, R.string.work_status_stale)
        TrackingStatusChip.Unknown -> Triple(AppPalette.outlineVariant, AppPalette.onSurfaceVariant, R.string.work_status_unknown)
    }
    AssistChip(
        onClick = {},
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, color = content) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content
        ),
        modifier = modifier.heightIn(min = AppSizing.touchTarget)
    )
}

@Composable
fun FilterChipGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppPalette.primarySoft,
                    selectedLabelColor = AppPalette.onSurface
                )
            )
        }
    }
}
