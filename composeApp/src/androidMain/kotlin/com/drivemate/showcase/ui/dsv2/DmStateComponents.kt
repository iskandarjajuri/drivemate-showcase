package com.drivemate.showcase.ui.dsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.drivemate.showcase.ui.AppSpacing

/** Pill status generik; memetakan [Dsv2StatusKind] ke [WorkdayVisualState]. */
@Composable
fun DmStatusPill(
    text: String,
    kind: Dsv2StatusKind,
    modifier: Modifier = Modifier
) {
    WorkdayStatusPill(
        state = kind.toWorkdayVisualState(),
        label = text,
        modifier = modifier
    )
}

private fun Dsv2StatusKind?.toWorkdayVisualState(): WorkdayVisualState {
    return when (this) {
        Dsv2StatusKind.Pending -> WorkdayVisualState.Open
        Dsv2StatusKind.Approved -> WorkdayVisualState.Approved
        Dsv2StatusKind.Rejected -> WorkdayVisualState.Rejected
        Dsv2StatusKind.Neutral -> WorkdayVisualState.Neutral
        else -> WorkdayVisualState.Neutral
    }
}

@Composable
fun DmEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Dsv2EmptyState(
        title = title,
        message = message,
        modifier = modifier,
        actionText = actionText,
        onAction = onAction
    )
}

/** Keadaan galat: pesan di tengah + tombol coba lagi (teks tombol dari pemanggil). */
@Composable
fun DmErrorState(
    message: String,
    retryText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Dsv2PremiumPrimaryButton(
                text = retryText,
                onClick = onRetry,
                hapticOnClick = true
            )
        }
    }
}

/** Keadaan terkunci: kartu dengan judul, pesan, dan satu aksi. */
@Composable
fun DmLockedState(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionContentDescription: String? = null
) {
    DmElevatedCard(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Dsv2PremiumPrimaryButton(
            text = actionText,
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (actionContentDescription != null) {
                        Modifier.semantics { contentDescription = actionContentDescription }
                    } else {
                        Modifier
                    }
                ),
            hapticOnClick = true
        )
    }
}

/** Blok skeleton untuk placeholder pemuatan. */
@Composable
fun DmSkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dsv2Radii.small))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
    )
}
