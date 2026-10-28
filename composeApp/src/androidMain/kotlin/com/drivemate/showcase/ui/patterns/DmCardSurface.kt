package com.drivemate.showcase.ui.patterns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.drivemate.showcase.ui.dsv2.Dsv2Border
import com.drivemate.showcase.ui.dsv2.Dsv2Elevation
import com.drivemate.showcase.ui.dsv2.Dsv2Radii
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing

@Composable
fun DmCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(Dsv2Radii.cardPrimary),
    elevation: Dp = Dsv2Elevation.none,
    borderEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (borderEnabled) {
        BorderStroke(
            width = Dsv2Border.hairline,
            color = borderColor
        )
    } else {
        null
    }
    val interactiveModifier = if (onClick != null) {
        Modifier
            .heightIn(min = Dsv2Sizing.touch)
            .dmPressable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(interactiveModifier),
        shape = shape,
        color = containerColor,
        border = border,
        tonalElevation = elevation,
        shadowElevation = Dsv2Elevation.none
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
            content = content
        )
    }
}
