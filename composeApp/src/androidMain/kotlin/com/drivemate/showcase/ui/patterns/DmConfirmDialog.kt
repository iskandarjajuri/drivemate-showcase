package com.drivemate.showcase.ui.patterns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemate.showcase.ui.dsv2.Dsv2ElevatedCard
import com.drivemate.showcase.ui.dsv2.Dsv2ElevatedSurface
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing

@Composable
fun DmConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    cancelLabel: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
) {
    DmDialogContainer(
        title = title,
        modifier = modifier,
        onDismiss = onDismiss,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside
    ) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = cancelLabel)
            }
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun DmDialogContainer(
    title: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val bodyModifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dsv2Spacing.md)
            .semantics { paneTitle = title }

        if (isDark) {
            Dsv2ElevatedSurface(
                modifier = bodyModifier,
                secondary = true,
                contentPadding = PaddingValues(
                    horizontal = Dsv2Spacing.lg,
                    vertical = Dsv2Spacing.md
                )
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                content()
            }
        } else {
            Dsv2ElevatedCard(
                modifier = bodyModifier,
                secondary = true,
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(
                    horizontal = Dsv2Spacing.lg,
                    vertical = Dsv2Spacing.md
                )
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                content()
            }
        }
    }
}
