package com.drivemate.showcase.ui.ds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.VisualTransformation
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSizing
import com.drivemate.showcase.ui.AppSpacing

@Composable
fun DriveMateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    helperText: String? = null,
    errorText: String? = null
) {
    val errorMessage = errorText?.takeIf { it.isNotBlank() }
    val helperMessage = helperText?.takeIf { it.isNotBlank() }
    val isError = errorMessage != null
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = {
            when {
                errorMessage != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = AppPalette.error
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = AppPalette.error
                        )
                    }
                }
                helperMessage != null -> {
                    Text(
                        text = helperMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppPalette.onSurfaceVariant
                    )
                }
            }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppShapes.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isError) AppPalette.error else AppPalette.primaryHover,
            unfocusedBorderColor = AppPalette.outline,
            focusedContainerColor = AppPalette.surface,
            unfocusedContainerColor = AppPalette.surface,
            disabledContainerColor = AppPalette.surfaceVariant,
            disabledBorderColor = AppPalette.outlineVariant,
            errorBorderColor = AppPalette.error,
            focusedLabelColor = AppPalette.primary,
            unfocusedLabelColor = AppPalette.onSurfaceVariant,
            errorLabelColor = AppPalette.error,
            focusedTextColor = AppPalette.onSurface,
            unfocusedTextColor = AppPalette.onSurface,
            disabledTextColor = AppPalette.onDisabled,
            focusedLeadingIconColor = if (isError) AppPalette.error else AppPalette.primaryHover,
            unfocusedLeadingIconColor = AppPalette.onSurfaceVariant,
            errorLeadingIconColor = AppPalette.error,
            focusedTrailingIconColor = if (isError) AppPalette.error else AppPalette.primaryHover,
            unfocusedTrailingIconColor = AppPalette.onSurfaceVariant,
            errorTrailingIconColor = AppPalette.error,
            cursorColor = AppPalette.primary,
            errorCursorColor = AppPalette.error
        ),
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = if (isFocused) AppSizing.textFieldHeight else AppSizing.touchTarget)
    )
}
