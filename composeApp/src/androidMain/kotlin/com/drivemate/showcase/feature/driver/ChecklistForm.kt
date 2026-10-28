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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppShapes
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.ds.AutoDismissInlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverChecklistScreen(
    vm: DriverChecklistViewModel,
    onBack: () -> Unit,
    onBackHome: () -> Unit,
    onSubmittedReturnHome: (submittedAtMillis: Long) -> Unit
) {
    val state by vm.state.collectAsState()
    var errorBanner by remember { mutableStateOf<String?>(null) }
    val errorMessageRes = state.errorMessageRes
    val errorMessage = errorMessageRes?.let { stringResource(it) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(errorMessage) {
        errorBanner = errorMessage
    }

    // One-shot: a Channel (not state) so this can't replay on rotation or
    // recomposition -- vm.submit() already delays the send so the button's
    // confirmed state is visible before Home takes over.
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DriverChecklistEvent.Submitted -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSubmittedReturnHome(event.submittedAtMillis)
                }
            }
        }
    }

    val form = state.form
    // A fresh page load with statusToday==true has no in-memory answers to show
    // (ChecklistRepository deliberately doesn't support reading them back) --
    // that case keeps the generic locked message. A submission that happened
    // THIS session still has the real answers in `form`, so it gets the richer
    // read-only echo instead.
    val freshLocked = state.statusToday == true && !state.justSubmitted
    val inputsEnabled = !state.submitting && !state.justSubmitted

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(DriverMaestroTags.CHECKLIST_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        // DriveMateRootScaffold's innerPadding already covers the status bar
        // for this route (only Splash/Login are full-bleed) -- DriverTopBar's
        // own default windowInsets duplicates that inset, stacking into an
        // empty gap above the title. Zeroed on both axes here, matching the
        // fix already applied to DriverAttendanceScreen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_checklist_title),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state.statusLoading && state.statusToday == null -> ChecklistLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md)
            )
            !state.statusLoading && state.statusToday == null && errorMessageRes != null -> {
                DriverNetworkBanner(modifier = Modifier.fillMaxWidth().padding(padding))
                DriverErrorState(
                    message = stringResource(errorMessageRes),
                    onRetry = { vm.refreshStatus() },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(
                    horizontal = Dsv2Spacing.lg,
                    vertical = Dsv2Spacing.md
                ),
            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
        ) {
            item(key = "offline_banner") { DriverNetworkBanner(modifier = Modifier.fillMaxWidth()) }
            item(key = "status_card") {
                ChecklistStatusCard(
                    statusLoading = state.statusLoading,
                    statusToday = state.statusToday,
                    answered = form.answeredItemCount(),
                    total = form.totalItemCount(),
                    modifier = Modifier.testTag(DriverMaestroTags.CHECKLIST_STATUS_CARD)
                )
            }

            if (!freshLocked && form.hasIssue()) {
                item(key = "issue_warning") {
                    DriverTonalCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                        ) {
                            DriverIconBubble(
                                icon = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = AppPalette.error
                            )
                            Text(
                                text = stringResource(R.string.driver_checklist_issue_warning),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item(key = "error_banner") {
                AutoDismissInlineAlert(
                    kind = InlineAlertKind.Error,
                    message = errorBanner,
                    onDismiss = { errorBanner = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (freshLocked) {
                item(key = "locked_state") {
                    DriverLockedState(
                        modifier = Modifier.testTag(DriverMaestroTags.CHECKLIST_LOCKED_STATE),
                        title = stringResource(R.string.driver_checklist_locked_title),
                        message = stringResource(R.string.driver_checklist_locked_message),
                        actionText = stringResource(R.string.driver_back_home),
                        actionContentDescription = stringResource(R.string.driver_back_home),
                        onAction = onBackHome
                    )
                }
            } else {
                val showErrors = state.showValidationErrors

                item(key = "section_vehicle") {
                    DriverCard(modifier = Modifier.fillMaxWidth()) {
                        DriverSectionHeader(
                            icon = Icons.Outlined.Description,
                            title = stringResource(R.string.driver_checklist_section_vehicle)
                        )
                        Spacer(Modifier.height(AppSpacing.sm))

                        ImeAwareOutlinedTextField(
                            value = state.form.vehicleId,
                            onValueChange = vm::updateVehicleId,
                            label = stringResource(R.string.driver_checklist_vehicle_plate_label),
                            helperText = stringResource(R.string.driver_checklist_vehicle_plate_help),
                            enabled = inputsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(AppSpacing.sm))

                        ImeAwareOutlinedTextField(
                            value = state.form.odometerKm,
                            onValueChange = vm::updateOdometer,
                            label = stringResource(R.string.driver_checklist_odometer_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            helperText = stringResource(R.string.driver_checklist_odometer_help),
                            enabled = inputsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(AppSpacing.sm))

                        DropdownField(
                            label = stringResource(R.string.driver_checklist_fuel_label),
                            value = state.form.fuelLevel,
                            options = listOf(
                                stringResource(R.string.driver_checklist_fuel_full),
                                stringResource(R.string.driver_checklist_fuel_three_quarter),
                                stringResource(R.string.driver_checklist_fuel_half),
                                stringResource(R.string.driver_checklist_fuel_quarter),
                                stringResource(R.string.driver_checklist_fuel_low)
                            ),
                            onValueChange = vm::updateFuelLevel,
                            isError = showErrors && state.form.fuelLevel.isBlank(),
                            errorText = stringResource(R.string.driver_checklist_fuel_error),
                            helperText = stringResource(R.string.driver_checklist_fuel_help),
                            enabled = inputsEnabled
                        )
                    }
                }

                item(key = "zone_tires_brakes") {
                    ChecklistZoneCard(
                        icon = Icons.Outlined.TireRepair,
                        title = stringResource(R.string.driver_checklist_zone_tires_brakes),
                        items = listOf(
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_tire_front_left),
                                form.tiresFrontLeftOk,
                                vm::updateTiresFrontLeftOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_tire_front_right),
                                form.tiresFrontRightOk,
                                vm::updateTiresFrontRightOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_tire_rear_left),
                                form.tiresRearLeftOk,
                                vm::updateTiresRearLeftOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_tire_rear_right),
                                form.tiresRearRightOk,
                                vm::updateTiresRearRightOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_brakes),
                                form.brakesOk,
                                vm::updateBrakesOk
                            )
                        ),
                        enabled = inputsEnabled
                    )
                }

                item(key = "tire_pressure_note") {
                    ImeAwareOutlinedTextField(
                        value = state.form.tirePressureNote,
                        onValueChange = vm::updateTirePressureNote,
                        label = stringResource(R.string.driver_checklist_tire_pressure_label),
                        helperText = stringResource(R.string.driver_checklist_tire_pressure_helper),
                        enabled = inputsEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "zone_lights_horn") {
                    ChecklistZoneCard(
                        icon = Icons.Outlined.Lightbulb,
                        title = stringResource(R.string.driver_checklist_zone_lights_horn),
                        items = listOf(
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_lights),
                                form.lightsOk,
                                vm::updateLightsOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_signal_brake_lights),
                                form.turnSignalBrakeLightsOk,
                                vm::updateTurnSignalBrakeLightsOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_horn),
                                form.hornOk,
                                vm::updateHornOk
                            )
                        ),
                        enabled = inputsEnabled
                    )
                }

                item(key = "zone_cabin_safety") {
                    ChecklistZoneCard(
                        icon = Icons.Outlined.Weekend,
                        title = stringResource(R.string.driver_checklist_zone_cabin_safety),
                        items = listOf(
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_seatbelt),
                                form.seatbeltOk,
                                vm::updateSeatbeltOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_wipers),
                                form.wipersOk,
                                vm::updateWipersOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_mirrors),
                                form.mirrorsOk,
                                vm::updateMirrorsOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_cleanliness),
                                form.cleanlinessOk,
                                vm::updateCleanlinessOk
                            )
                        ),
                        enabled = inputsEnabled
                    )
                }

                item(key = "zone_engine_fluids") {
                    ChecklistZoneCard(
                        icon = Icons.Outlined.BuildCircle,
                        title = stringResource(R.string.driver_checklist_zone_engine_fluids),
                        items = listOf(
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_engine_oil),
                                form.engineOilOk,
                                vm::updateEngineOilOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_coolant),
                                form.coolantOk,
                                vm::updateCoolantOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_battery),
                                form.batteryOk,
                                vm::updateBatteryOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_washer_fluid),
                                form.washerFluidOk,
                                vm::updateWasherFluidOk
                            )
                        ),
                        enabled = inputsEnabled
                    )
                }

                item(key = "zone_required_equipment") {
                    ChecklistZoneCard(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.driver_checklist_section_required_equipment),
                        items = listOf(
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_documents),
                                form.documentsOk,
                                vm::updateDocumentsOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_fire_extinguisher),
                                form.fireExtinguisherOk,
                                vm::updateFireExtinguisherOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_first_aid),
                                form.firstAidOk,
                                vm::updateFirstAidOk
                            ),
                            ChecklistItemSpec(
                                stringResource(R.string.driver_checklist_warning_triangle),
                                form.warningTriangleOk,
                                vm::updateWarningTriangleOk
                            )
                        ),
                        enabled = inputsEnabled
                    )
                }

                item(key = "section_notes") {
                    DriverCard(modifier = Modifier.fillMaxWidth()) {
                        DriverSectionHeader(
                            icon = Icons.Outlined.Description,
                            title = stringResource(R.string.driver_checklist_section_notes)
                        )
                        Spacer(Modifier.height(AppSpacing.sm))

                        ImeAwareOutlinedTextField(
                            value = state.form.notes,
                            onValueChange = vm::updateNotes,
                            label = stringResource(R.string.driver_checklist_notes_label),
                            enabled = inputsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            singleLine = false
                        )
                    }
                }

                item(key = "submit_section") {
                    val remaining = form.totalItemCount() - form.answeredItemCount()
                    val fuelBlank = form.fuelLevel.isBlank()
                    val canSubmit = !fuelBlank && remaining == 0
                    val hintText = when {
                        state.justSubmitted -> null
                        fuelBlank -> stringResource(R.string.driver_checklist_fuel_error)
                        remaining > 0 -> stringResource(R.string.driver_checklist_items_remaining_format, remaining)
                        else -> null
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (hintText != null) {
                            Text(
                                text = hintText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = AppSpacing.xs)
                            )
                        }
                        val confirmedDescription = stringResource(R.string.driver_success_checklist_submitted)
                        DriverPrimaryButton(
                            text = stringResource(R.string.driver_checklist_submit),
                            onClick = { vm.submit() },
                            enabled = !state.submitting && !state.justSubmitted && canSubmit,
                            loading = state.submitting,
                            spinnerOnly = true,
                            confirmed = state.justSubmitted,
                            confirmedText = stringResource(R.string.driver_checklist_status_submitted),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(DriverMaestroTags.CHECKLIST_SUBMIT_BUTTON)
                                .then(
                                    if (state.justSubmitted) {
                                        Modifier.semantics { contentDescription = confirmedDescription }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        if (state.justSubmitted) {
                            Text(
                                text = stringResource(R.string.driver_checklist_once_per_day),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = AppSpacing.xs)
                            )
                        }
                    }
                }
            }
        } // end LazyColumn
        } // end when
    }
}

@Composable
private fun ChecklistLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(AppSpacing.xl))
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.md))
        }
        repeat(5) {
            DriverCard(modifier = Modifier.fillMaxWidth()) {
                SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(AppSpacing.md))
                Spacer(Modifier.height(AppSpacing.sm))
                repeat(3) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun ChecklistStatusCard(
    statusLoading: Boolean,
    statusToday: Boolean?,
    answered: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        statusLoading -> stringResource(R.string.driver_checklist_status_loading)
        statusToday == true -> stringResource(R.string.driver_checklist_status_submitted)
        statusToday == false -> stringResource(R.string.driver_checklist_status_not_submitted)
        else -> stringResource(R.string.driver_checklist_status_unknown)
    }
    val chipKind = when {
        statusLoading -> Dsv2StatusKind.Pending
        statusToday == true -> Dsv2StatusKind.Approved
        statusToday == false -> Dsv2StatusKind.Pending
        else -> Dsv2StatusKind.Pending
    }
    // Submitted state doesn't need the count anymore -- "Sudah dikirim" already
    // says everything was answered. Showing it only pre-submit is also what
    // keeps this ONE indicator instead of repeating the same fact twice.
    val progressText = if (statusToday == true) {
        null
    } else {
        stringResource(R.string.driver_checklist_zone_progress_format, answered, total)
    }
    val a11yLabel = stringResource(R.string.driver_checklist_status_label, statusText)

    DriverTonalCard(modifier = modifier.fillMaxWidth(), secondary = true) {
        DriverSectionHeader(
            icon = Icons.Outlined.CheckCircle,
            title = stringResource(R.string.driver_checklist_status_title)
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.semantics { contentDescription = a11yLabel }
        ) {
            DriverStatusPill(text = statusText, kind = chipKind)
            if (progressText != null) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ChecklistItemSpec(
    val label: String,
    val value: Boolean?,
    val onValueChange: (Boolean?) -> Unit
)

@Composable
private fun ChecklistZoneCard(
    icon: ImageVector,
    title: String,
    items: List<ChecklistItemSpec>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val answered = items.count { it.value != null }
    DriverCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DriverSectionHeader(icon = icon, title = title)
            ChecklistZoneCountBadge(answered = answered, total = items.size)
        }
        Spacer(Modifier.height(AppSpacing.sm))
        items.forEach { spec ->
            ChecklistItemRow(
                label = spec.label,
                value = spec.value,
                onValueChange = spec.onValueChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ChecklistZoneCountBadge(answered: Int, total: Int, modifier: Modifier = Modifier) {
    val complete = answered == total
    val bg = if (complete) AppPalette.successContainerSolid else AppPalette.surfaceVariant
    val fg = if (complete) AppPalette.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppShapes.logoBadge),
        color = bg
    ) {
        Text(
            text = stringResource(R.string.driver_checklist_zone_progress_format, answered, total),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
        )
    }
}

/**
 * Three-state item selector: not-checked (neutral, empty circle) / OK (green
 * check) / issue (red warning triangle) -- shape-distinct, not color-alone,
 * matching the badge pattern already established on Driver Home
 * (`TileStatusBadge` in DriverHomeScreen.kt). Tap-to-cycle keeps the same
 * null -> true -> false -> null order the previous `TriStateCheckbox` used.
 */
@Composable
private fun ChecklistItemRow(
    label: String,
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
    enabled: Boolean
) {
    val okText = stringResource(R.string.driver_checklist_answer_ok)
    val issueText = stringResource(R.string.driver_checklist_answer_issue)
    val pendingText = stringResource(R.string.driver_checklist_answer_pending)
    val icon = when (value) {
        true -> Icons.Filled.CheckCircle
        false -> Icons.Filled.Warning
        null -> Icons.Outlined.RadioButtonUnchecked
    }
    val tint = when (value) {
        true -> AppPalette.success
        false -> AppPalette.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (value) {
        true -> okText
        false -> issueText
        null -> pendingText
    }
    val nextValue = when (value) {
        null -> true
        true -> false
        false -> null
    }
    val a11yLabel = "$label: $statusText"

    DriverSelectableSurfaceRow(
        active = value == true,
        enabled = enabled,
        onClick = { onValueChange(nextValue) },
        modifier = Modifier.semantics { contentDescription = a11yLabel }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dsv2Sizing.icon)
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall.copy(color = tint),
            modifier = Modifier.padding(start = AppSpacing.sm)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorText: String,
    helperText: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {
            if (enabled) expanded = !expanded
        }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            isError = isError,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
    if (isError) {
        Text(
            text = errorText,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (!helperText.isNullOrBlank()) {
        Text(
            text = helperText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ImeAwareOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    helperText: String? = null
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = keyboardOptions,
            minLines = minLines,
            singleLine = singleLine,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusEvent {
                    if (it.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
        )
        if (!helperText.isNullOrBlank()) {
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
