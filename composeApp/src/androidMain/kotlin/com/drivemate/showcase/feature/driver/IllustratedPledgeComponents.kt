package com.drivemate.showcase.feature.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.dsv2.Dsv2Radii
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing

/**
 * Shared "Illustrated Pledge" visual language for SOP and Safety Declaration --
 * both screens want the same large gradient icon badge + progress-dot pattern,
 * so it lives here once rather than being reimplemented per screen. Reuses
 * DriverHomeScreen's draw155Gradient (same-package internal) so badges match
 * the exact gradient direction/shape already used for Home's quick-action tiles.
 */
@Composable
fun PledgeGradientIconBadge(
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = Dsv2Sizing.iconContainer,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Dsv2Radii.medium))
            .drawBehind { draw155Gradient(gradient) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Position dots for a linear step flow. Filled = current or already passed,
 * outline = not yet reached. Deliberately position-only (no per-item
 * correct/incorrect meaning) since a "Belum" answer is still a completed step.
 */
@Composable
fun PledgeProgressDots(
    currentIndex: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val passedOrCurrent = index <= currentIndex
            Box(
                modifier = Modifier
                    .size(if (index == currentIndex) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (passedOrCurrent) AppPalette.primary else AppPalette.outlineVariant
                    )
            )
        }
    }
}

/**
 * Collapsed-by-default category card for SOP: gradient badge, title, point
 * count, expand chevron. Expanding is also the "I opened this category" read
 * signal the screen uses to gate acceptance -- see DriverSopScreen.
 */
@Composable
fun PledgeCategoryCard(
    icon: ImageVector,
    gradient: List<Color>,
    title: String,
    pointCountLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailingBadge: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    DriverCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onToggle
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            PledgeGradientIconBadge(icon = icon, gradient = gradient, size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pointCountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingBadge != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailingBadge
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                content = content
            )
        }
    }
}
