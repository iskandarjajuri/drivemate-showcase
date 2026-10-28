package com.drivemate.showcase.ui.ds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing

enum class InlineAlertKind { Info, Success, Warning, Error }
enum class InlineAlertActionPlacement { Below, Trailing }

@Composable
fun InlineAlert(
    kind: InlineAlertKind,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    actionPlacement: InlineAlertActionPlacement = InlineAlertActionPlacement.Below,
    onAction: (() -> Unit)? = null,
    containerOverride: Color? = null,
    contentOverride: Color? = null,
    showBorder: Boolean = false
) {
    val (icon, container, content) = when (kind) {
        InlineAlertKind.Info -> Triple(Icons.Outlined.Info, AppPalette.infoContainerSolid, AppPalette.onInfoContainer)
        InlineAlertKind.Success -> Triple(Icons.Outlined.TaskAlt, AppPalette.successContainerSolid, AppPalette.onSuccessContainer)
        InlineAlertKind.Warning -> Triple(Icons.Outlined.WarningAmber, AppPalette.warningContainerSolid, AppPalette.onWarningContainer)
        InlineAlertKind.Error -> Triple(Icons.Outlined.ReportProblem, AppPalette.errorContainerSolid, AppPalette.onErrorContainer)
    }
    InlineAlertLayout(
        icon = icon,
        containerColor = containerOverride ?: container,
        contentColor = contentOverride ?: content,
        message = message,
        modifier = modifier,
        actionText = actionText,
        actionIcon = actionIcon,
        actionContentDescription = actionContentDescription,
        actionPlacement = actionPlacement,
        onAction = onAction,
        showBorder = showBorder
    )
}

@Composable
private fun InlineAlertLayout(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    message: String,
    modifier: Modifier,
    actionText: String?,
    actionIcon: ImageVector?,
    actionContentDescription: String?,
    actionPlacement: InlineAlertActionPlacement,
    onAction: (() -> Unit)?,
    showBorder: Boolean
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppShapes.field),
        tonalElevation = 0.dp,
        border = if (showBorder) BorderStroke(width = AppSpacing.xxs / 4, color = AppPalette.outline) else null
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            if (actionPlacement == InlineAlertActionPlacement.Trailing && !actionText.isNullOrBlank() && onAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(AppSizing.iconMd),
                            tint = contentColor
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DmButton(
                        text = actionText,
                        onClick = onAction,
                        variant = DmButtonVariant.Ghost,
                        leading = if (actionIcon != null) {
                            {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = actionContentDescription,
                                    modifier = Modifier.size(AppSizing.iconSm)
                                )
                            }
                        } else null
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(AppSizing.iconMd), tint = contentColor)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = contentColor)
                }
                if (!actionText.isNullOrBlank() && onAction != null) {
                    Spacer(Modifier.height(AppSpacing.xxs))
                    DmButton(
                        text = actionText,
                        onClick = onAction,
                        variant = DmButtonVariant.Ghost,
                        leading = if (actionIcon != null) {
                            {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = actionContentDescription,
                                    modifier = Modifier.size(AppSizing.iconSm)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}
