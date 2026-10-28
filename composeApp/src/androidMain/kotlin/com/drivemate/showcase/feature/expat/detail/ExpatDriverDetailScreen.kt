package com.drivemate.showcase.feature.expat.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.drivemate.showcase.components.DMChipType
import com.drivemate.showcase.components.DMListCard
import com.drivemate.showcase.components.DMStatusChip
import com.drivemate.showcase.core.model.WorkDay
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.core.model.approvalStatus
import com.drivemate.showcase.core.model.endOfWorkMillis
import com.drivemate.showcase.core.model.startOfWorkMillis
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.core.model.displayOvertimeMinutesOrNull
import com.drivemate.showcase.core.model.displayTotalMinutesOrNull
import com.drivemate.showcase.feature.expat.ExpatMaestroTags
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.feature.workday.WorkdayPlanEditDialog
import com.drivemate.showcase.theme.Dimens
import com.drivemate.showcase.ui.SectionTitle
import com.drivemate.showcase.util.safeDriverLabel
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import com.drivemate.showcase.ui.dsv2.DmElevatedCard
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmErrorState
import com.drivemate.showcase.ui.dsv2.DmIconButton
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpatDriverDetailScreen(
    vm: ExpatDriverDetailViewModel,
    driverUid: String,
    onBack: () -> Unit
) {
    val attendance by vm.attendance.collectAsState()
    val checklists by vm.checklists.collectAsState()
    val safety by vm.safety.collectAsState()
    val workdays by vm.workdays.collectAsState()
    val location by vm.location.collectAsState()
    val sopStatus by vm.sopStatus.collectAsState()
    val driverName by vm.driverName.collectAsState()
    val loading by vm.loading.collectAsState()
    val errorRes by vm.error.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingWorkday by remember { mutableStateOf<WorkDay?>(null) }
    val rangeDays = 60
    val tabs = listOf(
        stringResource(R.string.expat_tab_attendance),
        stringResource(R.string.expat_tab_checklist),
        stringResource(R.string.expat_tab_safety),
        stringResource(R.string.expat_tab_workdays)
    )
    val zone = TimeZone.of("Asia/Jakarta")
    val placeholderDash = stringResource(R.string.expat_placeholder_dash)

    LaunchedEffect(driverUid) { vm.load(driverUid, rangeDays) }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DmScreenHeader(title = stringResource(R.string.expat_driver_detail_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ExpatMaestroTags.DRIVER_DETAIL_ROOT)
                .padding(padding)
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.screenPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            if (loading) {
                val loadingCd = stringResource(R.string.expat_state_loading_cd)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = loadingCd },
                    verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                ) {
                    repeat(4) { DmSkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 64.dp)) }
                }
                return@Column
            }
            if (errorRes != null) {
                DmErrorState(
                    message = errorRes?.let { stringResource(it) }.orEmpty(),
                    retryText = stringResource(R.string.expat_state_error_retry),
                    onRetry = { vm.load(driverUid, rangeDays) }
                )
            }

            DmElevatedCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.expat_section_driver))
                Text(safeDriverLabel(driverName, placeholder = stringResource(R.string.expat_driver_fallback)))
                Text(stringResource(R.string.expat_label_attendance_count, attendance.size))
                Text(stringResource(R.string.expat_label_checklist_count, checklists.size))
                Text(stringResource(R.string.expat_label_safety_count, safety.size))
                Text(stringResource(R.string.expat_label_workdays_count, workdays.size))
                val sopLabel = sopStatus?.let {
                    if (it.sopAccepted) {
                        stringResource(R.string.expat_status_sop_accepted)
                    } else {
                        stringResource(R.string.expat_status_sop_pending)
                    }
                } ?: placeholderDash
                Text(stringResource(R.string.expat_label_sop, sopLabel))
                location?.let { loc ->
                    val day = TimeUtil.epochDayOf(loc.updatedAtMillis, zone).toInt()
                    val dateText = expatDateLabel(day, rememberExpatLocale())
                    val timeText = WorkdayTimeFormatUtil.formatClockOrDash(
                        clockMillis = loc.updatedAtMillis,
                        dash = placeholderDash,
                        zone = zone
                    )
                    val locationText = loc.locationName ?: stringResource(R.string.expat_location_unavailable)
                    Text(stringResource(R.string.expat_label_location, locationText))
                    Text(stringResource(R.string.expat_label_update, dateText, timeText))
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab, divider = {}) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            when (selectedTab) {
                0 -> AttendanceTab(attendance)
                1 -> ChecklistTab(checklists)
                2 -> SafetyTab(safety)
                else -> WorkdaysTab(workdays) { editingWorkday = it }
            }
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
            errorRes = R.string.expat_error_workdays_load_failed,
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

@Composable
private fun AttendanceTab(items: List<com.drivemate.showcase.core.model.AttendanceEntry>) {
    if (items.isEmpty()) {
        DmEmptyState(
            title = stringResource(R.string.expat_empty_title),
            message = stringResource(R.string.expat_empty_message_attendance)
        )
        return
    }
    val zone = TimeZone.of("Asia/Jakarta")
    val placeholderDash = stringResource(R.string.expat_placeholder_dash)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        items(items, key = { it.id }) { item ->
            val day = TimeUtil.epochDayOf(item.timestampMillis, zone).toInt()
            val dateText = expatDateLabel(day, rememberExpatLocale())
            val timeText = WorkdayTimeFormatUtil.formatClockOrDash(
                clockMillis = item.timestampMillis,
                dash = placeholderDash,
                zone = zone
            )
            val chipType = if (item.type == "IN") DMChipType.In else DMChipType.Out
            DMListCard(
                title = "$dateText $timeText",
                subtitle = item.locationName ?: stringResource(R.string.expat_location_unavailable),
                trailingContent = { DMStatusChip(type = chipType, text = item.type) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChecklistTab(items: List<com.drivemate.showcase.core.model.ChecklistSummary>) {
    if (items.isEmpty()) {
        DmEmptyState(
            title = stringResource(R.string.expat_empty_title),
            message = stringResource(R.string.expat_empty_message_checklist)
        )
        return
    }
    val zone = TimeZone.of("Asia/Jakarta")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        items(items, key = { it.id }) { item ->
            val dateText = TimeUtil.formatYmdFromEpochDay(item.dateEpochDay, zone)
            val status = if (item.hasIssue) {
                stringResource(R.string.expat_status_issue)
            } else {
                stringResource(R.string.expat_status_ok)
            }
            val chipType = if (item.hasIssue) DMChipType.Issue else DMChipType.Ok
            DMListCard(
                title = dateText,
                subtitle = stringResource(R.string.expat_checklist_vehicle_label),
                trailingContent = { DMStatusChip(type = chipType, text = status) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SafetyTab(items: List<com.drivemate.showcase.core.model.SafetySummary>) {
    if (items.isEmpty()) {
        DmEmptyState(
            title = stringResource(R.string.expat_empty_title),
            message = stringResource(R.string.expat_empty_message_safety)
        )
        return
    }
    val zone = TimeZone.of("Asia/Jakarta")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        items(items, key = { it.id }) { item ->
            val dateText = TimeUtil.formatYmdFromEpochDay(item.dateEpochDay, zone)
            val status = if (!item.confirmed) {
                stringResource(R.string.expat_status_pending)
            } else if (item.fitToWork) {
                stringResource(R.string.expat_status_fit)
            } else {
                stringResource(R.string.expat_status_not_fit)
            }
            val chipType = when {
                !item.confirmed -> DMChipType.Pending
                item.fitToWork -> DMChipType.Ok
                else -> DMChipType.Issue
            }
            DMListCard(
                title = dateText,
                subtitle = stringResource(R.string.expat_safety_label),
                trailingContent = { DMStatusChip(type = chipType, text = status) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WorkdaysTab(
    items: List<WorkDay>,
    onEditPlan: (WorkDay) -> Unit
) {
    if (items.isEmpty()) {
        DmEmptyState(
            title = stringResource(R.string.expat_empty_title),
            message = stringResource(R.string.expat_empty_message_workdays)
        )
        return
    }
    val zone = TimeZone.of("Asia/Jakarta")
    val placeholderDash = stringResource(R.string.expat_placeholder_dash)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        items(items, key = { it.id }) { item ->
            val dateText = expatDateLabel(item.dateEpochDay, rememberExpatLocale())
            val approvalStatus = item.approvalStatus()
            val statusRes = approvalStatusRes(approvalStatus)
            val hasIssues = item.deviationFlags.isNotEmpty()
            val issueChipType = if (item.reviewHold) DMChipType.Issue else DMChipType.Pending
            DMListCard(
                title = dateText,
                subtitle = stringResource(
                    R.string.expat_label_total_overtime,
                    WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                        totalMinutes = item.displayTotalMinutesOrNull(),
                        dash = placeholderDash
                    ),
                    WorkdayTimeFormatUtil.formatDurationHoursMinutesOrDash(
                        totalMinutes = item.displayOvertimeMinutesOrNull(),
                        dash = placeholderDash
                    )
                ),
                trailingContent = {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
                    ) {
                        if (hasIssues) {
                            DMStatusChip(
                                type = issueChipType,
                                text = stringResource(R.string.expat_status_issue)
                            )
                        }
                        ExpatApprovalPill(status = approvalStatus, text = stringResource(statusRes), compact = true)
                        DmIconButton(
                            onClick = { onEditPlan(item) },
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.expat_section_work_time),
                            modifier = Modifier.size(Dimens.touch)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun approvalStatusRes(status: ApprovalStatus): Int = when (status) {
    ApprovalStatus.APPROVED -> R.string.driver_history_status_approved
    ApprovalStatus.REJECTED -> R.string.driver_history_status_rejected
    ApprovalStatus.PENDING -> R.string.driver_history_status_pending
}


// Privacy contract: never show UID-derived labels in UI.
