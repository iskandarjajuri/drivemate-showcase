package com.drivemate.showcase.ui.patterns

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Sets whether the status- and navigation-bar icons are drawn light (white) or
 * dark, for the whole window.
 *
 * Deliberately a single hoisted caller rather than one effect per screen. The
 * first attempt gave Splash and Login each a `DisposableEffect` that saved the
 * previous appearance and restored it on exit; that races during a navigation
 * transition, because both screens are composed at once. Login would capture
 * "light icons" as its previous value, Splash's `onDispose` would then fire and
 * restore "dark icons", and Login ended up with near-invisible dark icons on a
 * saturated blue gradient — measured, not guessed: status-bar luminance came
 * back max=94 (dark) on Login while Splash read max=255 (white).
 *
 * A [SideEffect] driven by the current route has no such ordering problem: there
 * is one owner, and it simply re-asserts the correct value whenever the route
 * changes.
 *
 * Note the inversion — `isAppearanceLight*Bars` describes the *background*, so
 * a light background is what asks for dark icons.
 *
 * @param useLightIcons true when the surface behind the bars is dark.
 */
@Composable
fun DmSystemBarIconAppearance(useLightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // LocalActivity rather than casting LocalView.current.context: the view's
    // context is a ContextThemeWrapper, so the cast silently fails and the
    // icons never change.
    val window = LocalActivity.current?.window ?: return

    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !useLightIcons
        controller.isAppearanceLightNavigationBars = !useLightIcons
    }
}
