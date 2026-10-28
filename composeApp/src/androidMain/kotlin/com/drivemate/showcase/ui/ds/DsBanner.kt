package com.drivemate.showcase.ui.ds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppMotion
import com.drivemate.showcase.ui.LocalReduceMotion
import kotlinx.coroutines.delay

@Composable
fun AutoDismissInlineAlert(
    kind: InlineAlertKind,
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionContentDescription: String? = null,
    durationMillis: Int = 5_000
) {
    val resolvedActionText = actionText ?: stringResource(R.string.auth_action_close)
    val resolvedActionDescription = actionContentDescription ?: stringResource(R.string.auth_action_close_message)
    val currentMessage by rememberUpdatedState(message)
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            delay(durationMillis.toLong())
            if (currentMessage == message) {
                onDismiss()
            }
        }
    }

    val reduceMotion = LocalReduceMotion.current
    val enterTransition = if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(AppMotion.normal)) +
            slideInVertically(initialOffsetY = { -it / 4 })
    }
    val exitTransition = if (reduceMotion) {
        ExitTransition.None
    } else {
        fadeOut(tween(AppMotion.normal)) +
            slideOutVertically(targetOffsetY = { -it / 6 })
    }
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = enterTransition,
        exit = exitTransition
    ) {
        InlineAlert(
            kind = kind,
            message = message.orEmpty(),
            actionText = resolvedActionText,
            actionIcon = Icons.Outlined.Close,
            actionContentDescription = resolvedActionDescription,
            actionPlacement = InlineAlertActionPlacement.Trailing,
            onAction = onDismiss,
            modifier = modifier,
            showBorder = true
        )
    }
}
