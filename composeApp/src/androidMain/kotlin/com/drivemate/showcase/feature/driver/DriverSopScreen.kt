package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.drivemate.showcase.R
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.ds.AutoDismissInlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import kotlinx.datetime.TimeZone

/** Fixed category identity: gradient + icon, matching the vivid Home-tile family. */
private data class SopCategorySpec(
    val key: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradient: List<Color>,
    val titleRes: Int,
    val lineRes: List<Int>
)

private val SOP_CATEGORIES = listOf(
    SopCategorySpec(
        key = "pretrip",
        icon = Icons.Outlined.DirectionsCar,
        gradient = listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
        titleRes = R.string.driver_sop_section1_title,
        lineRes = listOf(
            R.string.driver_sop_section1_line1,
            R.string.driver_sop_section1_line2,
            R.string.driver_sop_section1_line3
        )
    ),
    SopCategorySpec(
        key = "safedriving",
        icon = Icons.Outlined.CheckCircle,
        gradient = listOf(Color(0xFF4ADE80), Color(0xFF16A34A)),
        titleRes = R.string.driver_sop_section2_title,
        lineRes = listOf(
            R.string.driver_sop_section2_line1,
            R.string.driver_sop_section2_line2,
            R.string.driver_sop_section2_line3
        )
    ),
    SopCategorySpec(
        key = "incident",
        icon = Icons.Outlined.ReportProblem,
        gradient = listOf(Color(0xFFFCA5A5), Color(0xFFDC2626)),
        titleRes = R.string.driver_sop_section3_title,
        lineRes = listOf(
            R.string.driver_sop_section3_line1,
            R.string.driver_sop_section3_line2,
            R.string.driver_sop_section3_line3
        )
    ),
    SopCategorySpec(
        key = "parking",
        icon = Icons.Outlined.LocalParking,
        gradient = listOf(Color(0xFF22D3EE), Color(0xFF0891B2)),
        titleRes = R.string.driver_sop_section4_title,
        lineRes = listOf(
            R.string.driver_sop_section4_line1,
            R.string.driver_sop_section4_line2,
            R.string.driver_sop_section4_line3
        )
    ),
    SopCategorySpec(
        key = "ethics",
        icon = Icons.Outlined.Handshake,
        gradient = listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)),
        titleRes = R.string.driver_sop_section5_title,
        lineRes = listOf(
            R.string.driver_sop_section5_line1,
            R.string.driver_sop_section5_line2,
            R.string.driver_sop_section5_line3
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSopScreen(
    vm: DriverSopViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    var errorBanner by remember { mutableStateOf<String?>(null) }
    val errorMessage = state.errorMessageRes?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        errorBanner = errorMessage
    }

    // "Read" signal for the acceptance gate: which categories the driver has
    // actually opened at least once. Deliberately NOT scroll-position-based --
    // a collapsed 5-card list satisfies "scrolled to bottom" almost instantly,
    // which would let a driver accept without reading anything. Opening every
    // category is a much harder-to-game minimum bar for content this short
    // (3 lines each).
    var openedCategories by rememberSaveable { mutableStateOf(setOf<String>()) }
    var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val allCategoriesOpened = openedCategories.size >= SOP_CATEGORIES.size

    val listState = rememberLazyListState()

    Scaffold(
        // Layar ini merender `when {}` langsung di dalam Scaffold, tanpa container root
        // tunggal untuk ditandai — jadi tag dipasang di Scaffold-nya: nol perubahan struktur
        // layout, dan seluruh subtree tetap mengekspos tag-nya ke accessibility tree.
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(DriverMaestroTags.SOP_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_sop_title),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state.loading && state.sopAccepted == null -> {
                DriverSopLoadingSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm)
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Dsv2Spacing.md, vertical = Dsv2Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                ) {
                    item { DriverNetworkBanner(modifier = Modifier.fillMaxWidth()) }
                    item {
                        AutoDismissInlineAlert(
                            kind = InlineAlertKind.Error,
                            message = errorBanner,
                            onDismiss = { errorBanner = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state.sopAccepted == true) {
                        item(key = "accepted_banner") {
                            val acceptedText = sopAcceptedText(state.sopAcceptedAtMillis)
                            DriverCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    DriverIconBubble(
                                        icon = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = AppPalette.success,
                                        size = Dsv2Sizing.iconContainerLg,
                                        iconSize = Dsv2Sizing.icon
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.driver_sop_accepted_locked_title),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = acceptedText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(AppSpacing.xs))
                                DriverStatusPill(
                                    text = stringResource(R.string.driver_sop_status_accepted_short),
                                    kind = Dsv2StatusKind.Approved
                                )
                            }
                        }
                    } else {
                        item {
                            DriverStatusSummaryCard(
                                icon = Icons.Outlined.CheckCircle,
                                title = stringResource(R.string.driver_sop_status_section_title),
                                statusText = stringResource(R.string.driver_sop_status_pending_short),
                                statusKind = Dsv2StatusKind.Pending,
                                description = sopStatusText(
                                    loading = state.loading,
                                    accepted = state.sopAccepted,
                                    acceptedAtMillis = state.sopAcceptedAtMillis
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item(key = "categories_progress") {
                            Text(
                                text = stringResource(
                                    R.string.driver_sop_categories_opened_format,
                                    openedCategories.size,
                                    SOP_CATEGORIES.size
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(SOP_CATEGORIES, key = { it.key }) { category ->
                        val pointCount = category.lineRes.size
                        PledgeCategoryCard(
                            icon = category.icon,
                            gradient = category.gradient,
                            title = stringResource(category.titleRes),
                            pointCountLabel = stringResource(
                                R.string.driver_sop_points_count_format,
                                pointCount
                            ),
                            expanded = expandedCategory == category.key,
                            onToggle = {
                                val opening = expandedCategory != category.key
                                expandedCategory = if (opening) category.key else null
                                if (opening) {
                                    openedCategories = openedCategories + category.key
                                }
                            },
                            trailingBadge = if (category.key in openedCategories) {
                                {
                                    DriverIconBubble(
                                        icon = Icons.Outlined.CheckCircle,
                                        contentDescription = stringResource(
                                            R.string.driver_sop_category_opened_description
                                        ),
                                        tint = AppPalette.success,
                                        size = Dsv2Sizing.iconContainer,
                                        iconSize = Dsv2Sizing.icon
                                    )
                                }
                            } else {
                                null
                            }
                        ) {
                            category.lineRes.forEach { lineRes ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = stringResource(R.string.driver_bullet),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(lineRes),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    if (state.sopAccepted != true) {
                        item(key = "open_all_hint") {
                            if (!allCategoriesOpened) {
                                Text(
                                    text = stringResource(R.string.driver_sop_open_all_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item(key = "accept_button") {
                            val acceptDescription = stringResource(R.string.driver_sop_submit)
                            DriverPrimaryButton(
                                text = stringResource(R.string.driver_sop_submit),
                                onClick = { vm.saveAcknowledgement() },
                                enabled = allCategoriesOpened && !state.submitting,
                                loading = state.submitting,
                                spinnerOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = acceptDescription }
                            )
                        }
                    }
                }
            }
        }
    }

    val successMessageRes = state.successMessageRes
    if (successMessageRes != null) {
        DriverSuccessDialog(
            title = stringResource(R.string.driver_dialog_success_title),
            message = stringResource(successMessageRes),
            onConfirm = {
                vm.clearMessage()
                onBack()
            }
        )
    }
}

@Composable
private fun DriverSopLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        DriverCard(modifier = Modifier.fillMaxWidth()) {
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.xl))
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.md))
        }
        repeat(5) {
            DriverCard(modifier = Modifier.fillMaxWidth()) {
                SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(AppSpacing.lg))
            }
        }
    }
}

@Composable
private fun sopStatusText(
    loading: Boolean,
    accepted: Boolean?,
    acceptedAtMillis: Long?
): String {
    if (loading) return stringResource(R.string.driver_sop_status_loading)
    return when {
        accepted == true && acceptedAtMillis != null -> {
            val zone = TimeZone.of("Asia/Jakarta")
            val date = TimeUtil.formatYmdFromEpochDay(TimeUtil.epochDayOf(acceptedAtMillis, zone).toInt(), zone)
            val time = TimeUtil.formatHm(acceptedAtMillis, zone)
            stringResource(R.string.driver_sop_status_accepted_at, date, time)
        }
        accepted == true -> stringResource(R.string.driver_sop_status_accepted)
        else -> stringResource(R.string.driver_sop_status_pending)
    }
}

@Composable
private fun sopAcceptedText(acceptedAtMillis: Long?): String {
    if (acceptedAtMillis == null) return stringResource(R.string.driver_sop_status_accepted)
    val zone = TimeZone.of("Asia/Jakarta")
    val date = TimeUtil.formatYmdFromEpochDay(TimeUtil.epochDayOf(acceptedAtMillis, zone).toInt(), zone)
    val time = TimeUtil.formatHm(acceptedAtMillis, zone)
    return stringResource(R.string.driver_sop_status_accepted_at, date, time)
}
