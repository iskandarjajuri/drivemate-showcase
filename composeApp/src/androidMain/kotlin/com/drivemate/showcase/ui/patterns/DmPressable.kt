package com.drivemate.showcase.ui.patterns

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.motionDuration

@Composable
fun Modifier.dmPressable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) 0.985f else 1f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            tween(
                durationMillis = motionDuration(AppMotion.surfacePress),
                easing = FastOutSlowInEasing
            )
        },
        label = "dmPressableScale"
    )
    val shadowDp by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) 1.5f else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            tween(
                durationMillis = motionDuration(AppMotion.surfacePress),
                easing = FastOutSlowInEasing
            )
        },
        label = "dmPressableShadow"
    )
    val density = LocalDensity.current
    val shadowPx = with(density) { shadowDp.dp.toPx() }

    return this
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            shadowElevation = shadowPx
        )
        .clickable(
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
}
