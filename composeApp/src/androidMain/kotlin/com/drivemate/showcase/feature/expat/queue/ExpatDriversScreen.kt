package com.drivemate.showcase.feature.expat.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.feature.expat.ExpatBellAction
import com.drivemate.showcase.core.model.ApprovalStatus
import com.drivemate.showcase.feature.expat.status.ExpatApprovalPill
import com.drivemate.showcase.feature.expat.ExpatHomeViewModel
import com.drivemate.showcase.feature.expat.ManagedDriverSummaryUi
import com.drivemate.showcase.feature.expat.ja.ExpatJaTypography
import com.drivemate.showcase.navigation.floatingNavContentPadding
import com.drivemate.showcase.ui.dsv2.DmEmptyState
import com.drivemate.showcase.ui.dsv2.DmErrorState
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import com.drivemate.showcase.ui.dsv2.DmPressKind
import com.drivemate.showcase.ui.dsv2.DmSkeletonBlock
import com.drivemate.showcase.ui.dsv2.DmTonalCard
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.dmClickable

/** Test tag layar daftar Driver Expat (konvensi `dm_expat_...`). */
object ExpatDriversTags {
    const val ROOT = "dm_expat_drivers_root"
    const val LIST = "dm_expat_drivers_list"
    const val SEARCH = "dm_expat_drivers_search"
    const val ROW = "dm_expat_drivers_row"
}

/**
 * Daftar driver kelolaan dengan pencarian nama. Sumber data: [ExpatHomeViewModel] yang sama dengan Beranda
 * (tanpa panggilan baru); pencarian dilakukan lokal lewat [filterExpatDrivers].
 */
@Composable
fun ExpatDriversScreen(
    vm: ExpatHomeViewModel,
    onOpenDriver: (driverUid: String) -> Unit,
    notificationUnread: Int = 0,
    onOpenNotifications: (() -> Unit)? = null
) {
    val drivers by vm.drivers.collectAsState()
    val loading by vm.loading.collectAsState()
    val errorRes by vm.error.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (vm.drivers.value.isEmpty()) vm.refresh() }

    // Pembungkus gaya teks Jepang (J1): tak berpengaruh bila bahasa aplikasi bukan Jepang.
    ExpatJaTypography {
    Scaffold(
        // Flag harus di Scaffold: lonceng ada di slot topBar, subtree terpisah dari konten.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = Color.Transparent,
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.expat_drivers_title),
                actions = {
                    onOpenNotifications?.let { ExpatBellAction(unread = notificationUnread, onClick = it) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(ExpatDriversTags.ROOT)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.expat_drivers_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.expat_drivers_search_clear_cd)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dsv2Spacing.md)
                    .testTag(ExpatDriversTags.SEARCH)
            )
            when {
                errorRes != null && drivers.isEmpty() -> DmErrorState(
                    message = stringResource(R.string.expat_state_error_message),
                    retryText = stringResource(R.string.expat_state_error_retry),
                    onRetry = vm::refresh
                )
                loading && drivers.isEmpty() -> {
                    val loadingCd = stringResource(R.string.expat_state_loading_cd)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dsv2Spacing.md)
                            .semantics { contentDescription = loadingCd },
                        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                    ) {
                        repeat(5) { DmSkeletonBlock(Modifier.fillMaxWidth().heightIn(min = 64.dp)) }
                    }
                }
                else -> {
                    // Pencarian lokal lalu urut menunggu terbanyak (yang perlu tindakan di atas).
                    val visible = expatDriversForDisplay(drivers, query)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(ExpatDriversTags.LIST),
                        contentPadding = floatingNavContentPadding(PaddingValues(Dsv2Spacing.md)),
                        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                    ) {
                        if (visible.isEmpty()) {
                            item {
                                if (drivers.isEmpty()) {
                                    DmEmptyState(
                                        title = stringResource(R.string.expat_drivers_empty_title),
                                        message = stringResource(R.string.expat_drivers_empty_message)
                                    )
                                } else {
                                    DmEmptyState(
                                        title = stringResource(R.string.expat_drivers_search_empty_title),
                                        message = stringResource(R.string.expat_drivers_search_empty_message)
                                    )
                                }
                            }
                        }
                        items(visible, key = { it.uid }) { driver ->
                            DriverRow(driver = driver, onClick = { onOpenDriver(driver.uid) })
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Kartu driver: avatar inisial, nama, dan pil status berikon (menunggu, ditolak, atau "semua beres" untuk 0/0).
 * Deskripsi untuk TalkBack tetap memakai teks hitungan yang sama dengan sebelumnya.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverRow(driver: ManagedDriverSummaryUi, onClick: () -> Unit) {
    val counts = stringResource(R.string.expat_drivers_row_counts_format, driver.pendingCount, driver.rejectedCount)
    val rowCd = stringResource(R.string.expat_drivers_row_cd_format, driver.displayName, counts)
    val openCd = stringResource(R.string.expat_drivers_row_open_cd)
    DmTonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .dmClickable(onClick = onClick, role = Role.Button, kind = DmPressKind.Meaningful)
            .testTag(ExpatDriversTags.ROW)
            .semantics(mergeDescendants = true) { contentDescription = "$rowCd. $openCd" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
        ) {
            ExpatQueueAvatar(driver.displayName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)) {
                Text(text = driver.displayName, style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.xs)
                ) {
                    driverPills(driver).forEach { kind ->
                        when (kind) {
                            ExpatDriverPillKind.PENDING -> ExpatApprovalPill(
                                status = ApprovalStatus.PENDING,
                                text = stringResource(R.string.expat_drivers_pill_pending_format, driver.pendingCount),
                                compact = true
                            )
                            ExpatDriverPillKind.REJECTED -> ExpatApprovalPill(
                                status = ApprovalStatus.REJECTED,
                                text = stringResource(R.string.expat_drivers_pill_rejected_format, driver.rejectedCount),
                                compact = true
                            )
                            ExpatDriverPillKind.ALL_CLEAR -> ExpatApprovalPill(
                                status = ApprovalStatus.APPROVED,
                                text = stringResource(R.string.expat_drivers_pill_all_clear),
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }
}
