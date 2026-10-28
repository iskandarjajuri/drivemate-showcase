package com.drivemate.showcase.ui.dsv2

import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing

object Dsv2Spacing {
    val xs = AppSpacing.xs
    val sm = AppSpacing.sm
    val md = AppSpacing.md
    val lg = AppSpacing.lg
    val xl = AppSpacing.xl
}

object Dsv2Radii {
    val small = 12.dp
    val medium = 20.dp
    val large = 28.dp

    val cardPrimary = large
    val cardSecondary = medium
    val chip = medium
}

object Dsv2Premium {
    val cardPrimary = Dsv2Radii.large
    val cardSecondary = Dsv2Radii.medium
    val shadowLow = 2.dp
    val shadowHigh = 4.dp
    const val shadowAmbientAlpha = 0.03f
    const val shadowSpotAlpha = 0.06f
    const val borderAlpha = 0.08f
    const val tonalAlpha = 0.94f
    const val tonalBorderAlpha = 0.10f
    const val highlightAlpha = 0.05f
    const val iconBgAlpha = 0.10f
    const val iconBorderAlpha = 0.10f
    const val pillShadowAlpha = 0.03f
    val pillShadow = 0.dp
}

object Dsv2Elevation {
    val none = 0.dp
    val low = 1.dp
    val raised = 2.dp
}

object Dsv2Sizing {
    val touch = AppSizing.touchTarget
    val icon = 20.dp
    val iconContainer = 28.dp
    val iconContainerLg = 36.dp
}

object Dsv2Border {
    val hairline = 1.dp
}
