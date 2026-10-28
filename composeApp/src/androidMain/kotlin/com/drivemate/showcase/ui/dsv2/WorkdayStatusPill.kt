package com.drivemate.showcase.ui.dsv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppElevation
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSpacing

enum class WorkdayVisualState {
    Open,
    Closed,
    Approved,
    Rejected,
    Corrected,
    Neutral
}

@Composable
fun WorkdayStatusPill(
    state: WorkdayVisualState,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val label = when (state) {
        WorkdayVisualState.Open -> stringResource(R.string.admin_status_open)
        WorkdayVisualState.Closed -> stringResource(R.string.driver_status_closed)
        WorkdayVisualState.Approved -> stringResource(R.string.driver_history_status_approved)
        WorkdayVisualState.Rejected -> stringResource(R.string.driver_history_status_rejected)
        WorkdayVisualState.Corrected -> stringResource(R.string.workday_marker_corrected)
        WorkdayVisualState.Neutral -> stringResource(R.string.admin_status_unknown)
    }
    WorkdayStatusPill(
        state = state,
        label = label,
        modifier = modifier,
        compact = compact,
        contentDescription = label
    )
}

@Composable
fun WorkdayStatusPill(
    state: WorkdayVisualState,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentDescription: String? = label
) {
    val (container, content, borderColor) = state.visualColors()
    val horizontal = if (compact) AppSpacing.xs else AppSpacing.sm
    val vertical = if (compact) AppSpacing.xxs else AppSpacing.xs
    val resolvedContentDescription = contentDescription?.takeIf { it.isNotBlank() } ?: label
    Surface(
        modifier = modifier.then(Modifier.semantics { this.contentDescription = resolvedContentDescription }),
        shape = RoundedCornerShape(AppShapes.field),
        color = container,
        border = BorderStroke(AppSpacing.xxs / 4, borderColor),
        tonalElevation = AppElevation.none,
        shadowElevation = AppElevation.low
    ) {
        Text(
            text = label,
            color = content,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = horizontal, vertical = vertical)
        )
    }
}

private fun WorkdayVisualState.visualColors(): Triple<Color, Color, Color> {
    // Pasangan SOLID dengan kontras >= 4.5:1 (lihat StatusContrastTest), agar
    // terbaca sama baiknya di kartu putih maupun di atas gradien hero biru.
    return when (this) {
        WorkdayVisualState.Open -> Triple(
            AppPalette.warningContainerSolid,
            AppPalette.onWarningContainer,
            AppPalette.onWarningContainer.copy(alpha = 0.22f)
        )
        WorkdayVisualState.Closed, WorkdayVisualState.Corrected -> Triple(
            AppPalette.infoContainerSolid,
            AppPalette.onInfoContainer,
            AppPalette.onInfoContainer.copy(alpha = 0.22f)
        )
        WorkdayVisualState.Approved -> Triple(
            AppPalette.successContainerSolid,
            AppPalette.onSuccessContainer,
            AppPalette.onSuccessContainer.copy(alpha = 0.22f)
        )
        WorkdayVisualState.Rejected -> Triple(
            AppPalette.errorContainerSolid,
            AppPalette.onErrorContainer,
            AppPalette.onErrorContainer.copy(alpha = 0.22f)
        )
        WorkdayVisualState.Neutral -> Triple(
            AppPalette.surfaceVariant,
            AppPalette.onSurfaceVariant,
            AppPalette.outlineVariant
        )
    }
}
