package com.drivemate.showcase.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.ds.DmLink as DsLink

@Composable
fun DMLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color? = null,
    textStyle: TextStyle? = null,
    underline: Boolean = false
) {
    DsLink(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        textColor = textColor ?: AppPalette.primaryHover,
        textStyle = textStyle ?: MaterialTheme.typography.labelMedium,
        underline = underline
    )
}
