package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.navigation.floatingNavPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.Dsv2PremiumPrimaryButton
import com.drivemate.showcase.ui.dsv2.Dsv2SectionHeader
import com.drivemate.showcase.ui.dsv2.dmClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.ds.DriveMateLoadingIndicator
import com.drivemate.showcase.ui.dsv2.Dsv2Card
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusChip
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind

/**
 * Hub Tugas -- daftar tugas harian driver (SOP sekali per akun, Checklist +
 * Deklarasi Keselamatan tiap hari). Menggantikan slot tab "Pernyataan
 * keselamatan" di floating nav; SOP ditampilkan di sini hanya sebelum
 * diterima, mengikuti pola gating yang sama dengan [DriverDailyStatus].
 *
 * Tidak melakukan fetch sendiri -- membaca [DriverUiState]/[DriverDailyStatus]
 * dari [DriverViewModel] yang sama dipakai Driver Home (lihat
 * `AppNavGraph.kt`, pola `viewModelStoreOwner = parentEntry` yang sudah
 * dipakai `DriverAttendanceScreen`), jadi progress-nya selalu sinkron dengan
 * Home dan nav bar tanpa listener terpisah.
 */
@Composable
fun DriverTugasScreen(
    state: DriverUiState,
    sopAccepted: Boolean?,
    checklistSubmitted: Boolean?,
    safetySubmitted: Boolean?,
    homeStateLoading: Boolean,
    onBack: () -> Unit,
    onOpenSop: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSafety: () -> Unit,
    onRefresh: () -> Unit = {},
    onOpenAttendance: (() -> Unit)? = null
) {
    val dailyStatus = DriverDailyStatus(
        sopAccepted = sopAccepted == true,
        tappedIn = state is DriverUiState.Working,
        checklistDone = checklistSubmitted == true,
        safetyDone = safetySubmitted == true
    )

    LaunchedEffect(Unit) { onRefresh() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DriverMaestroTags.TUGAS_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_tugas_title),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        when {
            state is DriverUiState.Error -> DriverErrorState(
                message = stringResource(state.messageRes),
                onRetry = onRefresh,
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag(DriverMaestroTags.TUGAS_ERROR_STATE)
            )
            state is DriverUiState.Loading || homeStateLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                DriveMateLoadingIndicator()
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(Dsv2Spacing.md)
                    .floatingNavPadding()
                    .testTag(DriverMaestroTags.TUGAS_CONTENT),
                verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
            ) {
                val items = dailyStatus.tugasItems()
                val required = items.filter { !it.done }
                val finished = items.filter { it.done }

                TugasHero(status = dailyStatus)

                if (required.isNotEmpty()) {
                    Dsv2SectionHeader(title = stringResource(R.string.driver_tugas_section_required))
                    required.forEach { item -> TugasTaskCard(item, onOpenSop, onOpenChecklist, onOpenSafety) }
                }

                if (finished.isNotEmpty()) {
                    Dsv2SectionHeader(title = stringResource(R.string.driver_tugas_section_done))
                    finished.forEach { item -> TugasTaskCard(item, onOpenSop, onOpenChecklist, onOpenSafety) }
                }

                if (dailyStatus.nextTask() == null) {
                    Dsv2Card(
                        containerColor = AppPalette.successContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DriverMaestroTags.TUGAS_ALL_DONE)
                    ) {
                        Text(
                            text = stringResource(R.string.driver_home_all_done_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (dailyStatus.nextTask() == DriverTask.TAP_IN && onOpenAttendance != null) {
                    // Semua tugas wajib beres, tinggal presensi masuk.
                    Dsv2PremiumPrimaryButton(
                        text = stringResource(R.string.driver_tugas_cta_attendance),
                        onClick = onOpenAttendance,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TugasHero(status: DriverDailyStatus) {
    val total = 2
    val remaining = status.requiredRemaining()
    val progress = status.doneCount / total.toFloat()
    val progressLabel = stringResource(R.string.driver_tugas_progress_format, status.doneCount, total)
    Dsv2Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dsv2Spacing.md)
                .semantics(mergeDescendants = true) { contentDescription = progressLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = if (remaining == 0) AppPalette.success else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${status.doneCount}/$total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = if (remaining == 0) {
                    stringResource(R.string.driver_tugas_hero_done)
                } else {
                    stringResource(R.string.driver_tugas_hero_remaining_format, remaining)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TugasTaskCard(
    item: DriverTugasItem,
    onOpenSop: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenSafety: () -> Unit
) {
    val (title, icon, onClick, tag) = when (item.task) {
        DriverTask.SOP -> TugasRowSpec(
            stringResource(R.string.driver_sop_title), Icons.AutoMirrored.Outlined.Assignment,
            onOpenSop, DriverMaestroTags.TUGAS_SOP_ROW
        )
        DriverTask.VEHICLE_CHECKLIST -> TugasRowSpec(
            stringResource(R.string.driver_checklist_title), Icons.Outlined.Checklist,
            onOpenChecklist, DriverMaestroTags.TUGAS_CHECKLIST_ROW
        )
        else -> TugasRowSpec(
            stringResource(R.string.driver_safety_title), Icons.Outlined.CheckCircle,
            onOpenSafety, DriverMaestroTags.TUGAS_SAFETY_ROW
        )
    }
    Dsv2Card(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .dmClickable(onClick = onClick, role = Role.Button, kind = DmPressKind.Meaningful)
                .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
        ) {
            DriverIconBubble(
                icon = icon,
                contentDescription = null,
                tint = if (item.done) AppPalette.success else MaterialTheme.colorScheme.primary,
                size = 40.dp
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Dsv2StatusChip(
                text = stringResource(
                    if (item.done) R.string.driver_tugas_status_done else R.string.driver_tugas_status_pending
                ),
                kind = if (item.done) Dsv2StatusKind.Approved else Dsv2StatusKind.Pending
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class TugasRowSpec(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tag: String
)
