package com.drivemate.showcase.ui.dsv2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.motionDuration
import com.drivemate.showcase.ui.ds.DriveMateLoadingIndicator

@Composable
fun Dsv2ElevatedCard(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    // Opt-in override for surfaces that sit on a brand gradient rather than the
    // app background (login glass card). Defaults to the standard onSurface
    // hairline, so every existing caller is unchanged.
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(if (secondary) Dsv2Premium.cardSecondary else Dsv2Premium.cardPrimary)
    val elevation = if (secondary) Dsv2Premium.shadowLow else Dsv2Premium.shadowHigh
    val ambient = MaterialTheme.colorScheme.onSurface.copy(alpha = Dsv2Premium.shadowAmbientAlpha)
    val spot = MaterialTheme.colorScheme.onSurface.copy(alpha = Dsv2Premium.shadowSpotAlpha)
    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = ambient,
                spotColor = spot,
                clip = false
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            Dsv2Border.hairline,
            borderColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = Dsv2Premium.borderAlpha)
        ),
        tonalElevation = Dsv2Elevation.none,
        shadowElevation = Dsv2Elevation.none
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dsv2Spacing.sm),
            content = content
        )
    }
}

@Composable
fun Dsv2ElevatedSurface(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(if (secondary) Dsv2Premium.cardSecondary else Dsv2Premium.cardPrimary)
    val ambient = MaterialTheme.colorScheme.onSurface.copy(alpha = Dsv2Premium.shadowAmbientAlpha)
    val spot = MaterialTheme.colorScheme.onSurface.copy(alpha = Dsv2Premium.shadowSpotAlpha)
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = Dsv2Premium.tonalAlpha)
    Surface(
        modifier = modifier
            .shadow(
                elevation = Dsv2Premium.shadowLow,
                shape = shape,
                ambientColor = ambient,
                spotColor = spot,
                clip = false
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            Dsv2Border.hairline,
            MaterialTheme.colorScheme.onSurface.copy(alpha = Dsv2Premium.tonalBorderAlpha)
        ),
        tonalElevation = Dsv2Elevation.none,
        shadowElevation = Dsv2Elevation.none
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dsv2Spacing.sm),
            content = content
        )
    }
}

@Composable
fun Dsv2PremiumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    spinnerOnly: Boolean = false,
    hapticOnClick: Boolean = false,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    // Forces the disabled (dimmed, no press-scale) visual even when `enabled`
    // is true. Lets a caller keep a button clickable -- so it can intercept
    // taps to give reject-style haptic feedback -- while still LOOKING locked.
    visuallyLocked: Boolean = false,
    // Terminal "success" state: swaps the label for a bouncing checkmark +
    // confirmedText, tints the gradient success-green instead of primary, and
    // makes the button permanently non-clickable (prevents re-tap/resubmit).
    // Once true it should stay true for the rest of this button's lifetime --
    // callers drive that from UI state, not this composable.
    confirmed: Boolean = false,
    confirmedText: String? = null,
    // Di atas gradien hero biru: tombol putih solid + teks biru tua (kontras >= 4.5:1),
    // menggantikan tombol biru-di-atas-biru yang nyaris menyatu dengan latar.
    onHero: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val effectiveEnabled = enabled && !confirmed
    val scale by animateFloatAsState(
        targetValue = if (pressed && effectiveEnabled && !visuallyLocked && !reduceMotion) 0.98f else 1f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            tween(durationMillis = motionDuration(AppMotion.settle), easing = FastOutSlowInEasing)
        },
        label = "premiumButtonScale"
    )
    // Locked = disabled/visually-locked look, expressed as a container COLOUR swap (never layer alpha).
    // A loading button keeps its solid active colour so the spinner stays legible.
    val locked = !confirmed && !loading && (!effectiveEnabled || visuallyLocked)

    var confirmedHapticFired by remember { mutableStateOf(false) }
    LaunchedEffect(confirmed) {
        if (confirmed && !confirmedHapticFired) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            confirmedHapticFired = true
        }
    }
    val checkScale = remember { Animatable(if (confirmed) 1f else 0.4f) }
    LaunchedEffect(confirmed, reduceMotion) {
        if (confirmed) {
            if (reduceMotion) {
                checkScale.snapTo(1f)
            } else {
                checkScale.snapTo(0.4f)
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    val shape = RoundedCornerShape(Dsv2Premium.cardSecondary)
    val heroStyle = onHero && !confirmed
    val container = when {
        confirmed -> AppPalette.success
        locked -> AppPalette.disabledContainer
        heroStyle -> AppPalette.heroButtonContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val onPrimary = when {
        confirmed -> AppPalette.onSuccess
        locked -> AppPalette.onDisabled
        heroStyle -> AppPalette.heroButtonText
        else -> MaterialTheme.colorScheme.onPrimary
    }

    // ONE opaque, shape-clipped layer: solid fill, real elevation on the coloured Surface,
    // no border / gradient / highlight overlay / layer alpha.
    Surface(
        onClick = {
            if (hapticOnClick) {
                haptic.performHapticFeedback(hapticFeedbackType)
            }
            onClick()
        },
        enabled = effectiveEnabled,
        interactionSource = interactionSource,
        // M3's clickable Surface(onClick) overload does NOT set Role.Button -- its own
        // Button composable adds it via modifier.semantics. PROMPT-A requires role = Button
        // for TalkBack, so we set it here too. (Supersedes the earlier "redundant semantics"
        // review nit, which assumed Surface(onClick) already carried the role; it does not.)
        modifier = modifier
            .semantics { role = Role.Button }
            .heightIn(min = Dsv2Sizing.touch)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = shape,
        color = container,
        contentColor = onPrimary,
        border = null,
        tonalElevation = Dsv2Elevation.none,
        shadowElevation = if (locked) Dsv2Elevation.none else Dsv2Premium.shadowLow
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = Dsv2Spacing.sm, horizontal = Dsv2Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            when {
                confirmed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = onPrimary,
                            modifier = Modifier
                                .size(Dsv2Sizing.icon)
                                .graphicsLayer(scaleX = checkScale.value, scaleY = checkScale.value)
                        )
                        Spacer(Modifier.width(Dsv2Spacing.sm))
                        Text(
                            text = confirmedText ?: text,
                            color = onPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                loading -> {
                    if (spinnerOnly) {
                        DriveMateLoadingIndicator(
                            color = onPrimary,
                            size = Dsv2Sizing.iconContainer,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DriveMateLoadingIndicator(
                                color = onPrimary,
                                size = Dsv2Sizing.icon,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(Dsv2Spacing.sm))
                            Text(
                                text = text,
                                color = onPrimary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = text,
                        color = onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * MIGRATION: Prefer `WorkdayStatusPill` directly for new status UI.
 * Compatibility wrapper retained to keep existing callsites stable.
 */
@Composable
fun Dsv2StatusPill(
    text: String,
    kind: Dsv2StatusKind,
    modifier: Modifier = Modifier
) {
    WorkdayStatusPill(
        state = kind.toWorkdayVisualState(),
        label = text,
        modifier = modifier
    )
}

private fun Dsv2StatusKind?.toWorkdayVisualState(): WorkdayVisualState {
    return when (this) {
        Dsv2StatusKind.Pending -> WorkdayVisualState.Open
        Dsv2StatusKind.Approved -> WorkdayVisualState.Approved
        Dsv2StatusKind.Rejected -> WorkdayVisualState.Rejected
        Dsv2StatusKind.Neutral -> WorkdayVisualState.Neutral
        else -> WorkdayVisualState.Neutral
    }
}

@Composable
fun Dsv2IconContainer(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dsv2Sizing.iconContainer,
    iconSize: androidx.compose.ui.unit.Dp = Dsv2Sizing.icon
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = Dsv2Premium.iconBgAlpha),
        border = BorderStroke(
            Dsv2Border.hairline,
            tint.copy(alpha = Dsv2Premium.iconBorderAlpha)
        ),
        tonalElevation = Dsv2Elevation.none,
        shadowElevation = Dsv2Elevation.none
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
