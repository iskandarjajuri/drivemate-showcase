package com.drivemate.showcase.ui.ds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion

@Composable
fun DriveMateLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppPalette.primary,
    size: Dp = AppSpacing.lg,
    strokeWidth: Dp = 3.dp
) {
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "loadingPulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = AppMotion.slow * 3, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loadingAlpha"
        )
        animatedAlpha
    }
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color.copy(alpha = alpha),
        strokeWidth = strokeWidth
    )
}

@Composable
fun PremiumLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppPalette.primary,
    dotSize: Dp = AppSpacing.sm,
    dotSpacing: Dp = AppSpacing.xs
) {
    val reduceMotion = LocalReduceMotion.current
    val duration = AppMotion.slow * 3
    val delays = listOf(0, AppMotion.fast, AppMotion.fast * 2)

    if (reduceMotion) {
        Row(modifier = modifier) {
            delays.forEachIndexed { index, _ ->
                Spacer(
                    modifier = Modifier
                        .size(dotSize)
                        .background(color, CircleShape)
                )
                if (index != delays.lastIndex) {
                    Spacer(modifier = Modifier.width(dotSpacing))
                }
            }
        }
    } else {
        val transition = rememberInfiniteTransition(label = "premiumDots")
        Row(modifier = modifier) {
            delays.forEachIndexed { index, delay ->
                val alpha by transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = duration,
                            delayMillis = delay,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotAlpha$index"
                )
                val scale by transition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = duration,
                            delayMillis = delay,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotScale$index"
                )
                Spacer(
                    modifier = Modifier
                        .size(dotSize)
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .background(color, CircleShape)
                )
                if (index != delays.lastIndex) {
                    Spacer(modifier = Modifier.width(dotSpacing))
                }
            }
        }
    }
}
