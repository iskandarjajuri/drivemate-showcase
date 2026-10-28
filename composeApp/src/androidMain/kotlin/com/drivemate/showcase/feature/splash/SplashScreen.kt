package com.drivemate.showcase.feature.splash

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.patterns.DmBrandLoadingBar
import com.drivemate.showcase.ui.patterns.DmBrandWordmark
import com.drivemate.showcase.ui.patterns.DmNeuralWheel
import com.drivemate.showcase.ui.patterns.dmAuthGradientBackground
import com.drivemate.showcase.ui.patterns.rememberDmAuthBrandPalette
import com.drivemate.showcase.ui.patterns.rememberDmAuthIsDark
import kotlinx.coroutines.delay

object SplashMaestroTags {
    const val ROOT = "dm_splash_root"
}

/**
 * Minimum time the splash stays up once session readiness has already resolved.
 *
 * Two seconds is a deliberate brand hold, not an anti-flash guard: the earlier
 * 550ms floor was only ever long enough to stop a single-frame flicker, and the
 * Neural Wheel never got a full pulse cycle to be seen. There is still no
 * ceiling — the screen leaves as soon as readiness *and* this floor are both
 * satisfied, so a slow session resolve is never made slower by it.
 */
private const val SPLASH_MIN_VISIBLE_MILLIS = 2000L

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    isReady: Boolean,
    minimumVisibleMillis: Long = SPLASH_MIN_VISIBLE_MILLIS
) {
    // Anchor the floor to the first *rendered* frame, not to composition.
    //
    // On a cold start the system splash window is still up while this composable
    // is already composed. Timing from composition let the entire floor elapse
    // behind that window, so the redesigned splash was composed, counted, and
    // navigated away from without ever being presented — verified on-device: the
    // recording went straight from the system splash to Login.
    //
    // elapsedRealtime rather than currentTimeMillis: immune to the wall clock
    // moving (NTP sync, manual change) while the splash is up.
    var firstFrameAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        withFrameMillis { }
        firstFrameAt = SystemClock.elapsedRealtime()
    }

    LaunchedEffect(isReady, firstFrameAt, minimumVisibleMillis) {
        val anchor = firstFrameAt ?: return@LaunchedEffect
        if (!isReady) return@LaunchedEffect
        val remaining = minimumVisibleMillis - (SystemClock.elapsedRealtime() - anchor)
        if (remaining > 0L) delay(remaining)
        onFinished()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reduceMotion = LocalReduceMotion.current
    val palette = rememberDmAuthBrandPalette(isDark = rememberDmAuthIsDark())

    // The gradient Box takes no window insets on purpose — it is the outermost
    // visual layer and must reach the true window edges, behind the status and
    // navigation bars. Only the content Columns below are inset, via
    // safeDrawing, which also covers display cutouts.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dmAuthGradientBackground(palette)
            .semantics { testTagsAsResourceId = true }
            .testTag(SplashMaestroTags.ROOT)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = if (isLandscape) AppSpacing.scale24 else AppSpacing.scale16,
                    vertical = AppSpacing.scale24
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DmNeuralWheel(
                palette = palette,
                diameter = if (isLandscape) 108.dp else 148.dp,
                reduceMotion = reduceMotion
            )

            Spacer(modifier = Modifier.height(AppSpacing.scale24))

            DmBrandWordmark(
                palette = palette,
                style = MaterialTheme.typography.displaySmall
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = if (isLandscape) AppSpacing.scale32 else AppSpacing.scale24,
                    vertical = AppSpacing.scale24
                ),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DmBrandLoadingBar(
                palette = palette,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxWidth(if (isLandscape) 0.44f else 0.62f)
            )
            Spacer(
                modifier = Modifier.height(
                    if (isLandscape) AppSpacing.scale16 else AppSpacing.scale24
                )
            )
        }
    }
}
