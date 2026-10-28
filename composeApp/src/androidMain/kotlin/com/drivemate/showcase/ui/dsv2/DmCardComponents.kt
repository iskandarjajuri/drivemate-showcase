package com.drivemate.showcase.ui.dsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing

/** Kartu generik (bukan khusus peran): permukaan terangkat standar Dsv2. */
@Composable
fun DmElevatedCard(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    Dsv2ElevatedCard(
        modifier = modifier,
        secondary = secondary,
        contentPadding = contentPadding,
        content = content
    )
}

/** Kartu aksi: varian sekunder dengan warna wadah `surface`. */
@Composable
fun DmActionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    Dsv2ElevatedCard(
        modifier = modifier,
        secondary = true,
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = contentPadding,
        content = content
    )
}

/** Kartu tonal generik. */
@Composable
fun DmTonalCard(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
    content: @Composable ColumnScope.() -> Unit
) {
    Dsv2ElevatedSurface(
        modifier = modifier,
        secondary = secondary,
        contentPadding = contentPadding,
        content = content
    )
}

/** Dialog satu tombol (sukses/info); tidak bisa ditutup tanpa konfirmasi. */
@Composable
fun DmSuccessDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit
) {
    DmDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        dismissText = null,
        onConfirm = onConfirm,
        onDismiss = null,
        dismissible = false
    )
}

/** Dialog konfirmasi dua tombol; bisa ditutup lewat back/klik luar. */
@Composable
fun DmConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DmDialog(
        title = title,
        message = message,
        confirmText = confirmText,
        dismissText = dismissText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissible = true
    )
}

@Composable
private fun DmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)?,
    dismissible: Boolean
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DmDialogBackdrop()
            DmTonalCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg),
                secondary = false,
                contentPadding = PaddingValues(AppSpacing.lg)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    if (dismissText == null) {
                        DmDialogPrimaryButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            Dsv2SecondaryButton(
                                text = dismissText,
                                onClick = { onDismiss?.invoke() },
                                modifier = Modifier.weight(1f)
                            )
                            DmDialogPrimaryButton(
                                text = confirmText,
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Sama dengan DriverPrimaryButton dengan argumen bawaan (haptic aktif).
@Composable
private fun DmDialogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Dsv2PremiumPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        loading = false,
        spinnerOnly = false,
        hapticOnClick = true,
        hapticFeedbackType = androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
        visuallyLocked = false,
        confirmed = false,
        confirmedText = null,
        onHero = false
    )
}

@Composable
private fun DmDialogBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.14f))
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
    )
}

/** Chip filter generik. */
@Composable
fun DmFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = text,
                style = labelStyle
            )
        },
        modifier = modifier.minimumInteractiveComponentSize(),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = AppPalette.primarySoft.copy(alpha = 0.78f),
            selectedLabelColor = AppPalette.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = AppPalette.outlineVariant.copy(alpha = 0.55f),
            selectedBorderColor = AppPalette.primary.copy(alpha = 0.26f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}
