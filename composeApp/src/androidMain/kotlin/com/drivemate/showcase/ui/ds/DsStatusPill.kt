package com.drivemate.showcase.ui.ds

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.drivemate.showcase.ui.dsv2.WorkdayStatusPill
import com.drivemate.showcase.ui.dsv2.WorkdayVisualState

/**
 * MIGRATION: Prefer `WorkdayStatusPill` for new workday-related status UI.
 * Compatibility wrapper retained to keep existing callsites stable.
 */
enum class StatusPillKind { Ok, Issue, Pending, In, Out }

@Composable
fun StatusPill(
    kind: StatusPillKind,
    text: String,
    modifier: Modifier = Modifier
) {
    WorkdayStatusPill(
        state = kind.toWorkdayVisualState(),
        label = text,
        modifier = modifier
    )
}

private fun StatusPillKind?.toWorkdayVisualState(): WorkdayVisualState {
    return when (this) {
        StatusPillKind.Ok -> WorkdayVisualState.Approved
        StatusPillKind.Issue -> WorkdayVisualState.Rejected
        StatusPillKind.Pending -> WorkdayVisualState.Neutral
        StatusPillKind.In -> WorkdayVisualState.Approved
        StatusPillKind.Out -> WorkdayVisualState.Closed
        else -> WorkdayVisualState.Neutral
    }
}
