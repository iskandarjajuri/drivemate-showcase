package com.drivemate.showcase.feature.driver

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.motionDuration
import com.drivemate.showcase.ui.dsv2.dmClickable
import com.drivemate.showcase.ui.dsv2.DmActionCard
import com.drivemate.showcase.ui.dsv2.DmConfirmationDialog
import com.drivemate.showcase.ui.dsv2.DmElevatedCard
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmErrorState
import com.drivemate.showcase.ui.dsv2.DmFilterChip
import com.drivemate.showcase.ui.dsv2.DmLockedState
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.ui.dsv2.DmStatusPill
import com.drivemate.showcase.ui.dsv2.DmSuccessDialog
import com.drivemate.showcase.ui.dsv2.DmTonalCard
import com.drivemate.showcase.ui.dsv2.Dsv2IconContainer
import com.drivemate.showcase.ui.dsv2.Dsv2ListRow
import com.drivemate.showcase.ui.dsv2.Dsv2Premium
import com.drivemate.showcase.ui.dsv2.Dsv2PremiumPrimaryButton
import com.drivemate.showcase.ui.dsv2.Dsv2Radii
import com.drivemate.showcase.ui.dsv2.Dsv2SectionHeader
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

@Composable
fun DriverCard(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    DmElevatedCard(
        modifier = modifier,
        secondary = secondary,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun DriverNeumorphActionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    DmActionCard(
        modifier = modifier,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun DriverTonalCard(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    DmTonalCard(
        modifier = modifier,
        secondary = secondary,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun DriverPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    spinnerOnly: Boolean = false,
    hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType =
        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
    visuallyLocked: Boolean = false,
    confirmed: Boolean = false,
    confirmedText: String? = null,
    onHero: Boolean = false
) {
    Dsv2PremiumPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        spinnerOnly = spinnerOnly,
        hapticOnClick = true,
        hapticFeedbackType = hapticFeedbackType,
        visuallyLocked = visuallyLocked,
        confirmed = confirmed,
        confirmedText = confirmedText,
        onHero = onHero
    )
}

@Composable
fun DriverSuccessDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    confirmText: String? = null
) {
    val resolvedConfirmText = confirmText ?: stringResource(R.string.action_ok)
    DmSuccessDialog(
        title = title,
        message = message,
        confirmText = resolvedConfirmText,
        onConfirm = onConfirm
    )
}

@Composable
fun DriverConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedDismissText = dismissText ?: stringResource(R.string.action_cancel)
    DmConfirmationDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        dismissText = resolvedDismissText,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun DriverSectionHeader(
    title: String,
    subtitle: String? = null
) {
    Dsv2SectionHeader(title = title, subtitle = subtitle)
}

@Composable
fun DriverSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        DriverIconBubble(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = Dsv2Sizing.iconContainerLg,
            iconSize = Dsv2Sizing.icon
        )
        Dsv2SectionHeader(title = title, subtitle = subtitle)
    }
}

@Composable
fun DriverStatusSummaryCard(
    icon: ImageVector,
    title: String,
    statusText: String,
    statusKind: Dsv2StatusKind,
    description: String,
    modifier: Modifier = Modifier
) {
    DriverTonalCard(modifier = modifier.fillMaxWidth(), secondary = true) {
        DriverSectionHeader(icon = icon, title = title)
        Spacer(Modifier.height(AppSpacing.sm))
        DriverStatusPill(text = statusText, kind = statusKind)
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DriverInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        if (icon != null) {
            DriverIconBubble(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = Dsv2Sizing.iconContainer,
                iconSize = Dsv2Sizing.icon
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun DriverSelectableSurfaceRow(
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val reduceMotion = LocalReduceMotion.current
    val transition = if (reduceMotion) {
        snap<androidx.compose.ui.graphics.Color>()
    } else {
        tween(durationMillis = motionDuration(AppMotion.surfacePress), easing = FastOutSlowInEasing)
    }
    val highlightColor by animateColorAsState(
        targetValue = if (active) AppPalette.successContainer.copy(alpha = 0.88f) else AppPalette.surface,
        animationSpec = transition,
        label = "driverSelectionHighlight"
    )
    val borderColor = if (active) AppPalette.success.copy(alpha = 0.55f) else AppPalette.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (enabled) 1f else 0.6f)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = highlightColor,
        shape = RoundedCornerShape(Dsv2Radii.medium),
        tonalElevation = AppSpacing.xxs / 2,
        shadowElevation = AppSpacing.xxs / 2,
        border = BorderStroke(AppSpacing.xxs / 4, borderColor)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dsv2Sizing.touch)
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun DriverEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    DmEmptyState(
        title = title,
        message = message,
        modifier = modifier,
        actionText = actionText,
        onAction = onAction
    )
}

@Composable
fun DriverLockedState(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    actionText: String,
    actionContentDescription: String? = null,
    onAction: () -> Unit
) {
    DmLockedState(
        title = title,
        message = message,
        actionText = actionText,
        onAction = onAction,
        modifier = modifier,
        actionContentDescription = actionContentDescription
    )
}

/**
 * MIGRATION: Prefer `WorkdayStatusPill` directly for new status UI.
 * Compatibility wrapper retained to keep existing callsites stable.
 */
@Composable
fun DriverStatusPill(
    text: String,
    kind: Dsv2StatusKind,
    modifier: Modifier = Modifier
) {
    DmStatusPill(text = text, kind = kind, modifier = modifier)
}

@Composable
fun DriverIconBubble(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dsv2Sizing.iconContainer,
    iconSize: androidx.compose.ui.unit.Dp = Dsv2Sizing.icon
) {
    Dsv2IconContainer(
        icon = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
        size = size,
        iconSize = iconSize
    )
}

@Composable
fun DriverSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    com.drivemate.showcase.ui.dsv2.Dsv2SecondaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun DriverInteractiveFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    DmFilterChip(
        text = text,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun DriverErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    DmErrorState(
        message = message,
        retryText = stringResource(R.string.action_retry),
        onRetry = onRetry,
        modifier = modifier
    )
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    DmSkeletonBlock(modifier = modifier)
}

@Composable
fun DriverListItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Dsv2ListRow(
        title = title,
        subtitle = subtitle,
        meta = meta,
        onClick = onClick,
        leading = leading,
        trailing = trailing,
        modifier = modifier
    )
}
