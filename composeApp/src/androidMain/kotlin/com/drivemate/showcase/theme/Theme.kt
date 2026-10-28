package com.drivemate.showcase.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Legacy alias kept for compatibility.
 * Canonical theme wrapper lives in `com.drivemate.showcase.ui.DriveMateTheme`.
 */
@Composable
fun DriveMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    com.drivemate.showcase.ui.DriveMateTheme(
        darkTheme = darkTheme,
        content = content
    )
}
