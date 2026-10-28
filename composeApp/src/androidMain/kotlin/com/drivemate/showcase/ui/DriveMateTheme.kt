package com.drivemate.showcase.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DriveMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AppPalette.apply(darkTheme)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = AppPalette.primary,
            onPrimary = AppPalette.onPrimary,
            primaryContainer = AppPalette.primarySoft,
            onPrimaryContainer = AppPalette.onBackground,
            secondary = AppPalette.accent,
            onSecondary = AppPalette.onSurface,
            secondaryContainer = AppPalette.surfaceVariant,
            onSecondaryContainer = AppPalette.onSurface,
            tertiary = AppPalette.success,
            onTertiary = AppPalette.onSuccess,
            tertiaryContainer = AppPalette.successContainer,
            onTertiaryContainer = AppPalette.onSurface,
            background = AppPalette.background,
            onBackground = AppPalette.onBackground,
            surface = AppPalette.surface,
            onSurface = AppPalette.onSurface,
            surfaceVariant = AppPalette.surfaceVariant,
            onSurfaceVariant = AppPalette.onSurfaceVariant,
            outline = AppPalette.outline,
            outlineVariant = AppPalette.outlineVariant,
            error = AppPalette.error,
            onError = AppPalette.onError,
            errorContainer = AppPalette.errorContainer,
            onErrorContainer = AppPalette.onSurface,
            surfaceTint = AppPalette.primary
        )
    } else {
        lightColorScheme(
            primary = AppPalette.primary,
            onPrimary = AppPalette.onPrimary,
            primaryContainer = AppPalette.primarySoft,
            onPrimaryContainer = AppPalette.onBackground,
            secondary = AppPalette.accent,
            onSecondary = AppPalette.onSurface,
            secondaryContainer = AppPalette.surfaceVariant,
            onSecondaryContainer = AppPalette.onSurface,
            tertiary = AppPalette.success,
            onTertiary = AppPalette.onSuccess,
            tertiaryContainer = AppPalette.successContainer,
            onTertiaryContainer = AppPalette.onSurface,
            background = AppPalette.background,
            onBackground = AppPalette.onBackground,
            surface = AppPalette.surface,
            onSurface = AppPalette.onSurface,
            surfaceVariant = AppPalette.surfaceVariant,
            onSurfaceVariant = AppPalette.onSurfaceVariant,
            outline = AppPalette.outline,
            outlineVariant = AppPalette.outlineVariant,
            error = AppPalette.error,
            onError = AppPalette.onError,
            errorContainer = AppPalette.errorContainer,
            onErrorContainer = AppPalette.onSurface,
            surfaceTint = AppPalette.primary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DriveMateTypography,
        shapes = DriveMateShapes
    ) {
        ProvideReduceMotion {
            Surface(
                modifier = Modifier,
                color = colorScheme.background
            ) {
                content()
            }
        }
    }
}
