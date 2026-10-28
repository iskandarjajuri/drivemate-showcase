package com.drivemate.showcase.ui

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalReduceMotion provides isReduceMotionEnabled(context)) {
        content()
    }
}

private fun isReduceMotionEnabled(context: Context): Boolean {
    return !ValueAnimator.areAnimatorsEnabled()
}

@Composable
fun motionDuration(millis: Int): Int = AppMotion.duration(LocalReduceMotion.current, millis)
