package com.drivemate.showcase.ui.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.drivemate.showcase.ui.AppPalette

/**
 * Subtle depth behind the three role dashboards (Admin/Driver/Expat Home).
 *
 * Used to be a sports-car photo watermarked at 20% alpha under a scrim. The
 * photo is gone — it was the last remnant of the pre-Neural-Wheel brand, and
 * the two PNGs behind it cost ~4.5MB of APK for something the scrim rendered
 * nearly invisible anyway.
 *
 * The replacement is deliberately not a new decorative invention: it reuses the
 * existing theme-aware `decor1`/`decor2` tokens in the same vertical-gradient
 * shape the old `AuthLiquidBackground` used before the redesign, so light and
 * dark both stay correct with no new colors introduced. The scrim below is
 * unchanged, which keeps the net visual weight in the same "barely there" range
 * the car occupied.
 */
@Composable
internal fun DmPostLoginBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scrimTopAlpha: Float = 0.52f,
    scrimBottomAlpha: Float = 0.80f
) {
    if (!enabled) return

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppPalette.decor1.copy(alpha = 0.55f),
                            AppPalette.decor2.copy(alpha = 0.40f),
                            backgroundColor
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = scrimTopAlpha),
                            backgroundColor.copy(alpha = scrimBottomAlpha)
                        )
                    )
                )
        )
    }
}
