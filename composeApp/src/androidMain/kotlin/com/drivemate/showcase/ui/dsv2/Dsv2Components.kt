package com.drivemate.showcase.ui.dsv2

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R

data class Dsv2BottomNavItem(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dsv2TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                DmIconButton(
                    onClick = onBack,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun Dsv2BottomBar(
    items: List<Dsv2BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (Dsv2BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    NavigationBar(
        // 48dp (AppSizing.touchTarget), bukan 40dp: diukur langsung di emulator
        // (uiautomator bounds) -- container 40dp membatasi tinggi item navigasi
        // di dalamnya jadi TIDAK BISA lebih dari 40dp sama sekali (parent max
        // adalah batas keras), sehingga target sentuh tiap item benar-benar di
        // bawah 48dp minimum, bukan cuma dugaan. 48dp tetap jauh lebih ringkas
        // dari bar portrait default (~80dp), masih menghemat ruang vertikal di
        // landscape, tapi tidak lagi melanggar minimum target sentuh.
        modifier = if (isLandscape) {
            modifier.heightIn(min = Dsv2Sizing.touch, max = Dsv2Sizing.touch)
        } else {
            modifier
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = Dsv2Elevation.low
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val label = stringResource(item.labelRes)
            val interactionSource = remember(item.route) { MutableInteractionSource() }
            val haptic = LocalHapticFeedback.current
            NavigationBarItem(
                selected = selected,
                onClick = {
                    // Haptik ringan hanya saat benar-benar pindah tab.
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onItemSelected(item)
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = label,
                        modifier = Modifier
                            .size(if (isLandscape) 20.dp else Dsv2Sizing.icon)
                            .scale(rememberDmTabPopScale(selected, item.route))
                    )
                },
                label = if (isLandscape) null else { { Text(label) } },
                alwaysShowLabel = !isLandscape,
                interactionSource = interactionSource,
                modifier = Modifier.dmPressScale(interactionSource, DmPressKind.Tab)
            )
        }
    }
}

@Composable
fun Dsv2Card(
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(if (secondary) Dsv2Radii.cardSecondary else Dsv2Radii.cardPrimary)
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor ?: MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dsv2Elevation.none),
        border = BorderStroke(width = Dsv2Border.hairline, color = MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
            content = content
        )
    }
}

@Composable
fun Dsv2SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * MIGRATION: Prefer `WorkdayVisualState` with `WorkdayStatusPill` for new status UI.
 * Compatibility enum retained to keep existing callsites stable.
 */
enum class Dsv2StatusKind { Pending, Approved, Rejected, Neutral }

@Composable
fun Dsv2StatusChip(
    text: String,
    kind: Dsv2StatusKind,
    modifier: Modifier = Modifier
) {
    WorkdayStatusPill(
        state = kind.toWorkdayVisualState(),
        label = text,
        modifier = modifier,
        compact = true
    )
}

private fun Dsv2StatusKind?.toWorkdayVisualState(): WorkdayVisualState {
    return when (this) {
        Dsv2StatusKind.Pending -> WorkdayVisualState.Neutral
        Dsv2StatusKind.Approved -> WorkdayVisualState.Approved
        Dsv2StatusKind.Rejected -> WorkdayVisualState.Rejected
        Dsv2StatusKind.Neutral -> WorkdayVisualState.Neutral
        else -> WorkdayVisualState.Neutral
    }
}

@Composable
fun Dsv2ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        modifier.dmClickable(
            onClick = onClick,
            role = Role.Button,
            interactionSource = interactionSource
        )
    } else {
        modifier
    }

    Row(
        modifier = clickableModifier
            .fillMaxWidth()
            .heightIn(min = Dsv2Sizing.touch)
            .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        if (leading != null) {
            leading()
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)) {
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
fun Dsv2PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dsv2Sizing.touch),
        shape = RoundedCornerShape(Dsv2Radii.cardSecondary)
    ) {
        Text(text = text)
    }
}

@Composable
fun Dsv2SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dsv2Sizing.touch),
        shape = RoundedCornerShape(Dsv2Radii.cardSecondary),
        border = BorderStroke(Dsv2Border.hairline, MaterialTheme.colorScheme.outline)
    ) {
        Text(text = text)
    }
}

@Composable
fun Dsv2GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dsv2Sizing.touch)
    ) {
        Text(text = text)
    }
}

@Composable
fun Dsv2SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(Dsv2Radii.cardSecondary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.heightIn(min = Dsv2Sizing.touch)
    )
}

@Composable
fun Dsv2EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dsv2Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!actionText.isNullOrBlank() && onAction != null) {
            Dsv2GhostButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
fun Dsv2ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dsv2Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!actionText.isNullOrBlank() && onAction != null) {
            Dsv2PrimaryButton(text = actionText, onClick = onAction)
        }
    }
}
