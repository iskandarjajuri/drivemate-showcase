package com.drivemate.showcase.ui.ds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.drivemate.showcase.ui.AppElevation
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSpacing

enum class DmCardVariant { Soft, Window }

@Composable
fun DmCard(
    modifier: Modifier = Modifier,
    variant: DmCardVariant = DmCardVariant.Soft,
    content: @Composable ColumnScope.() -> Unit
) {
    val container = when (variant) {
        DmCardVariant.Soft -> AppPalette.surface
        DmCardVariant.Window -> AppPalette.surface
    }
    val elevation = when (variant) {
        DmCardVariant.Soft -> AppElevation.none
        DmCardVariant.Window -> AppElevation.med
    }

    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppShapes.screenCard),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(width = AppSpacing.xxs / 4, color = AppPalette.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            content = content
        )
    }
}

@Composable
fun FeatureWindow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    DmCard(modifier = modifier.fillMaxWidth(), variant = DmCardVariant.Window) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), content = actions)
        }
        content()
    }
}
