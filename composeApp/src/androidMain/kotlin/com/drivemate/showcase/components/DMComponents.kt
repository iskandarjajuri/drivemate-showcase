package com.drivemate.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.VisualTransformation
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppElevation
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.ds.DmButton
import com.drivemate.showcase.ui.ds.DmButtonLoadingStyle
import com.drivemate.showcase.ui.ds.DmButtonVariant
import com.drivemate.showcase.ui.ds.DmCard
import com.drivemate.showcase.ui.ds.DmCardVariant
import com.drivemate.showcase.ui.ds.DriveMateTextField
import com.drivemate.showcase.ui.ds.DriveMateTopBar
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.WorkdayStatusPill
import com.drivemate.showcase.ui.dsv2.WorkdayVisualState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    DriveMateTopBar(title = title, onBack = onBack, actions = actions)
}

@Composable
fun DMPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingStyle: DmButtonLoadingStyle = DmButtonLoadingStyle.InlineWithText,
    animateLoading: Boolean = false,
    strongDisabled: Boolean = false
) {
    DmButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        loadingStyle = loadingStyle,
        animateLoading = animateLoading,
        strongDisabled = strongDisabled,
        modifier = modifier,
        variant = DmButtonVariant.Primary
    )
}

@Composable
fun DMOutlinedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
    isError: Boolean = false
) {
    DriveMateTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        helperText = if (!isError) supportingText else null,
        errorText = if (isError) supportingText else null
    )
}

@Composable
fun DMSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DmCard(modifier = modifier, variant = DmCardVariant.Soft) {
        if (!title.isNullOrBlank()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

enum class DMStatusType { Info, Success, Warning, Error }

@Composable
fun DMStatusBanner(
    type: DMStatusType,
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val kind = when (type) {
        DMStatusType.Info -> InlineAlertKind.Info
        DMStatusType.Success -> InlineAlertKind.Success
        DMStatusType.Warning -> InlineAlertKind.Warning
        DMStatusType.Error -> InlineAlertKind.Error
    }
    InlineAlert(kind = kind, message = text, modifier = modifier, actionText = actionText, onAction = onAction)
}

@Composable
fun DMLoadingState(modifier: Modifier = Modifier, message: String? = null) {
    val resolvedMessage = message ?: stringResource(R.string.state_loading_message)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        CircularProgressIndicator()
        Text(resolvedMessage, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DMEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) {
            DMPrimaryButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
fun DMErrorState(
    message: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String? = null
) {
    val resolvedActionText = actionText ?: stringResource(R.string.action_retry)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        DMStatusBanner(type = DMStatusType.Error, text = message)
        DMPrimaryButton(text = resolvedActionText, onClick = onAction)
    }
}

/**
 * MIGRATION: Prefer `WorkdayVisualState` with `WorkdayStatusPill` for new status UI.
 * Compatibility enum retained to keep existing callsites stable.
 */
enum class DMChipType { Ok, Issue, Pending, In, Out }

@Composable
fun DMStatusChip(
    type: DMChipType,
    text: String,
    modifier: Modifier = Modifier
) {
    WorkdayStatusPill(
        state = type.toWorkdayVisualState(),
        label = text,
        modifier = modifier,
        compact = true
    )
}

private fun DMChipType?.toWorkdayVisualState(): WorkdayVisualState {
    return when (this) {
        DMChipType.Ok -> WorkdayVisualState.Approved
        DMChipType.Issue -> WorkdayVisualState.Rejected
        DMChipType.Pending -> WorkdayVisualState.Neutral
        DMChipType.In -> WorkdayVisualState.Approved
        DMChipType.Out -> WorkdayVisualState.Closed
        else -> WorkdayVisualState.Neutral
    }
}

@Composable
fun DMListCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppShapes.screenCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.none)
    ) {
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = if (subtitle != null || supportingText != null) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        supportingText?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                null
            },
            leadingContent = if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null) }
            } else {
                null
            },
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                headlineColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
