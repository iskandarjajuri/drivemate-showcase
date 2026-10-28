package com.drivemate.showcase.ui.ds

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing

@Composable
fun DmLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = AppPalette.primaryHover,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    underline: Boolean = false
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = AppSizing.touchTarget),
        contentPadding = PaddingValues(horizontal = AppSpacing.xxs, vertical = AppSpacing.xxs),
        colors = ButtonDefaults.textButtonColors(
            contentColor = textColor,
            disabledContentColor = AppPalette.onDisabled
        )
    ) {
        Text(
            text,
            style = textStyle.copy(
                textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
            )
        )
    }
}
