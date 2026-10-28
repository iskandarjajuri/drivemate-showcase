package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.navigation.floatingNavContentPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemate.showcase.R
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.domain.calendar.WorkHistoryDay
import com.drivemate.showcase.domain.calendar.WorkHistoryDayState
import com.drivemate.showcase.ui.dsv2.DmIconButton
import com.drivemate.showcase.ui.dsv2.Dsv2Badge3D
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.androidx.compose.koinViewModel
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// === Token warna dari SSOT (docs/design/work-history-agenda-view.md) ===
private val ColorPrimary = Color(0xFF2563EB)
private val ColorSuccess = Color(0xFF16A34A)
private val ColorSuccessLight = Color(0xFF4ADE80)
private val ColorWarning = Color(0xFFFBBF24)
private val ColorWarningDeep = Color(0xFFD97706)
private val ColorWarningInk = Color(0xFF92400E)
private val ColorDanger = Color(0xFFDC2626)
private val ColorNeutralFill = Color(0xFFE4E8F0)
private val ColorNeutralInk = Color(0xFF6B7686)

private val zone = TimeZone.of("Asia/Jakarta")

@Composable
fun DriverWorkHistoryAgendaScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriverWorkHistoryAgendaViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadCurrentMonth() }

    Scaffold(
        modifier = modifier,
        // Back di TopAppBar kiri-atas — konvensi navigasi Material yang sudah dipakai
        // seluruh layar driver lain lewat DriverTopBar.
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_history_title),
                onBack = onBack
            )
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                // Ekspos testTag sebagai resource-id supaya adb/Maestro bisa
                // menyeleksinya — pola sama seperti AuthScreen dan layar ber-tag lain.
                .semantics { testTagsAsResourceId = true }
                // CATATAN GESTUR (open item SSOT): swipe-ganti-bulan SUDAH DICOBA di sini
                // memakai detectHorizontalDragGestures pada Box induk, lalu DIBUANG —
                // pointerInput induk menelan event dan mematahkan scroll vertikal
                // LazyColumn (diverifikasi: 2x swipe vertikal tidak menggerakkan list
                // sama sekali). Navigasi bulan memakai chevron ‹ › saja, yang juga persis
                // bentuk yang diminta SSOT. Kalau nanti swipe tetap diinginkan, pasang di
                // level item non-scrollable (mis. MonthNavigator), bukan di container list.
        ) {
            if (state.loading && state.days.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                AgendaContent(state = state, onToggle = viewModel::toggleExpanded,
                    onPrev = viewModel::showPreviousMonth, onNext = viewModel::showNextMonth)
            }
        }
    }
}

@Composable
private fun AgendaContent(
    state: WorkHistoryAgendaUiState,
    onToggle: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DriverMaestroTags.HISTORY_AGENDA_LIST),
        contentPadding = floatingNavContentPadding(PaddingValues(Dsv2Spacing.md)),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
    ) {
        item(key = "month-nav") { MonthNavigator(state.year, state.month, onPrev, onNext) }
        item(key = "stats") { StatCardRow(state) }
        item(key = "overview") { MonthOverviewCard(state) }
        item(key = "detail-header") {
            Text(
                text = stringResource(R.string.driver_history_daily_detail_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (state.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.driver_history_empty_month),
                    color = ColorNeutralInk
                )
            }
        }
        items(state.days, key = { it.dateEpochDay }) { day ->
            AgendaRow(
                day = day,
                isToday = day.dateEpochDay == state.todayEpochDay,
                expanded = state.expandedEpochDay == day.dateEpochDay,
                onToggle = { onToggle(day.dateEpochDay) }
            )
        }
    }
}

/** `‹ [Bulan] [Tahun] ›` sesuai SSOT. */
@Composable
private fun MonthNavigator(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val label = if (year == 0) "" else {
        val m = java.time.Month.of(month).getDisplayName(JavaTextStyle.FULL, locale)
        "$m $year"
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DmIconButton(
            onClick = onPrev,
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.driver_history_prev_month),
            modifier = Modifier.testTag(DriverMaestroTags.HISTORY_PREV_MONTH)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(180.dp)
                .testTag(DriverMaestroTags.HISTORY_MONTH_LABEL)
        )
        DmIconButton(
            onClick = onNext,
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.driver_history_next_month),
            modifier = Modifier.testTag(DriverMaestroTags.HISTORY_NEXT_MONTH)
        )
    }
}

@Composable
private fun StatCardRow(state: WorkHistoryAgendaUiState) {
    val s = state.summary
    Row(
        Modifier.fillMaxWidth().testTag(DriverMaestroTags.HISTORY_STAT_ROW),
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.driver_history_stat_work_hours),
            value = hoursText(s?.totalWorkedMinutes ?: 0),
            badgeStart = ColorPrimary,
            badgeEnd = Color(0xFF1E40AF),
            valueColor = MaterialTheme.colorScheme.onSurface,
            tag = DriverMaestroTags.HISTORY_STAT_HOURS
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.EventAvailable,
            label = stringResource(R.string.driver_history_stat_work_days),
            value = stringResource(
                R.string.driver_history_stat_days_format,
                s?.workedDayCount ?: 0,
                s?.scheduledWorkdayCount ?: 0
            ),
            badgeStart = ColorSuccessLight,
            badgeEnd = ColorSuccess,
            valueColor = MaterialTheme.colorScheme.onSurface,
            tag = DriverMaestroTags.HISTORY_STAT_DAYS
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Bolt,
            label = stringResource(R.string.driver_history_stat_overtime),
            value = hoursText(s?.totalOvertimeMinutes ?: 0),
            badgeStart = ColorWarning,
            badgeEnd = ColorWarningDeep,
            // Kartu Lembur sengaja beda skema warna dari dua kartu lain (SSOT).
            valueColor = ColorWarningInk,
            tag = DriverMaestroTags.HISTORY_STAT_OVERTIME
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    badgeStart: Color,
    badgeEnd: Color,
    valueColor: Color,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeutralFill)
    ) {
        Column(
            Modifier.padding(Dsv2Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
        ) {
            Dsv2Badge3D(icon = icon, start = badgeStart, end = badgeEnd)
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = ColorNeutralInk, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/** Grid mini 7 kolom, TANPA header nama hari (SSOT). */
@Composable
private fun MonthOverviewCard(state: WorkHistoryAgendaUiState) {
    Surface(
        Modifier.fillMaxWidth().testTag(DriverMaestroTags.HISTORY_MINI_CALENDAR),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeutralFill)
    ) {
        Column(Modifier.padding(Dsv2Spacing.md), verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)) {
            Text(
                stringResource(R.string.driver_history_month_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (state.days.isNotEmpty()) {
                // Offset Senin-dulu dihitung di commonMain (leadingBlankCountForMonth), bukan
                // di sini — supaya iOS memakai angka yang SAMA, tidak menghitungnya lewat
                // Calendar.current yang default-nya Minggu-dulu.
                val cells: List<WorkHistoryDay?> = List(state.leadingBlankCount) { null } + state.days
                cells.chunked(7).forEach { week ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
                    ) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (d != null) {
                                    MiniDayCell(d, d.dateEpochDay == state.todayEpochDay)
                                } else {
                                    Spacer(Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDayCell(day: WorkHistoryDay, isToday: Boolean) {
    val dayNum = LocalDate.fromEpochDays(day.dateEpochDay).dayOfMonth
    val isFuture = day.state == WorkHistoryDayState.FUTURE
    val worked = day.state == WorkHistoryDayState.WORKED
    val absent = day.state == WorkHistoryDayState.SCHEDULED_ABSENT
    val holiday = day.state == WorkHistoryDayState.OFF_COMPANY_HOLIDAY

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    worked -> ColorPrimary
                    isFuture -> Color.Transparent
                    absent -> Color.Transparent
                    else -> ColorNeutralFill
                }
            )
            .then(
                when {
                    // terjadwal kerja tapi absen -> outline merah (WAJIB beda dari "libur")
                    absent -> Modifier.border(1.5.dp, ColorDanger, RoundedCornerShape(8.dp))
                    // belum terjadi -> border putus-putus
                    isFuture -> Modifier.drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = ColorNeutralInk.copy(alpha = 0.55f),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                        )
                    }
                    else -> Modifier
                }
            )
            .then(
                // hari ini -> ring
                if (isToday) Modifier.border(2.dp, ColorWarningDeep, RoundedCornerShape(8.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayNum.toString(),
            fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (worked) Color.White else ColorNeutralInk
        )
        // libur KHUSUS perusahaan -> titik aksen kecil di pojok
        if (holiday) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ColorWarningDeep)
            )
        }
    }
}

@Composable
private fun AgendaRow(
    day: WorkHistoryDay,
    isToday: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val date = LocalDate.fromEpochDays(day.dateEpochDay)
    val locale: Locale = LocalConfiguration.current.locales[0]
    // Nama hari LENGKAP via API tanggal sistem — locale Jepang otomatis kanji tunggal,
    // tidak ada tabel string Indonesia yang di-hardcode.
    val dayName = java.time.DayOfWeek.of(date.dayOfWeek.ordinal + 1)
        .getDisplayName(JavaTextStyle.FULL, locale)
    val accent = accentFor(day.state)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DriverMaestroTags.historyRowTag(day.dateEpochDay))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (expanded) 6.dp else 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            if (expanded) 1.5.dp else 1.dp,
            if (expanded) accent.copy(alpha = 0.65f) else ColorNeutralFill
        )
    ) {
        Row(Modifier.fillMaxWidth()) {
            // Bar aksen kiri, skema warna sama dengan kalender mini.
            Box(
                Modifier
                    .width(5.dp)
                    .height(if (expanded) 150.dp else 62.dp)
                    .background(accent)
            )
            Column(Modifier.padding(Dsv2Spacing.sm).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            dayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            "${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorNeutralInk
                        )
                    }
                    Text(
                        text = summaryText(day),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (day.state == WorkHistoryDayState.WORKED) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            ColorNeutralInk
                        }
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                ) {
                    ExpandedDetail(day)
                }
            }
        }
    }
}

@Composable
private fun ExpandedDetail(day: WorkHistoryDay) {
    val wd = day.workDay
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Dsv2Spacing.sm)
            .testTag(DriverMaestroTags.HISTORY_ROW_EXPANDED),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.driver_history_clock_in),
                    style = MaterialTheme.typography.labelSmall, color = ColorNeutralInk)
                Text(
                    wd?.clockInMillis?.takeIf { it > 0L }?.let { TimeUtil.formatHm(it, zone) } ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.driver_history_clock_out),
                    style = MaterialTheme.typography.labelSmall, color = ColorNeutralInk)
                Text(
                    wd?.clockOutMillis?.takeIf { it > 0L }?.let { TimeUtil.formatHm(it, zone) } ?: "--:--",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
            }
        }

        val m = day.minutes
        if (m != null && m.totalMinutes > 0) {
            val normalWeight = m.normalMinutes.coerceAtLeast(0).toFloat()
            val otWeight = m.overtimeMinutes.coerceAtLeast(0).toFloat()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .testTag(DriverMaestroTags.HISTORY_OVERTIME_BAR)
            ) {
                if (normalWeight > 0f) {
                    Box(Modifier.weight(normalWeight).fillMaxSize().background(ColorPrimary))
                }
                if (otWeight > 0f) {
                    Box(Modifier.weight(otWeight).fillMaxSize().background(ColorWarning))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)) {
                Text(
                    "${stringResource(R.string.driver_history_normal_hours)} ${hoursText(m.normalMinutes)}",
                    style = MaterialTheme.typography.labelSmall, color = ColorPrimary
                )
                Text(
                    "${stringResource(R.string.driver_history_overtime_hours)} ${hoursText(m.overtimeMinutes)}",
                    style = MaterialTheme.typography.labelSmall, color = ColorWarningInk
                )
            }
        }

        StatusLine(
            ok = wd?.hasChecklist == true,
            okText = stringResource(R.string.driver_history_checklist_done),
            missingText = stringResource(R.string.driver_history_checklist_missing)
        )
        StatusLine(
            ok = wd?.hasSafetyDeclaration == true,
            okText = stringResource(R.string.driver_history_safety_done),
            missingText = stringResource(R.string.driver_history_safety_missing)
        )
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, missingText: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.HighlightOff,
            contentDescription = null,
            tint = if (ok) ColorSuccess else ColorNeutralInk,
            modifier = Modifier.size(16.dp)
        )
        Text(
            if (ok) okText else missingText,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) ColorSuccess else ColorNeutralInk
        )
    }
}

private fun accentFor(state: WorkHistoryDayState): Color = when (state) {
    WorkHistoryDayState.WORKED -> ColorPrimary
    WorkHistoryDayState.SCHEDULED_ABSENT -> ColorDanger
    WorkHistoryDayState.OFF_COMPANY_HOLIDAY -> ColorWarningDeep
    WorkHistoryDayState.OFF_WEEKEND -> ColorNeutralFill
    WorkHistoryDayState.FUTURE -> ColorNeutralFill
}

@Composable
private fun summaryText(day: WorkHistoryDay): String = when (day.state) {
    WorkHistoryDayState.WORKED -> day.minutes?.let { hoursText(it.totalMinutes) } ?: "--"
    WorkHistoryDayState.OFF_WEEKEND -> stringResource(R.string.driver_history_state_off)
    WorkHistoryDayState.OFF_COMPANY_HOLIDAY -> stringResource(R.string.driver_history_state_holiday)
    WorkHistoryDayState.SCHEDULED_ABSENT -> stringResource(R.string.driver_history_state_absent)
    WorkHistoryDayState.FUTURE -> stringResource(R.string.driver_history_state_upcoming)
}

@Composable
private fun hoursText(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    return stringResource(R.string.driver_history_hours_format, safe / 60, safe % 60)
}
