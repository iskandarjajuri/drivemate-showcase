package com.drivemate.showcase.feature.expat.review

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.feature.expat.status.ExpatApprovalPill
import com.drivemate.showcase.feature.expat.ja.expatDateLabel
import com.drivemate.showcase.feature.expat.ja.rememberExpatLocale
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMStatusBanner
import com.drivemate.showcase.components.DMStatusType
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.displayOvertimeMinutesOrNull
import com.drivemate.showcase.core.model.displayTotalMinutesOrNull
import com.drivemate.showcase.feature.expat.ExpatMaestroTags
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.theme.Dimens
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.PrimaryButton
import com.drivemate.showcase.ui.SecondaryButton
import com.drivemate.showcase.ui.SectionTitle
import com.drivemate.showcase.ui.dsv2.DmElevatedCard
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmErrorState
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.patterns.DmFormDialog
import com.drivemate.showcase.util.WorkdayCorrectionUiInfo
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import com.drivemate.showcase.util.safeDriverLabel
import kotlinx.datetime.TimeZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpatWorkdayDetailScreen(
    vm: ExpatWorkdayDetailViewModel,
    workdayId: String,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val driverName by vm.driverName.collectAsState()
    val sopStatus by vm.sopStatus.collectAsState()
    val actionState by vm.actionState.collectAsState()
    val scheduleSummary by vm.scheduleSummary.collectAsState()
    val canReviewActions by vm.canReviewActions.collectAsState()
    val reviewAudit by vm.reviewAudit.collectAsState()
    val reviewLockedByFinalized by vm.reviewLockedByFinalized.collectAsState()
    val placeholderDash = stringResource(R.string.expat_placeholder_dash)
    val fallbackDriverLabel = stringResource(R.string.expat_driver_fallback)
    val zone = TimeZone.of("Asia/Jakarta")
    val auditDateTimeFormatter = rememberLocaleDateTimeFormatter(skeleton = "yMMMdHm")
    val auditZoneId = remember { ZoneId.of("Asia/Jakarta") }
    val data = state.data

    var showApproveDialog by remember { mutableStateOf(false) }
    var approveNote by remember { mutableStateOf("") }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var rejectError by remember { mutableStateOf(false) }

    LaunchedEffect(workdayId) { vm.load(workdayId) }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DmScreenHeader(title = stringResource(R.string.expat_workday_detail_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ExpatMaestroTags.WORKDAY_DETAIL_ROOT)
                .padding(padding)
                // Tanpa gulir, tombol Setujui/Tolak terpotong di bawah layar (terbukti di Pixel 1080x2400).
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.screenPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            when (val action = actionState) {
                is ExpatReviewActionState.Success -> DMStatusBanner(
                    type = DMStatusType.Success,
                    text = stringResource(action.messageRes),
                    actionText = stringResource(R.string.expat_action_close),
                    onAction = { vm.clearActionMessage() }
                )
                is ExpatReviewActionState.Error -> DMStatusBanner(
                    type = DMStatusType.Error,
                    text = stringResource(action.messageRes),
                    actionText = stringResource(R.string.expat_action_close),
                    onAction = { vm.clearActionMessage() }
                )
                else -> Unit
            }

            when {
                state.loading -> {
                    val loadingCd = stringResource(R.string.expat_state_loading_cd)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = loadingCd },
                        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                    ) {
                        repeat(3) { DmSkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 96.dp)) }
                    }
                }
                state.errorRes != null -> DmErrorState(
                    message = state.errorRes?.let { stringResource(it) }.orEmpty(),
                    retryText = stringResource(R.string.expat_state_error_retry),
                    onRetry = { vm.load(workdayId) }
                )
                data == null -> DmEmptyState(
                    title = stringResource(R.string.expat_empty_title),
                    message = stringResource(R.string.expat_workday_detail_empty_message)
                )
                else -> {
                    val dateText = expatDateLabel(data.dateEpochDay, rememberExpatLocale(), withYear = true)
                    val inText = WorkdayTimeFormatUtil.formatClockOrDash(
                        clockMillis = data.clockInMillis,
                        dash = placeholderDash,
                        zone = zone
                    )
                    val outText = WorkdayTimeFormatUtil.formatClockOrDash(
                        clockMillis = data.clockOutMillis,
                        dash = placeholderDash,
                        zone = zone
                    )
                    val approvalStatus = data.approvalStatus()
                    val statusRes = approvalStatusRes(approvalStatus)
                    val correctionInfo = WorkdayTimeFormatUtil.correctionUiInfo(data)
                    val correctionSummary = correctionSummaryText(correctionInfo, zone)
                    val checklistStatus = when (data.hasChecklist) {
                        true -> stringResource(R.string.expat_status_submitted)
                        false -> stringResource(R.string.expat_status_not_submitted)
                        null -> stringResource(R.string.expat_status_no_data)
                    }
                    val safetyStatus = when (data.hasSafetyDeclaration) {
                        true -> stringResource(R.string.expat_status_submitted)
                        false -> stringResource(R.string.expat_status_not_submitted)
                        null -> stringResource(R.string.expat_status_no_data)
                    }
                    val sopLabel = sopStatus?.let {
                        if (it.sopAccepted) {
                            stringResource(R.string.expat_status_sop_accepted)
                        } else {
                            stringResource(R.string.expat_status_sop_pending)
                        }
                    } ?: stringResource(R.string.expat_status_no_data)

                    DmElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.expat_section_driver))
                        Text(safeDriverLabel(driverName, placeholder = fallbackDriverLabel))
                        Text(stringResource(R.string.expat_label_date, dateText))
                    }

                    DmElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.expat_section_work_time))
                        val scheduledLabel = when {
                            !scheduleSummary.scheduleAvailable ->
                                stringResource(R.string.expat_review_schedule_unavailable)
                            scheduleSummary.isOffDay ->
                                stringResource(R.string.expat_schedule_off)
                            else -> WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                                totalMinutes = scheduleSummary.scheduledMinutes,
                                dash = placeholderDash
                            )
                        }
                        Text(
                            stringResource(
                                R.string.expat_schedule_label_scheduled,
                                scheduledLabel
                            )
                        )
                        Text(
                            stringResource(
                                R.string.expat_schedule_label_actual,
                                WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                                    totalMinutes = scheduleSummary.actualMinutes,
                                    dash = placeholderDash
                                )
                            )
                        )
                        if (scheduleSummary.overtimeMinutes > 0) {
                            Text(
                                stringResource(
                                    R.string.expat_schedule_label_overtime,
                                    WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                                        totalMinutes = scheduleSummary.overtimeMinutes,
                                        dash = placeholderDash
                                    )
                                )
                            )
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(stringResource(R.string.expat_label_in_out, inText, outText))
                        Text(
                            stringResource(
                                R.string.expat_label_total_overtime,
                                WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                                    totalMinutes = data.displayTotalMinutesOrNull(),
                                    dash = placeholderDash
                                ),
                                WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                                    totalMinutes = data.displayOvertimeMinutesOrNull(),
                                    dash = placeholderDash
                                )
                            )
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        ExpatApprovalPill(status = approvalStatus, text = stringResource(statusRes), compact = true)
                        if (correctionSummary != null) {
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                text = stringResource(R.string.workday_marker_corrected),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = correctionSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics {
                                    contentDescription = correctionSummary
                                }
                            )
                        }
                    }

                    reviewAudit?.let { audit ->
                        ReviewAuditSection(
                            audit = audit,
                            formatter = auditDateTimeFormatter,
                            zoneId = auditZoneId
                        )
                    }

                    DmElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.expat_section_daily_status))
                        Text(stringResource(R.string.expat_label_checklist_status, checklistStatus))
                        Text(stringResource(R.string.expat_label_safety_status, safetyStatus))
                        Text(stringResource(R.string.expat_label_sop_status, sopLabel))
                    }

                    if (canReviewActions) {
                        DMStatusBanner(
                            type = DMStatusType.Info,
                            text = stringResource(R.string.expat_review_ready_message)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PrimaryButton(
                                onClick = { showApproveDialog = true },
                                enabled = actionState !is ExpatReviewActionState.Submitting,
                                modifier = Modifier.fillMaxWidth().testTag(ExpatMaestroTags.WORKDAY_APPROVE)
                            ) {
                                Text(stringResource(R.string.expat_action_approve_this))
                            }
                            SecondaryButton(
                                onClick = { showRejectDialog = true },
                                enabled = actionState !is ExpatReviewActionState.Submitting,
                                modifier = Modifier.fillMaxWidth().testTag(ExpatMaestroTags.WORKDAY_REJECT)
                            ) {
                                Text(stringResource(R.string.expat_action_reject_this))
                            }
                        }
                    } else if (reviewLockedByFinalized) {
                        DMStatusBanner(
                            type = DMStatusType.Info,
                            text = stringResource(R.string.expat_review_finalized_locked)
                        )
                    }
                }
            }
        }
    }

    if (showApproveDialog && canReviewActions) {
        DmFormDialog(
            title = stringResource(R.string.expat_dialog_approve_title),
            confirmLabel = stringResource(R.string.expat_action_approve),
            cancelLabel = stringResource(R.string.expat_action_cancel),
            onConfirm = {
                showApproveDialog = false
                vm.approve(approveNote.trim().ifBlank { null })
                approveNote = ""
            },
            onDismiss = {
                showApproveDialog = false
                approveNote = ""
            },
            confirmEnabled = actionState !is ExpatReviewActionState.Submitting,
            dismissOnBackPress = actionState !is ExpatReviewActionState.Submitting,
            dismissOnClickOutside = actionState !is ExpatReviewActionState.Submitting
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(stringResource(R.string.expat_dialog_approve_message))
                OutlinedTextField(
                    value = approveNote,
                    onValueChange = { approveNote = it },
                    label = { Text(stringResource(R.string.expat_dialog_note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showRejectDialog && canReviewActions) {
        DmFormDialog(
            title = stringResource(R.string.expat_dialog_reject_title),
            confirmLabel = stringResource(R.string.expat_action_reject),
            cancelLabel = stringResource(R.string.expat_action_cancel),
            onConfirm = {
                if (rejectReason.isBlank()) {
                    rejectError = true
                } else {
                    showRejectDialog = false
                    vm.reject(rejectReason.trim())
                    rejectReason = ""
                    rejectError = false
                }
            },
            onDismiss = {
                showRejectDialog = false
                rejectReason = ""
                rejectError = false
            },
            confirmEnabled = actionState !is ExpatReviewActionState.Submitting,
            dismissOnBackPress = actionState !is ExpatReviewActionState.Submitting,
            dismissOnClickOutside = actionState !is ExpatReviewActionState.Submitting
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(stringResource(R.string.expat_dialog_reject_message))
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = {
                        rejectReason = it
                        rejectError = false
                    },
                    label = { Text(stringResource(R.string.expat_dialog_reject_reason_label)) },
                    isError = rejectError,
                    supportingText = if (rejectError) {
                        {
                            Text(
                                stringResource(R.string.expat_dialog_reject_reason_error),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    }
}

private fun approvalStatusRes(status: ApprovalStatus): Int = when (status) {
    ApprovalStatus.APPROVED -> R.string.driver_history_status_approved
    ApprovalStatus.REJECTED -> R.string.driver_history_status_rejected
    ApprovalStatus.PENDING -> R.string.driver_history_status_pending
}


@Composable
private fun correctionSummaryText(
    correctionInfo: WorkdayCorrectionUiInfo,
    zone: TimeZone
): String? {
    if (!correctionInfo.hasCorrection) return null
    val roleLabel = when (correctionInfo.correctedByRole?.trim()?.uppercase(Locale.ROOT)) {
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

@Composable
private fun ReviewAuditSection(
    audit: ExpatWorkdayReviewAuditUi,
    formatter: DateTimeFormatter,
    zoneId: ZoneId
) {
    DmElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            SectionTitle(stringResource(R.string.expat_audit_section_title))
            when (audit) {
                is ExpatWorkdayReviewAuditUi.Approved -> {
                    val approvedAt = audit.approvedAtMillis?.let { formatAuditDateTime(it, zoneId, formatter) }
                    val summaryText = when {
                        !audit.approverName.isNullOrBlank() && !approvedAt.isNullOrBlank() ->
                            stringResource(R.string.expat_audit_approved_by_time, audit.approverName, approvedAt)
                        !approvedAt.isNullOrBlank() ->
                            stringResource(R.string.expat_audit_approved_time, approvedAt)
                        else ->
                            stringResource(R.string.expat_audit_approved)
                    }
                    Text(summaryText)
                    if (!audit.approvalNote.isNullOrBlank()) {
                        Text(
                            stringResource(
                                R.string.expat_audit_note_label,
                                audit.approvalNote
                            )
                        )
                    }
                }
                is ExpatWorkdayReviewAuditUi.Rejected -> {
                    val rejectedAt = audit.rejectedAtMillis?.let { formatAuditDateTime(it, zoneId, formatter) }
                    val summaryText = when {
                        !audit.reason.isNullOrBlank() && !rejectedAt.isNullOrBlank() ->
                            stringResource(R.string.expat_audit_rejected_reason_time, audit.reason, rejectedAt)
                        !audit.reason.isNullOrBlank() ->
                            stringResource(R.string.expat_audit_rejected_reason, audit.reason)
                        !rejectedAt.isNullOrBlank() ->
                            stringResource(R.string.expat_audit_rejected_time, rejectedAt)
                        else ->
                            stringResource(R.string.expat_audit_rejected)
                    }
                    Text(summaryText)
                    if (!audit.reason.isNullOrBlank()) {
                        Text(
                            stringResource(
                                R.string.expat_audit_reason_label,
                                audit.reason
                            )
                        )
                    }
                    if (!audit.rejectionNote.isNullOrBlank()) {
                        Text(
                            stringResource(
                                R.string.expat_audit_note_label,
                                audit.rejectionNote
                            )
                        )
                    }
                    if (!audit.rejectedByName.isNullOrBlank()) {
                        Text(
                            stringResource(
                                R.string.expat_audit_rejected_by_label,
                                audit.rejectedByName
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatAuditDateTime(
    epochMillis: Long,
    zoneId: ZoneId,
    formatter: DateTimeFormatter
): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .format(formatter)
}

@Composable
private fun rememberLocaleDateTimeFormatter(skeleton: String): DateTimeFormatter {
    val configuration = LocalConfiguration.current
    val locale: Locale = configuration.locales[0]
    val pattern = remember(locale, skeleton) {
        DateFormat.getBestDateTimePattern(locale, skeleton)
    }
    return remember(pattern, locale) {
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}
