package com.drivemate.showcase.feature.driver

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.exclude
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMEmptyState
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.displayOvertimeMinutesOrNull
import com.drivemate.showcase.core.model.displayTotalMinutesOrNull
import com.drivemate.showcase.feature.driver.DriverMaestroTags
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import com.drivemate.showcase.util.WorkdayCorrectionUiInfo
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import kotlinx.datetime.TimeZone

@Composable
fun DriverWorkdayDetailScreen(
    vm: DriverWorkdayDetailViewModel,
    workdayId: String,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val zone = TimeZone.of("Asia/Jakarta")
    val dash = stringResource(R.string.driver_placeholder_dash)
    val errorRes = state.errorRes
    val data = state.data

    LaunchedEffect(workdayId) { vm.load(workdayId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_workday_detail_title),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(DriverMaestroTags.WORKDAY_DETAIL_ROOT)
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.statusBars))
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
        ) {
            DriverNetworkBanner(modifier = Modifier.fillMaxWidth())
            when {
                state.loading -> DriverWorkdayDetailSkeleton()
                errorRes != null -> DriverErrorState(
                    message = stringResource(errorRes),
                    onRetry = { vm.load(workdayId) }
                )
                data == null -> DMEmptyState(
                    title = stringResource(R.string.driver_workday_detail_title),
                    message = stringResource(R.string.driver_workday_detail_empty)
                )
                else -> {
                    DriverWorkdayDetailContent(
                        data = data,
                        zone = zone,
                        dash = dash
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverWorkdayDetailContent(
    data: WorkDay,
    zone: TimeZone,
    dash: String
) {
    val dateText = WorkdayTimeFormatUtil.formatDateLabel(
        epochDay = data.dateEpochDay,
        skeleton = "yMMMMEEEEd"
    )
    val correctionInfo = WorkdayTimeFormatUtil.correctionUiInfo(data)
    val effectiveInText = WorkdayTimeFormatUtil.formatClockOrDash(correctionInfo.effectiveClockInMillis, dash, zone)
    val effectiveOutText = WorkdayTimeFormatUtil.formatClockOrDash(correctionInfo.effectiveClockOutMillis, dash, zone)
    val beforeInText = WorkdayTimeFormatUtil.formatClockOrDash(correctionInfo.beforeClockInMillis, dash, zone)
    val beforeOutText = WorkdayTimeFormatUtil.formatClockOrDash(correctionInfo.beforeClockOutMillis, dash, zone)
    val totalText = WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(data.displayTotalMinutesOrNull(), dash)
    val overtimeText = WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(data.displayOvertimeMinutesOrNull(), dash)
    val status = resolveWorkdayDetailStatus(data, correctionInfo.hasCorrection)
    val correctedSummary = correctedSummaryText(correctionInfo, zone)
    val hasBeforeSnapshot = correctionInfo.beforeClockInMillis != null || correctionInfo.beforeClockOutMillis != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(AppSpacing.xs))
            DriverStatusPill(
                text = stringResource(status.labelRes),
                kind = status.kind
            )
            if (correctionInfo.hasCorrection && correctedSummary != null) {
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = correctedSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = correctedSummary }
                )
            }
        }

        DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
            DriverSectionHeader(title = stringResource(R.string.driver_workday_detail_section_effective))
            Spacer(Modifier.height(AppSpacing.xs))
            DriverInfoRow(
                label = stringResource(R.string.admin_data_status_in),
                value = effectiveInText
            )
            DriverInfoRow(
                label = stringResource(R.string.admin_data_status_out),
                value = effectiveOutText
            )
            DriverInfoRow(
                label = stringResource(R.string.driver_history_summary_total),
                value = totalText
            )
            Text(stringResource(R.string.driver_workday_detail_label_break, dash))
            DriverInfoRow(
                label = stringResource(R.string.driver_history_summary_overtime),
                value = overtimeText
            )
        }

        if (correctionInfo.hasCorrection) {
            DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
                DriverSectionHeader(title = stringResource(R.string.driver_workday_detail_section_correction))
                Spacer(Modifier.height(AppSpacing.xs))
                Text(stringResource(R.string.driver_workday_detail_label_before))
                DriverInfoRow(
                    label = stringResource(R.string.admin_data_status_in),
                    value = beforeInText
                )
                DriverInfoRow(
                    label = stringResource(R.string.admin_data_status_out),
                    value = beforeOutText
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(stringResource(R.string.driver_workday_detail_label_after))
                DriverInfoRow(
                    label = stringResource(R.string.admin_data_status_in),
                    value = effectiveInText
                )
                DriverInfoRow(
                    label = stringResource(R.string.admin_data_status_out),
                    value = effectiveOutText
                )
                if (!hasBeforeSnapshot) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = stringResource(R.string.driver_workday_detail_before_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverWorkdayDetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        repeat(3) {
            DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
                SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(AppSpacing.lg))
                Spacer(Modifier.height(AppSpacing.xs))
                SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.md))
                Spacer(Modifier.height(AppSpacing.xxs))
                SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(AppSpacing.md))
                Spacer(Modifier.height(AppSpacing.xxs))
                SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(AppSpacing.md))
            }
        }
    }
}

private data class WorkdayStatusUi(
    val labelRes: Int,
    val kind: Dsv2StatusKind
)

private fun resolveWorkdayDetailStatus(
    data: WorkDay,
    hasCorrection: Boolean
): WorkdayStatusUi {
    return when (data.approvalStatus()) {
        ApprovalStatus.APPROVED -> WorkdayStatusUi(
            labelRes = R.string.driver_history_status_approved,
            kind = Dsv2StatusKind.Approved
        )
        ApprovalStatus.REJECTED -> WorkdayStatusUi(
            labelRes = R.string.driver_history_status_rejected,
            kind = Dsv2StatusKind.Rejected
        )
        ApprovalStatus.PENDING -> {
            val effectiveIn = WorkdayTimeFormatUtil.effectiveClockOrNull(data.clockInMillis)
            val effectiveOut = WorkdayTimeFormatUtil.effectiveClockOrNull(data.clockOutMillis)
            if (effectiveIn != null && effectiveOut == null) {
                WorkdayStatusUi(
                    labelRes = R.string.admin_status_open,
                    kind = Dsv2StatusKind.Neutral
                )
            } else if (hasCorrection) {
                WorkdayStatusUi(
                    labelRes = R.string.driver_status_closed,
                    kind = Dsv2StatusKind.Neutral
                )
            } else {
                WorkdayStatusUi(
                    labelRes = R.string.driver_history_status_pending,
                    kind = Dsv2StatusKind.Pending
                )
            }
        }
    }
}

@Composable
private fun correctedSummaryText(
    correctionInfo: WorkdayCorrectionUiInfo,
    zone: TimeZone
): String? {
    if (!correctionInfo.hasCorrection) return null
    val roleLabel = when (correctionInfo.correctedByRole?.trim()?.uppercase()) {
        "ADMIN" -> stringResource(R.string.driver_history_corrected_role_admin)
        "EXPAT" -> stringResource(R.string.driver_history_corrected_role_expat)
        "SYSTEM" -> stringResource(R.string.driver_history_corrected_role_system)
        else -> stringResource(R.string.driver_history_corrected_role_unknown)
    }
    val correctedAtText = WorkdayTimeFormatUtil.formatClockOrNull(correctionInfo.correctedAtMillis, zone)
    return if (correctedAtText != null) {
        stringResource(R.string.driver_history_corrected_by_role_at, roleLabel, correctedAtText)
    } else {
        stringResource(R.string.driver_history_corrected_by_role, roleLabel)
    }
}
