package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.navigation.floatingNavPadding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.core.model.AttendanceReferenceWindow
import com.drivemate.showcase.core.model.DriverTodaySchedule
import com.drivemate.showcase.core.model.PlanSource
import com.drivemate.showcase.core.model.computeAttendanceTimelineZones
import com.drivemate.showcase.core.model.resolveAttendanceReferenceWindow
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.ds.AutoDismissInlineAlert
import com.drivemate.showcase.ui.ds.InlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.util.WorkdayTimeFormatUtil
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import java.util.Locale

private enum class AttendanceAction { TapIn, TapOut }
private enum class AttendancePhase { Idle, Open, Closed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverAttendanceScreen(
    state: DriverUiState,
    checklistSubmitted: Boolean?,
    safetySubmitted: Boolean?,
    plannedStartMillis: Long?,
    plannedEndMillis: Long?,
    planSource: PlanSource?,
    onTapIn: () -> Unit,
    onTapOut: () -> Unit,
    onBack: () -> Unit,
    onBackHome: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    // "Complete" here means both daily items are submitted -- there is no
    // percentage of sub-fields to weigh, `hasChecklist`/`hasSafetyDeclaration`
    // are each a single yes/no per day. This is a UX nicety only: TapOutUseCase
    // enforces the same rule server/logic-side regardless of what this renders.
    val checklistGateComplete = checklistSubmitted == true && safetySubmitted == true
    val checklistGateCompletedCount = listOf(checklistSubmitted, safetySubmitted).count { it == true }
    val zone = TimeZone.of("Asia/Jakarta")
    val dateEpochDay = TimeUtil.todayEpochDay(zone).toInt()
    val locale = LocalConfiguration.current.locales[0]
    val dateText = WorkdayTimeFormatUtil.formatDateLabel(dateEpochDay, skeleton = "EEEEyMMMd").uppercase(locale)
    val todayAttendance = (state as? DriverUiState.Working)?.todayAttendance
    val attendancePhase = resolveAttendancePhase(todayAttendance)
    val isWorking = attendancePhase == AttendancePhase.Open
    val isCompleted = attendancePhase == AttendancePhase.Closed
    val statusWorking = stringResource(R.string.driver_status_working)
    val statusCompleted = stringResource(R.string.driver_status_completed)
    val statusIdle = stringResource(R.string.driver_status_idle)
    val presensiStatus = when {
        isWorking -> statusWorking
        isCompleted -> statusCompleted
        else -> statusIdle
    }.uppercase(locale)

    // Jendela referensi yang dipakai timeline datang dari GetDriverTodayScheduleUseCase
    // (lewat homeState, di-passing dari AppNavGraph) -- BUKAN dihardcode di sini. Kalau
    // driver belum punya plan (workday_plans/workdays kosong), resolveAttendanceReferenceWindow
    // jatuh ke jam kerja standar 07:30/16:30 dari WorkDay.kt (satu sumber kebenaran yang
    // sama dipakai bridge iOS via defaultStartMillis/defaultEndMillis).
    val schedule = remember(plannedStartMillis, plannedEndMillis, planSource) {
        DriverTodaySchedule(plannedStartMillis, plannedEndMillis, planSource)
    }
    val referenceWindow = remember(schedule, dateEpochDay, zone) {
        resolveAttendanceReferenceWindow(schedule, dateEpochDay, zone)
    }

    // Detak hidup untuk timer HH:MM:SS dan posisi "sekarang" di timeline. Berhenti
    // begitu Closed -- hari sudah selesai, tidak ada lagi yang perlu diperbarui.
    val liveNowMillis = rememberTickingMillis(
        enabled = attendancePhase != AttendancePhase.Closed,
        intervalMillis = 1_000L
    )

    var pendingAction by rememberSaveable { mutableStateOf<AttendanceAction?>(null) }
    var successAction by rememberSaveable { mutableStateOf<AttendanceAction?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var infoBanner by remember { mutableStateOf<String?>(null) }
    val errorMessage = (state as? DriverUiState.Error)?.messageRes?.let { stringResource(it) }
    val infoMessage = (state as? DriverUiState.Working)?.messageRes?.let { stringResource(it) }

    LaunchedEffect(state) {
        if (state is DriverUiState.Error) {
            errorBanner = errorMessage
        }
        if (state is DriverUiState.Working && !infoMessage.isNullOrBlank()) {
            infoBanner = infoMessage
        }
    }

    LaunchedEffect(state, pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        when (action) {
            AttendanceAction.TapIn -> {
                if (state is DriverUiState.Working && attendancePhase != AttendancePhase.Idle) {
                    if (state.messageRes != R.string.driver_error_attendance_tap_in_failed) {
                        successAction = action
                    }
                    pendingAction = null
                }
            }
            AttendanceAction.TapOut -> {
                if (state is DriverUiState.Working && (
                        attendancePhase == AttendancePhase.Closed ||
                            state.messageRes == R.string.driver_error_attendance_tap_out_failed
                        )
                ) {
                    if (attendancePhase == AttendancePhase.Closed &&
                        state.messageRes != R.string.driver_error_attendance_tap_out_failed
                    ) {
                        successAction = action
                    }
                    pendingAction = null
                }
            }
        }
        if (state is DriverUiState.Error) {
            pendingAction = null
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // contentWindowInsets nol, BUKAN default: layar ini sudah dibungkus
    // DriveMateRootScaffold, yang menyerahkan innerPadding mencakup status bar +
    // navigation bar ke setiap route non-full-bleed (pola yang sama seperti
    // DriverHomeScreen). Tanpa ini, Scaffold di sini menghitung inset yang SAMA
    // untuk kedua kalinya -- di portrait efeknya tersamar karena kontennya
    // scrollable, tapi di landscape padding-bawah ganda itu memakan hampir
    // seluruh ruang yang seharusnya untuk Row hero+timeline (weight(1f) hanya
    // kebagian sisa setelah padding ganda dipotong) -- ditemukan lewat QA nyata
    // di emulator: kartu hero/timeline landscape ter-clip parah sampai nyaris
    // tak terlihat.
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(DriverMaestroTags.ATTENDANCE_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_attendance_screen_title),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state is DriverUiState.Error -> DriverErrorState(
                message = stringResource(state.messageRes),
                onRetry = onRefresh,
                modifier = Modifier
                    .padding(padding)
                    .testTag(DriverMaestroTags.ATTENDANCE_ERROR_STATE)
            )
            state is DriverUiState.Loading -> DriverAttendanceLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md)
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Tanpa verticalArrangement.spacedBy: saat ketiga banner tersembunyi,
                // Column ini benar-benar nol tinggi -- tidak ada arrangement yang
                // menyisipkan jarak mati (bug yang sama pernah ditemukan di Driver Home).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dsv2Spacing.lg)
                ) {
                    DriverNetworkBanner(modifier = Modifier.fillMaxWidth())
                    AutoDismissInlineAlert(
                        kind = InlineAlertKind.Warning,
                        message = infoBanner,
                        onDismiss = { infoBanner = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AutoDismissInlineAlert(
                        kind = InlineAlertKind.Error,
                        message = errorBanner,
                        onDismiss = { errorBanner = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val checklistDetailVisible = attendancePhase == AttendancePhase.Open && !checklistGateComplete

                if (isLandscape) {
                    AttendanceLandscapeContent(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        presensiStatus = presensiStatus,
                        dateText = dateText,
                        attendancePhase = attendancePhase,
                        todayAttendance = todayAttendance,
                        liveNowMillis = liveNowMillis,
                        referenceWindow = referenceWindow,
                        checklistDetailVisible = checklistDetailVisible,
                        checklistSubmitted = checklistSubmitted,
                        safetySubmitted = safetySubmitted
                    )
                } else {
                    AttendancePortraitContent(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        presensiStatus = presensiStatus,
                        dateText = dateText,
                        attendancePhase = attendancePhase,
                        todayAttendance = todayAttendance,
                        liveNowMillis = liveNowMillis,
                        referenceWindow = referenceWindow,
                        checklistDetailVisible = checklistDetailVisible,
                        checklistSubmitted = checklistSubmitted,
                        safetySubmitted = safetySubmitted
                    )
                }

                // Peringatan gate + CTA sticky di bawah, DI LUAR area scroll --
                // ditemukan lewat QA nyata di emulator: pada skenario lembur,
                // konten scrollable (hero + timeline + detail checklist) jadi
                // lebih tinggi dari viewport, dan tombol CTA -- satu-satunya aksi
                // utama layar ini -- tertelan di bawah lipatan, hanya sudut atasnya
                // yang kelihatan. CTA yang butuh di-scroll dulu untuk ditemukan
                // gagal "single dominant action" yang jadi tujuan redesign ini.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                ) {
                    if (attendancePhase == AttendancePhase.Open && !checklistGateComplete) {
                        InlineAlert(
                            kind = InlineAlertKind.Warning,
                            message = stringResource(
                                R.string.driver_attendance_checklist_gate_warning,
                                checklistGateCompletedCount,
                                2
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(DriverMaestroTags.CHECKLIST_GATE_WARNING)
                        )
                    }
                    AttendanceCtaButton(
                        modifier = Modifier.fillMaxWidth(),
                        attendancePhase = attendancePhase,
                        checklistGateComplete = checklistGateComplete,
                        pendingAction = pendingAction,
                        onTapIn = { pendingAction = AttendanceAction.TapIn; onTapIn() },
                        onTapOut = { pendingAction = AttendanceAction.TapOut; onTapOut() },
                        onBackHome = onBackHome
                    )
                }
            }
        }
    }

    if (successAction != null) {
        val successText = if (successAction == AttendanceAction.TapIn) {
            stringResource(R.string.driver_attendance_success_in)
        } else {
            stringResource(R.string.driver_attendance_success_out)
        }
        DriverSuccessDialog(
            title = stringResource(R.string.driver_dialog_success_title),
            message = successText,
            onConfirm = {
                successAction = null
                onBack()
            }
        )
    }
}

@Composable
private fun AttendancePortraitContent(
    modifier: Modifier,
    presensiStatus: String,
    dateText: String,
    attendancePhase: AttendancePhase,
    todayAttendance: TodayAttendanceUi?,
    liveNowMillis: Long,
    referenceWindow: AttendanceReferenceWindow,
    checklistDetailVisible: Boolean,
    checklistSubmitted: Boolean?,
    safetySubmitted: Boolean?
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md)
            .floatingNavPadding(),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.lg)
    ) {
        AttendanceHeroTimerCard(
            modifier = Modifier.fillMaxWidth(),
            presensiStatus = presensiStatus,
            dateText = dateText,
            attendancePhase = attendancePhase,
            todayAttendance = todayAttendance,
            liveNowMillis = liveNowMillis
        )
        AttendanceTimelineCard(
            modifier = Modifier.fillMaxWidth(),
            attendancePhase = attendancePhase,
            todayAttendance = todayAttendance,
            liveNowMillis = liveNowMillis,
            referenceWindow = referenceWindow
        )
        if (checklistDetailVisible) {
            AttendanceChecklistDetailCard(
                modifier = Modifier.fillMaxWidth(),
                checklistSubmitted = checklistSubmitted,
                safetySubmitted = safetySubmitted
            )
        }
    }
}

@Composable
private fun AttendanceLandscapeContent(
    modifier: Modifier,
    presensiStatus: String,
    dateText: String,
    attendancePhase: AttendancePhase,
    todayAttendance: TodayAttendanceUi?,
    liveNowMillis: Long,
    referenceWindow: AttendanceReferenceWindow,
    checklistDetailVisible: Boolean,
    checklistSubmitted: Boolean?,
    safetySubmitted: Boolean?
) {
    Row(
        modifier = modifier.padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
    ) {
        AttendanceHeroTimerCard(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .floatingNavPadding(),
            presensiStatus = presensiStatus,
            dateText = dateText,
            attendancePhase = attendancePhase,
            todayAttendance = todayAttendance,
            liveNowMillis = liveNowMillis,
            compact = true
        )
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .floatingNavPadding(),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
        ) {
            AttendanceTimelineCard(
                modifier = Modifier.fillMaxWidth(),
                attendancePhase = attendancePhase,
                todayAttendance = todayAttendance,
                liveNowMillis = liveNowMillis,
                referenceWindow = referenceWindow
            )
            if (checklistDetailVisible) {
                AttendanceChecklistDetailCard(
                    modifier = Modifier.fillMaxWidth(),
                    checklistSubmitted = checklistSubmitted,
                    safetySubmitted = safetySubmitted
                )
            }
        }
    }
}

@Composable
private fun AttendanceCtaButton(
    modifier: Modifier,
    attendancePhase: AttendancePhase,
    checklistGateComplete: Boolean,
    pendingAction: AttendanceAction?,
    onTapIn: () -> Unit,
    onTapOut: () -> Unit,
    onBackHome: () -> Unit
) {
    // Single kontekstual CTA -- tidak pernah Tap In dan Tap Out bersamaan.
    // Tap Out tetap `enabled` walau gate belum lengkap (bukan `enabled = false`)
    // supaya tap-nya bisa ditangkap untuk haptic Reject alih-alih diam saja;
    // treatment visual dim 0.6 alpha (`visuallyLocked`) tetap identik dengan
    // tombol disabled yang lama.
    when (attendancePhase) {
        AttendancePhase.Closed -> {
            DriverPrimaryButton(
                text = stringResource(R.string.driver_back_home),
                onClick = onBackHome,
                hapticFeedbackType = HapticFeedbackType.Confirm,
                modifier = modifier.testTag(DriverMaestroTags.ATTENDANCE_BACK_HOME_BUTTON)
            )
        }
        AttendancePhase.Idle -> {
            val tapInEnabled = pendingAction == null
            DriverPrimaryButton(
                text = stringResource(R.string.driver_attendance_button_tap_in),
                onClick = onTapIn,
                enabled = tapInEnabled,
                loading = pendingAction == AttendanceAction.TapIn,
                spinnerOnly = true,
                hapticFeedbackType = HapticFeedbackType.Confirm,
                modifier = modifier.testTag(DriverMaestroTags.TAP_IN_BUTTON)
            )
        }
        AttendancePhase.Open -> {
            val tapOutEligible = pendingAction == null && checklistGateComplete
            val tapOutBlocked = pendingAction == null && !checklistGateComplete
            DriverPrimaryButton(
                text = stringResource(R.string.driver_attendance_button_tap_out),
                onClick = { if (tapOutEligible) onTapOut() },
                enabled = tapOutEligible || tapOutBlocked,
                visuallyLocked = tapOutBlocked,
                loading = pendingAction == AttendanceAction.TapOut,
                spinnerOnly = true,
                hapticFeedbackType = if (tapOutEligible) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                modifier = modifier.testTag(DriverMaestroTags.TAP_OUT_BUTTON)
            )
        }
    }
}

@Composable
private fun AttendanceHeroTimerCard(
    modifier: Modifier,
    presensiStatus: String,
    dateText: String,
    attendancePhase: AttendancePhase,
    todayAttendance: TodayAttendanceUi?,
    liveNowMillis: Long,
    compact: Boolean = false
) {
    val elapsedMillis = when (attendancePhase) {
        AttendancePhase.Open -> {
            val clockIn = todayAttendance?.clockInMillis
            if (clockIn != null) (liveNowMillis - clockIn).coerceAtLeast(0L) else null
        }
        AttendancePhase.Closed -> {
            val clockIn = todayAttendance?.clockInMillis
            val clockOut = todayAttendance?.clockOutMillis
            if (clockIn != null && clockOut != null) (clockOut - clockIn).coerceAtLeast(0L) else null
        }
        AttendancePhase.Idle -> null
    }
    val timerText = elapsedMillis?.let { formatElapsedHms(it) }
        ?: stringResource(R.string.driver_attendance_timer_placeholder)
    val tapInTimeText = WorkdayTimeFormatUtil.formatClockOrNull(todayAttendance?.clockInMillis)

    HeroShell(
        modifier = modifier.testTag(DriverMaestroTags.DAILY_STATUS),
        // Landscape punya jauh lebih sedikit tinggi tersedia (lebar layar jadi
        // tinggi) -- displayMedium (~45sp) untuk timer di situ akan ter-clip.
        // headlineMedium + padding lebih rapat menjaga ketiga baris konten hero
        // tetap muat tanpa terpotong, ditemukan lewat QA nyata di emulator.
        contentPadding = if (compact) Dsv2Spacing.md else Dsv2Spacing.lg
    ) {
        val statusIcon = when (attendancePhase) {
            AttendancePhase.Open -> Icons.Outlined.PlayCircle
            AttendancePhase.Closed -> Icons.Outlined.CheckCircle
            AttendancePhase.Idle -> Icons.Outlined.Schedule
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                // LocalContentColor.current (bukan AppPalette.onPrimary
                // langsung): HeroShell menyediakan wordmarkPrimary lewat
                // provider ini -- gradien hero tetap gelap di light maupun
                // dark mode, tapi onPrimary ikut tema dan jadi gelap di dark
                // mode, membuat ikon nyaris tak terbaca di sana.
                tint = androidx.compose.material3.LocalContentColor.current,
                modifier = Modifier.size(Dsv2Sizing.icon)
            )
            val inOnlyText = if (tapInTimeText != null) {
                stringResource(R.string.driver_time_range_in_only, tapInTimeText)
            } else {
                null
            }
            // Mode compact (landscape) tidak punya ruang vertikal untuk baris
            // "Masuk HH:mm" terpisah -- digabung ke baris status/tanggal yang
            // sama alih-alih dihilangkan, supaya info jam masuk tetap ada di
            // kedua orientasi.
            val statusLineText = if (compact && inOnlyText != null) {
                "$presensiStatus · $dateText · $inOnlyText"
            } else {
                "$presensiStatus · $dateText"
            }
            Text(
                text = statusLineText,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
            )
        }
        Text(
            text = timerText,
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium
        )
        if (tapInTimeText != null && !compact) {
            Text(
                text = stringResource(R.string.driver_time_range_in_only, tapInTimeText),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.78f)
            )
        }
    }
}

private fun formatElapsedHms(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

@Composable
private fun rememberTickingMillis(enabled: Boolean, intervalMillis: Long): Long {
    val state = produceState(initialValue = TimeUtil.nowEpochMillis(), enabled, intervalMillis) {
        if (enabled) {
            while (true) {
                value = TimeUtil.nowEpochMillis()
                delay(intervalMillis)
            }
        }
    }
    return state.value
}

@Composable
private fun AttendanceTimelineCard(
    modifier: Modifier,
    attendancePhase: AttendancePhase,
    todayAttendance: TodayAttendanceUi?,
    liveNowMillis: Long,
    referenceWindow: AttendanceReferenceWindow
) {
    val zonesNowOrClockOut = when (attendancePhase) {
        AttendancePhase.Idle -> referenceWindow.startMillis
        AttendancePhase.Open -> liveNowMillis
        AttendancePhase.Closed -> todayAttendance?.clockOutMillis ?: referenceWindow.endMillis
    }
    val zonesClockIn = if (attendancePhase == AttendancePhase.Idle) null else todayAttendance?.clockInMillis
    val zones = remember(referenceWindow, zonesClockIn, zonesNowOrClockOut) {
        computeAttendanceTimelineZones(
            referenceWindow = referenceWindow,
            clockInMillis = zonesClockIn,
            nowOrClockOutMillis = zonesNowOrClockOut
        )
    }
    val carPositionBasisMillis = when (attendancePhase) {
        AttendancePhase.Closed -> todayAttendance?.clockOutMillis ?: referenceWindow.endMillis
        else -> liveNowMillis
    }

    val normalMin = ((referenceWindow.endMillis - referenceWindow.startMillis) / 60_000L).toInt().coerceAtLeast(1)
    val earlyMin = zones.earlyOvertimeMinutes
    val lateMin = zones.lateOvertimeMinutes
    val totalMin = earlyMin + normalMin + lateMin
    val carOffsetFromWindowStartMin = ((carPositionBasisMillis - referenceWindow.startMillis) / 60_000L).toInt()
    val carOffsetFromBarStartMin = (carOffsetFromWindowStartMin + earlyMin).coerceIn(0, totalMin)
    val carFraction = carOffsetFromBarStartMin.toFloat() / totalMin.toFloat()

    val earlyColor = AppPalette.accent
    val normalColor = AppPalette.primary
    val lateColor = AppPalette.warning

    DriverCard(
        modifier = modifier.testTag(DriverMaestroTags.ATTENDANCE_TIMELINE_CARD)
    ) {
        Text(
            text = stringResource(R.string.driver_attendance_timeline_title),
            style = MaterialTheme.typography.titleMedium
        )

        // Label "+Xm awal"/"+Xm lembur" -- diposisikan via BiasAlignment di titik
        // tengah segmennya, BUKAN Row proporsional-weight: overtime singkat (mis.
        // 5 menit dari total ~9 jam) menghasilkan slot weight yang jauh lebih
        // sempit dari lebar teksnya sendiri, memaksa teks wrap 3-4 baris vertikal
        // dan mendorong kartu checklist + tombol CTA di bawahnya keluar layar.
        // BiasAlignment membiarkan teks selalu selebar alaminya di posisi yang benar.
        if (earlyMin > 0 || lateMin > 0) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (earlyMin > 0) {
                    Text(
                        text = stringResource(R.string.driver_attendance_timeline_early_label, earlyMin),
                        style = MaterialTheme.typography.labelSmall,
                        color = earlyColor,
                        modifier = Modifier.align(BiasAlignment(fractionToBias(earlyMin / 2f / totalMin), 0f))
                    )
                }
                if (lateMin > 0) {
                    Text(
                        text = stringResource(R.string.driver_attendance_timeline_late_label, lateMin),
                        style = MaterialTheme.typography.labelSmall,
                        color = lateColor,
                        modifier = Modifier.align(
                            BiasAlignment(fractionToBias((earlyMin + normalMin + lateMin / 2f) / totalMin), 0f)
                        )
                    )
                }
            }
        }

        val nowContentDescription = stringResource(
            R.string.driver_attendance_timeline_now_content_description,
            WorkdayTimeFormatUtil.formatClockOrDash(carPositionBasisMillis, "--:--")
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_BAR_HEIGHT + TIMELINE_CAR_HEIGHT / 2)
        ) {
            val barWidthDp = maxWidth
            val carOffsetDp = ((barWidthDp - TIMELINE_CAR_WIDTH) * carFraction)
                .coerceIn(0.dp, barWidthDp - TIMELINE_CAR_WIDTH)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIMELINE_BAR_HEIGHT)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(TIMELINE_BAR_HEIGHT / 2))
            ) {
                if (earlyMin > 0) {
                    Box(
                        modifier = Modifier
                            .weight(earlyMin.toFloat())
                            .fillMaxHeight()
                            .background(earlyColor.copy(alpha = 0.55f))
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(normalMin.toFloat())
                        .fillMaxHeight()
                        .background(normalColor.copy(alpha = 0.28f))
                )
                if (lateMin > 0) {
                    Box(
                        modifier = Modifier
                            .weight(lateMin.toFloat())
                            .fillMaxHeight()
                            .background(lateColor.copy(alpha = 0.55f))
                    )
                }
            }

            DriverCarIndicator(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = carOffsetDp)
                    .semantics { contentDescription = nowContentDescription },
                bodyColor = normalColor,
                windowColor = MaterialTheme.colorScheme.surface,
                wheelColor = MaterialTheme.colorScheme.onSurface,
                groundShadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
            )
        }

        // Label jam di ujung kiri/kanan bar SAJA -- bukan 4 label di tiap batas
        // segmen. Window-start/window-end di TENGAH bar hanya dipakai sebagai
        // label ujung ketika overtime di sisi itu tidak ada; begitu overtime ada,
        // label window-boundary "di tengah" itu jadi terlalu dekat dengan label
        // ujung overtime di sebelahnya (bisa bertabrakan/berdempetan kalau
        // overtime-nya cuma beberapa menit) -- dua label yang SELALU dipatok di
        // bias -1/+1 dijamin tidak pernah bertabrakan berapa pun proporsi segmennya.
        val leftTickMillis = if (earlyMin > 0) zonesClockIn else referenceWindow.startMillis
        val rightTickMillis = if (lateMin > 0) zonesNowOrClockOut else referenceWindow.endMillis
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = WorkdayTimeFormatUtil.formatClockOrDash(leftTickMillis, "--:--"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(BiasAlignment(-1f, 0f))
            )
            Text(
                text = WorkdayTimeFormatUtil.formatClockOrDash(rightTickMillis, "--:--"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(BiasAlignment(1f, 0f))
            )
        }
    }
}

private fun fractionToBias(fraction: Float): Float = (fraction.coerceIn(0f, 1f) * 2f - 1f)

private val TIMELINE_BAR_HEIGHT = 20.dp
private val TIMELINE_CAR_WIDTH = 44.dp
private val TIMELINE_CAR_HEIGHT = 34.dp

/**
 * Siluet mobil datar (bodi + kabin/kaca depan miring + dua roda dengan detail
 * hub) sebagai penanda posisi waktu "sekarang" di timeline. Bentuknya flat
 * vector via Canvas/Path, konsisten dengan bahasa visual abstrak Neural Wheel
 * -- bukan ikon literal/fotografis. Bounce halus + roda berputar + garis
 * gerak kecil + bayangan tanah, semuanya menghormati reduce-motion.
 */
@Composable
private fun DriverCarIndicator(
    modifier: Modifier = Modifier,
    bodyColor: Color,
    windowColor: Color,
    wheelColor: Color,
    groundShadowColor: Color
) {
    val reduceMotion = LocalReduceMotion.current
    val infiniteTransition = rememberInfiniteTransition(label = "driverCarIndicator")
    val bounce = if (reduceMotion) {
        0f
    } else {
        val animated by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "carBounce"
        )
        animated
    }
    val wheelRotation = if (reduceMotion) {
        0f
    } else {
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing)
            ),
            label = "wheelSpin"
        )
        animated
    }

    Canvas(modifier = modifier.size(width = TIMELINE_CAR_WIDTH, height = TIMELINE_CAR_HEIGHT)) {
        val w = size.width
        val h = size.height
        val bounceOffsetPx = bounce * (h * 0.05f)

        drawOval(
            color = groundShadowColor,
            topLeft = Offset(w * 0.10f, h * 0.88f),
            size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.10f)
        )

        val motionLineYs = listOf(h * 0.38f, h * 0.50f, h * 0.62f)
        motionLineYs.forEachIndexed { index, y ->
            drawLine(
                color = wheelColor.copy(alpha = 0.35f),
                start = Offset(w * -0.06f, y + bounceOffsetPx),
                end = Offset(w * (0.06f - index * 0.02f), y + bounceOffsetPx),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        translate(top = bounceOffsetPx) {
            val bodyPath = Path().apply {
                moveTo(w * 0.12f, h * 0.62f)
                lineTo(w * 0.20f, h * 0.62f)
                lineTo(w * 0.30f, h * 0.34f)
                lineTo(w * 0.45f, h * 0.20f)
                lineTo(w * 0.68f, h * 0.20f)
                lineTo(w * 0.80f, h * 0.34f)
                lineTo(w * 0.90f, h * 0.34f)
                cubicTo(
                    w * 0.96f, h * 0.34f,
                    w * 0.98f, h * 0.40f,
                    w * 0.98f, h * 0.48f
                )
                lineTo(w * 0.98f, h * 0.62f)
                lineTo(w * 0.86f, h * 0.62f)
                close()
            }
            drawPath(path = bodyPath, color = bodyColor)

            val windowPath = Path().apply {
                moveTo(w * 0.34f, h * 0.34f)
                lineTo(w * 0.46f, h * 0.24f)
                lineTo(w * 0.66f, h * 0.24f)
                lineTo(w * 0.76f, h * 0.34f)
                close()
            }
            drawPath(path = windowPath, color = windowColor)

            val wheelRadius = h * 0.14f
            val wheelCenters = listOf(
                Offset(w * 0.28f, h * 0.64f),
                Offset(w * 0.80f, h * 0.64f)
            )
            wheelCenters.forEach { c ->
                drawCircle(color = wheelColor, radius = wheelRadius, center = c)
                drawCircle(color = bodyColor, radius = wheelRadius * 0.45f, center = c)
                rotate(degrees = wheelRotation, pivot = c) {
                    drawLine(
                        color = wheelColor,
                        start = c,
                        end = Offset(c.x, c.y - wheelRadius * 0.42f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = wheelColor,
                        start = c,
                        end = Offset(c.x - wheelRadius * 0.36f, c.y + wheelRadius * 0.24f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = wheelColor,
                        start = c,
                        end = Offset(c.x + wheelRadius * 0.36f, c.y + wheelRadius * 0.24f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceChecklistDetailCard(
    modifier: Modifier,
    checklistSubmitted: Boolean?,
    safetySubmitted: Boolean?
) {
    val checklistTitle = stringResource(R.string.driver_action_checklist_title)
    val safetyTitle = stringResource(R.string.driver_action_safety_title)
    val statusWord = stringResource(R.string.driver_action_status_not_submitted)

    val incompleteItems = buildList {
        if (checklistSubmitted != true) add(checklistTitle)
        if (safetySubmitted != true) add(safetyTitle)
    }
    if (incompleteItems.isEmpty()) return

    DriverCard(
        modifier = modifier.testTag(DriverMaestroTags.ATTENDANCE_CHECKLIST_DETAIL_CARD)
    ) {
        Text(
            text = stringResource(R.string.driver_attendance_checklist_detail_title),
            style = MaterialTheme.typography.titleMedium
        )
        incompleteItems.forEach { title ->
            val description = stringResource(
                R.string.driver_action_status_content_description,
                title,
                statusWord
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AppPalette.warningIcon,
                    modifier = Modifier.size(Dsv2Sizing.icon)
                )
                Column {
                    Text(text = title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = statusWord,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverAttendanceLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.lg)
    ) {
        DriverCard(modifier = Modifier.fillMaxWidth()) {
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(AppSpacing.md))
            SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(AppSpacing.xxl))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.md))
        }
        DriverCard(modifier = Modifier.fillMaxWidth()) {
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.xl))
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.xxl))
    }
}

private fun resolveAttendancePhase(todayAttendance: TodayAttendanceUi?): AttendancePhase {
    val clockIn = todayAttendance?.clockInMillis
    val clockOut = todayAttendance?.clockOutMillis
    return when {
        clockIn == null -> AttendancePhase.Idle
        clockOut != null && clockOut > clockIn -> AttendancePhase.Closed
        else -> AttendancePhase.Open
    }
}
