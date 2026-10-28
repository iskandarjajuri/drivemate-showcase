package com.drivemate.showcase.feature.expat.review

import androidx.compose.foundation.clickable
import android.content.ClipData
import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drivemate.showcase.ui.patterns.DmConfirmDialog
import com.drivemate.showcase.feature.expat.status.ExpatApprovalPill
import com.drivemate.showcase.feature.expat.ja.expatDateLabel
import com.drivemate.showcase.feature.expat.ja.rememberExpatLocale
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMChipType
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmErrorState
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.components.DMStatusChip
import com.drivemate.showcase.components.DMTopAppBar
import com.drivemate.showcase.feature.expat.ExpatMaestroTags
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.feature.workday.WorkdayPlanEditDialog
import com.drivemate.showcase.core.log.DMLog
import com.drivemate.showcase.core.model.AttendanceDailyState
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.attendanceState
import com.drivemate.showcase.core.model.endOfWorkMillis
import com.drivemate.showcase.core.model.startOfWorkMillis
import com.drivemate.showcase.domain.calendar.OverrideKind
import com.drivemate.showcase.theme.Dimens
import com.drivemate.showcase.ui.SoftCard
import com.drivemate.showcase.ui.patterns.DmFormDialog
import com.drivemate.showcase.util.isAppDebuggable
import com.drivemate.showcase.util.safeDriverLabel
import com.drivemate.showcase.util.UiText
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import com.drivemate.showcase.ui.dsv2.DmIconButton
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * # ExpatMonthlyReviewScreen
 * Review bulanan (Expat):
 * - Menampilkan daftar WorkDay sebulan.
 * - Checkbox (bulk approve).
 * - Tombol Approve Selected & Reject (per item).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpatMonthlyReviewScreen(
    vm: ExpatMonthlyReviewViewModel,
    driverUid: String,
    onBack: () -> Unit,
    onOpenWorkdayDetail: (String) -> Unit
) {
    val loading by vm.loading.collectAsState()
    val errorRes by vm.error.collectAsState()
    val list by vm.list.collectAsState()
    val selected by vm.selected.collectAsState()
    val driverName by vm.driverName.collectAsState()
    val sessionReady by vm.sessionReady.collectAsState()
    val bulkMessage by vm.bulkMessage.collectAsState()
    val bulkWarning by vm.bulkWarning.collectAsState()
    val bulkDetails by vm.bulkDetails.collectAsState()
    val showCopyDetails by vm.showCopyDetails.collectAsState()
    val bulkRunning by vm.bulkRunning.collectAsState()
    val monthlySummary by vm.monthlyCalendarSummary.collectAsState()
    val dayCalendarMeta by vm.dayCalendarMeta.collectAsState()
    val auditLineByWorkdayId by vm.auditLineByWorkdayId.collectAsState()
    val showPendingOnly by vm.showPendingOnly.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val showUidActions = isAppDebuggable(context)
    val showRejectDialog = remember { mutableStateOf(false) }
    val showApproveConfirm = remember { mutableStateOf(false) }
    var editingWorkday by remember { mutableStateOf<WorkDay?>(null) }
    val rejectReason = rememberSaveable { mutableStateOf("") }
    val fallbackDriverLabel = stringResource(R.string.expat_driver_fallback)
    val copyDetailsToast = stringResource(R.string.expat_debug_copy_details_toast)
    val reviewDetailLabel = stringResource(R.string.expat_review_detail_label)
    // Salin rincian aksi massal: hanya build debug, lewat tekan-lama judul (tanpa teks "Debug" di UI; kebutuhan QA: docs/QA.md).
    val canCopyBulkDetails = showUidActions && showCopyDetails && !bulkDetails.isNullOrBlank()
    val copyBulkDetails: () -> Unit = {
        scope.launch {
            clipboard.setClipEntry(
                ClipEntry(ClipData.newPlainText(reviewDetailLabel, bulkDetails.orEmpty()))
            )
        }
        android.widget.Toast
            .makeText(context, copyDetailsToast, android.widget.Toast.LENGTH_SHORT)
            .show()
    }
    val auditFormatter = rememberLocaleDateTimeFormatter(skeleton = "yMMMdHm")
    val auditZoneId = remember { ZoneId.of("Asia/Jakarta") }
    val visiblePendingCount = list.count {
        it.approvalStatus() == ApprovalStatus.PENDING &&
            it.attendanceState() != AttendanceDailyState.OPEN
    }
    val selectedCount = selected.size
    val expatLocale = rememberExpatLocale()
    LaunchedEffect(driverUid) {
        DMLog.i("DM/Expat", "Load rolling review (last 3 months)")
        vm.load(driverUid)
    }
    DisposableEffect(lifecycleOwner, driverUid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onResumeFromDetail(driverUid)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DMTopAppBar(
                title = stringResource(R.string.expat_review_title),
                onBack = {
                    DMLog.d("DM/Expat", "Back pressed from review screen")
                    onBack()
                },
                // Aksi massal dipindah ke ExpatBulkActionBar (bottomBar); top bar
                // hanya menampilkan indikator proses.
                actions = {
                    if (bulkRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimens.lg),
                                strokeWidth = Dimens.xxs
                            )
                            Text(
                                stringResource(R.string.expat_review_processing),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                ExpatBulkActionBar(
                    selectedCount = selectedCount,
                    actionsEnabled = !loading && sessionReady && !bulkRunning,
                    onReject = {
                        DMLog.i(
                            "DM/Expat",
                            "Reject Selected clicked → total=${selected.size} items"
                        )
                        showRejectDialog.value = true
                    },
                    onApprove = {
                        DMLog.i(
                            "DM/Expat",
                            "Approve Selected clicked → total=${selected.size} items"
                        )
                        showApproveConfirm.value = true
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ExpatMaestroTags.REVIEW_ROOT)
                .padding(padding)
                .padding(
                    horizontal = Dimens.screenPadding,
                    vertical = Dimens.screenPaddingVertical
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Text(
                stringResource(
                    R.string.expat_review_driver_summary,
                    safeDriverLabel(driverName, placeholder = fallbackDriverLabel)
                ),
                style = MaterialTheme.typography.titleMedium,
                // Kunci memuat bulkDetails agar aksi massal berikutnya tidak menyalin rincian lama.
                modifier = Modifier.pointerInput(canCopyBulkDetails, bulkDetails) {
                    if (canCopyBulkDetails) detectTapGestures(onLongPress = { copyBulkDetails() })
                }
            )
            ExpatReviewFocusStrip(
                visibleCount = list.size,
                pendingCount = visiblePendingCount,
                selectedCount = selectedCount,
                pendingOnly = showPendingOnly
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                Checkbox(
                    checked = showPendingOnly,
                    onCheckedChange = { vm.togglePendingFilter() },
                    enabled = !loading && !bulkRunning
                )
                Text(
                    text = stringResource(R.string.expat_review_filter_pending_only),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val bulkMessageText = bulkMessage?.let { resolveUiText(it) }
            val bulkWarningText = bulkWarning?.let { resolveUiText(it) }
            if (bulkMessageText != null || bulkWarningText != null) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        bulkMessageText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        bulkWarningText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            when {
                loading -> {
                    DMLog.d("DM/Expat", "Review list loading…")
                    val loadingCd = stringResource(R.string.expat_state_loading_cd)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = loadingCd },
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                    ) {
                        repeat(4) { DmSkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 64.dp)) }
                    }
                }

                errorRes != null -> {
                    val errorText = errorRes?.let { stringResource(it) }.orEmpty()
                    DMLog.e("DM/Expat", "Error loading review list: $errorText")
                    DmErrorState(
                        message = errorText,
                        retryText = stringResource(R.string.expat_state_error_retry),
                        onRetry = { vm.load(driverUid) }
                    )
                }

                list.isEmpty() -> {
                    DMLog.d("DM/Expat", "No workday data for last 3 months")
                    DmEmptyState(
                        title = stringResource(R.string.expat_empty_title),
                        message = stringResource(R.string.expat_review_empty_message)
                    )
                }

                else -> {
                    ExpatMonthlySummaryStrip(monthlySummary = monthlySummary)
                    val grouped = list.groupBy { it.dateEpochDay }
                    val sortedKeys = grouped.keys.sortedDescending()
                    val driverLabel = safeDriverLabel(driverName, placeholder = fallbackDriverLabel)
                    LaunchedEffect(list) {
                        vm.logGroupingApplied(list.size)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                    ) {
                        sortedKeys.forEach { day ->
                            val dateLabel = expatDateLabel(day, expatLocale)
                            item(key = "header_$day") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Dimens.xs, horizontal = Dimens.sm)
                                    )
                                }
                            }
                            items(grouped[day].orEmpty(), key = { it.id }) { wd ->
                                WorkDayRow(
                                    item = wd,
                                    driverLabel = driverLabel,
                                    checked = wd.id in selected,
                                    enabled = !bulkRunning,
                                    auditLine = auditLineByWorkdayId[wd.id] ?: ExpatWorkdayAuditLineUi.None,
                                    auditDateTimeFormatter = auditFormatter,
                                    auditZoneId = auditZoneId,
                                    dayCalendarMeta = dayCalendarMeta[wd.dateEpochDay],
                                    onCheckedChange = { isChecked ->
                                        DMLog.d(
                                            "DM/Expat",
                                            "Toggle WorkDay, checked=$isChecked"
                                        )
                                        vm.toggle(wd.id)
                                    },
                                    onOpenDetail = { onOpenWorkdayDetail(wd.id) },
                                    onEditPlan = { editingWorkday = wd }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val bulkSummary = summarizeBulkSelection(
        list.filter { it.id in selected }.map { it.driverUid to it.dateEpochDay }
    )
    if (showApproveConfirm.value) {
        DmConfirmDialog(
            title = stringResource(R.string.expat_bulk_confirm_approve_title),
            message = if (bulkSummary.firstEpochDay == bulkSummary.lastEpochDay) {
                pluralStringResource(
                    R.plurals.expat_bulk_confirm_message_single,
                    bulkSummary.workdayCount,
                    bulkSummary.workdayCount,
                    bulkSummary.driverCount,
                    bulkSummary.firstEpochDay?.let { expatDateLabel(it, expatLocale) }.orEmpty()
                )
            } else {
                pluralStringResource(
                    R.plurals.expat_bulk_confirm_message,
                    bulkSummary.workdayCount,
                    bulkSummary.workdayCount,
                    bulkSummary.driverCount,
                    bulkSummary.firstEpochDay?.let { expatDateLabel(it, expatLocale) }.orEmpty(),
                    bulkSummary.lastEpochDay?.let { expatDateLabel(it, expatLocale) }.orEmpty()
                )
            },
            confirmLabel = stringResource(R.string.expat_action_approve),
            cancelLabel = stringResource(R.string.expat_action_cancel),
            onConfirm = {
                showApproveConfirm.value = false
                vm.approve()
            },
            onDismiss = { showApproveConfirm.value = false }
        )
    }

    if (showRejectDialog.value) {
        DmFormDialog(
            title = stringResource(R.string.expat_review_reject_reason_title),
            confirmLabel = stringResource(R.string.expat_action_reject),
            cancelLabel = stringResource(R.string.expat_action_cancel),
            onConfirm = {
                val reason = rejectReason.value.trim()
                showRejectDialog.value = false
                rejectReason.value = ""
                vm.rejectSelected(reason)
            },
            onDismiss = { showRejectDialog.value = false },
            confirmEnabled = rejectReason.value.isNotBlank() && !loading && !bulkRunning
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.expat_bulk_confirm_reject_summary,
                    bulkSummary.workdayCount,
                    bulkSummary.workdayCount,
                    bulkSummary.driverCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = rejectReason.value,
                onValueChange = { rejectReason.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.expat_review_reject_reason_label)) },
                singleLine = false
            )
        }
    }

    val planTarget = editingWorkday
    if (planTarget != null) {
        val effectiveStartMillis = WorkdayTimeFormatUtil.effectiveClockOrNull(planTarget.clockInMillis)
        val effectiveEndMillis = WorkdayTimeFormatUtil.effectiveClockOrNull(planTarget.clockOutMillis)
        val hasEffectiveClockData = effectiveStartMillis != null || effectiveEndMillis != null
        val fallbackStartMillis = planTarget.plannedStartMillis ?: planTarget.startOfWorkMillis()
        val fallbackEndMillis = planTarget.plannedEndMillis ?: planTarget.endOfWorkMillis()
        WorkdayPlanEditDialog(
            titleRes = R.string.expat_section_work_time,
            errorRes = R.string.expat_error_review_load_failed,
            epochDay = planTarget.dateEpochDay,
            initialStartMillis = effectiveStartMillis ?: if (!hasEffectiveClockData) fallbackStartMillis else null,
            initialEndMillis = effectiveEndMillis ?: if (!hasEffectiveClockData) fallbackEndMillis else null,
            onDismiss = { editingWorkday = null },
            onSave = { startMillis, endMillis ->
                vm.saveWorkdayPlan(planTarget.driverUid, planTarget.dateEpochDay, startMillis, endMillis)
                editingWorkday = null
            }
        )
    }
    }
}

/** Bilah aksi massal: jumlah terpilih, Tolak, Setujui. Hanya tampil saat ada pilihan. */
@Composable
private fun ExpatBulkActionBar(
    selectedCount: Int,
    actionsEnabled: Boolean,
    onReject: () -> Unit,
    onApprove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = Dimens.xs,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            Text(
                text = stringResource(R.string.expat_bulkbar_selected_format, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onReject, enabled = actionsEnabled) {
                Text(stringResource(R.string.expat_bulkbar_reject))
            }
            Button(onClick = onApprove, enabled = actionsEnabled) {
                Text(stringResource(R.string.expat_bulkbar_approve))
            }
        }
    }
}

@Composable
private fun ExpatMonthlySummaryStrip(
    monthlySummary: ExpatMonthlyCalendarSummary
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
            Text(
                text = stringResource(R.string.expat_review_monthly_summary_title),
                style = MaterialTheme.typography.titleSmall
            )
            if (monthlySummary.scheduleAvailable) {
                Text(
                    text = stringResource(
                        R.string.expat_review_monthly_summary_scheduled,
                        WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                            totalMinutes = monthlySummary.scheduledMinutes,
                            dash = stringResource(R.string.expat_placeholder_dash)
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.expat_review_schedule_unavailable),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(
                    R.string.expat_review_monthly_summary_actual,
                    WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                        totalMinutes = monthlySummary.actualMinutes,
                        dash = stringResource(R.string.expat_placeholder_dash)
                    )
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.expat_review_monthly_summary_overtime,
                    WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                        totalMinutes = monthlySummary.overtimeMinutes,
                        dash = stringResource(R.string.expat_placeholder_dash)
                    )
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ExpatReviewFocusStrip(
    visibleCount: Int,
    pendingCount: Int,
    selectedCount: Int,
    pendingOnly: Boolean
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
            Text(
                text = stringResource(R.string.expat_review_focus_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    if (pendingOnly) {
                        R.string.expat_review_focus_subtitle_pending_only
                    } else {
                        R.string.expat_review_focus_subtitle
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.expat_review_focus_counts,
                    visibleCount,
                    pendingCount,
                    selectedCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun WorkDayRow(
    item: WorkDay,
    driverLabel: String,
    checked: Boolean,
    enabled: Boolean,
    auditLine: ExpatWorkdayAuditLineUi,
    auditDateTimeFormatter: DateTimeFormatter,
    auditZoneId: ZoneId,
    dayCalendarMeta: ExpatDayCalendarMeta?,
    onCheckedChange: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
    onEditPlan: () -> Unit
) {
    val zone = TimeZone.of("Asia/Jakarta")
    val dateText = expatDateLabel(item.dateEpochDay, rememberExpatLocale())
    val placeholderDash = stringResource(R.string.expat_placeholder_dash)
    val inText = WorkdayTimeFormatUtil.formatClockOrDash(
        clockMillis = item.clockInMillis,
        dash = placeholderDash,
        zone = zone
    )
    val outText = WorkdayTimeFormatUtil.formatClockOrDash(
        clockMillis = item.clockOutMillis,
        dash = placeholderDash,
        zone = zone
    )
    val attendanceState = item.attendanceState()
    val isOpen = attendanceState == AttendanceDailyState.OPEN
    val approvalStatus = item.approvalStatus()
    val canSelect = enabled && approvalStatus == ApprovalStatus.PENDING && !isOpen
    val statusRes = approvalStatusRes(approvalStatus)
    val statusText = if (isOpen) {
        stringResource(R.string.expat_status_open)
    } else {
        stringResource(statusRes)
    }
    val auditLineText = resolveAuditLineText(
        auditLine = auditLine,
        formatter = auditDateTimeFormatter,
        zoneId = auditZoneId
    )
    val hasIssues = item.deviationFlags.isNotEmpty()
    val issueChipType = if (item.reviewHold) DMChipType.Issue else DMChipType.Pending

    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExpatMaestroTags.REVIEW_DAY_ITEM)
            .clickable { onOpenDetail() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = canSelect
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Text(dateText, style = MaterialTheme.typography.titleMedium)
                Text(driverLabel, style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.expat_label_in_out, inText, outText),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (auditLineText != null) {
                    Text(
                        text = auditLineText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
            ) {
                if (hasIssues) {
                    DMStatusChip(
                        type = issueChipType,
                        text = stringResource(R.string.expat_status_issue)
                    )
                }
                dayCalendarMeta?.overrideKind?.let { overrideKind ->
                    DMStatusChip(
                        type = DMChipType.Pending,
                        text = stringResource(overrideKind.toLabelRes())
                    )
                }
                if (isOpen) {
                    DMStatusChip(type = DMChipType.Pending, text = statusText)
                } else {
                    ExpatApprovalPill(status = approvalStatus, text = statusText, compact = true)
                }
                DmIconButton(
                    onClick = onEditPlan,
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.expat_section_work_time),
                    modifier = Modifier.size(Dimens.touch)
                )
                Text(stringResource(R.string.expat_review_detail_label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun approvalStatusRes(status: ApprovalStatus): Int = when (status) {
    ApprovalStatus.APPROVED -> R.string.driver_history_status_approved
    ApprovalStatus.REJECTED -> R.string.driver_history_status_rejected
    ApprovalStatus.PENDING -> R.string.driver_history_status_pending
}


private fun OverrideKind.toLabelRes(): Int = when (this) {
    OverrideKind.HOLIDAY -> R.string.expat_review_reason_holiday_off
    OverrideKind.WORKDAY_SWAP -> R.string.expat_review_reason_swap
    OverrideKind.CUSTOM -> R.string.expat_review_reason_custom
}

@Composable
private fun resolveUiText(text: UiText): String {
    return if (text.args.isEmpty()) {
        stringResource(text.resId)
    } else {
        stringResource(text.resId, *text.args.toTypedArray())
    }
}

@Composable
private fun resolveAuditLineText(
    auditLine: ExpatWorkdayAuditLineUi,
    formatter: DateTimeFormatter,
    zoneId: ZoneId
): String? {
    return when (auditLine) {
        ExpatWorkdayAuditLineUi.None -> null
        is ExpatWorkdayAuditLineUi.Approved -> {
            val approvedAt = auditLine.approvedAtMillis?.let { formatAuditDateTime(it, zoneId, formatter) }
            when {
                !auditLine.approverName.isNullOrBlank() && !approvedAt.isNullOrBlank() ->
                    stringResource(R.string.expat_audit_approved_by_time, auditLine.approverName, approvedAt)
                !approvedAt.isNullOrBlank() ->
                    stringResource(R.string.expat_audit_approved_time, approvedAt)
                else ->
                    stringResource(R.string.expat_audit_approved)
            }
        }
        is ExpatWorkdayAuditLineUi.Rejected -> {
            val rejectedAt = auditLine.rejectedAtMillis?.let { formatAuditDateTime(it, zoneId, formatter) }
            when {
                !auditLine.reason.isNullOrBlank() && !rejectedAt.isNullOrBlank() ->
                    stringResource(
                        R.string.expat_audit_rejected_reason_time,
                        auditLine.reason,
                        rejectedAt
                    )
                !auditLine.reason.isNullOrBlank() ->
                    stringResource(R.string.expat_audit_rejected_reason, auditLine.reason)
                !rejectedAt.isNullOrBlank() ->
                    stringResource(R.string.expat_audit_rejected_time, rejectedAt)
                else ->
                    stringResource(R.string.expat_audit_rejected)
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
