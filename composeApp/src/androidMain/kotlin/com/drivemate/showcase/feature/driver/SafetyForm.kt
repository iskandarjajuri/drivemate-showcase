package com.drivemate.showcase.feature.driver

import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.NoDrinks
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.AppPalette
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.LocalReduceMotion
import com.drivemate.showcase.ui.ds.AutoDismissInlineAlert
import com.drivemate.showcase.ui.ds.InlineAlertKind
import com.drivemate.showcase.ui.dsv2.Dsv2Sizing
import com.drivemate.showcase.ui.dsv2.Dsv2Spacing
import com.drivemate.showcase.ui.dsv2.Dsv2StatusKind
import kotlinx.coroutines.launch

private val GRADIENT_SAFETY_GOOD = listOf(Color(0xFF4ADE80), Color(0xFF16A34A))
private val GRADIENT_SAFETY_CAUTION = listOf(Color(0xFFFCA5A5), Color(0xFFDC2626))
private val GRADIENT_SAFETY_CALM = listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))
private val GRADIENT_SAFETY_RULE = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
private val GRADIENT_SAFETY_AMBER = listOf(Color(0xFFFCD34D), Color(0xFFF59E0B))
private val GRADIENT_SAFETY_SKY = listOf(Color(0xFF22D3EE), Color(0xFF0891B2))

/** One entry per boolean pledge step, in wizard order. Index == position in DriverSafetyFormState.pledgeAnswers(). */
private data class BooleanPledgeSpec(
    val icon: ImageVector,
    val gradient: List<Color>,
    val statementRes: Int,
    val value: Boolean?,
    val onAnswer: (Boolean) -> Unit
)

private const val TOTAL_STEPS = 12
private const val STEP_HAZARD = 7
private const val STEP_WEATHER = 8
private const val STEP_SLEEP = 9
private const val STEP_NOTES = 10
private const val STEP_CONFIRM = 11

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSafetyDeclarationScreen(
    vm: DriverSafetyDeclarationViewModel,
    onBack: () -> Unit,
    onBackHome: () -> Unit
) {
    val state by vm.state.collectAsState()
    var errorBanner by remember { mutableStateOf<String?>(null) }
    val errorMessage = state.errorMessageRes?.let { stringResource(it) }
    androidx.compose.runtime.LaunchedEffect(errorMessage) {
        errorBanner = errorMessage
    }

    val form = state.form
    val pledges = listOf(
        BooleanPledgeSpec(Icons.Outlined.HealthAndSafety, GRADIENT_SAFETY_GOOD, R.string.driver_safety_fit_to_work, form.fitToWork, vm::updateFitToWork),
        BooleanPledgeSpec(Icons.Outlined.NoDrinks, GRADIENT_SAFETY_CAUTION, R.string.driver_safety_no_alcohol, form.noAlcoholOrDrugs, vm::updateNoAlcoholOrDrugs),
        BooleanPledgeSpec(Icons.Outlined.SelfImprovement, GRADIENT_SAFETY_CALM, R.string.driver_safety_no_stress, form.noSevereStress, vm::updateNoSevereStress),
        BooleanPledgeSpec(Icons.Outlined.Speed, GRADIENT_SAFETY_RULE, R.string.driver_safety_follow_speed_limit, form.willFollowSpeedLimit, vm::updateWillFollowSpeedLimit),
        BooleanPledgeSpec(Icons.Outlined.FreeBreakfast, GRADIENT_SAFETY_AMBER, R.string.driver_safety_follow_rest_breaks, form.willFollowRestBreaks, vm::updateWillFollowRestBreaks),
        BooleanPledgeSpec(Icons.Outlined.PhoneDisabled, GRADIENT_SAFETY_CAUTION, R.string.driver_safety_no_phone, form.willNotUsePhoneWhileDriving, vm::updateWillNotUsePhone),
        BooleanPledgeSpec(Icons.Outlined.Shield, GRADIENT_SAFETY_GOOD, R.string.driver_safety_wear_seatbelt, form.willWearSeatbelt, vm::updateWillWearSeatbelt)
    )

    val freshLocked = state.statusToday == true && state.successMessageRes == null
    val inputsEnabled = !state.submitting

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag(DriverMaestroTags.SAFETY_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        // Same double-inset fix as ChecklistForm/DriverAttendanceScreen -- see
        // the comment on DriverTopBar's windowInsets param for the root cause.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DmScreenHeader(
                title = stringResource(R.string.driver_safety_title),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state.statusLoading && state.statusToday == null -> SafetyLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md)
            )
            freshLocked -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md)
            ) {
                DriverNetworkBanner(modifier = Modifier.fillMaxWidth())
                AutoDismissInlineAlert(
                    kind = InlineAlertKind.Error,
                    message = errorBanner,
                    onDismiss = { errorBanner = null },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Dsv2Spacing.sm))
                DriverLockedState(
                    modifier = Modifier.testTag(DriverMaestroTags.SAFETY_LOCKED_STATE),
                    title = stringResource(R.string.driver_safety_locked_title),
                    message = stringResource(R.string.driver_safety_locked_message),
                    actionText = stringResource(R.string.driver_back_home),
                    actionContentDescription = stringResource(R.string.driver_back_home),
                    onAction = onBackHome
                )
            }
            state.successMessageRes != null -> SafetyPledgeCompleteScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md),
                onBackHome = {
                    vm.clearMessage()
                    onBackHome()
                }
            )
            else -> {
                BackHandler(enabled = state.currentStepIndex > 0) {
                    vm.updateCurrentStep(state.currentStepIndex - 1)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .padding(horizontal = Dsv2Spacing.lg, vertical = Dsv2Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
                ) {
                    DriverNetworkBanner(modifier = Modifier.fillMaxWidth())
                    AutoDismissInlineAlert(
                        kind = InlineAlertKind.Error,
                        message = errorBanner,
                        onDismiss = { errorBanner = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                    StatusCard(
                        statusLoading = state.statusLoading,
                        statusToday = state.statusToday,
                        modifier = Modifier.testTag(DriverMaestroTags.SAFETY_STATUS_CARD)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PledgeProgressDots(currentIndex = state.currentStepIndex, total = TOTAL_STEPS)
                        Text(
                            text = stringResource(
                                R.string.driver_safety_step_format,
                                state.currentStepIndex + 1,
                                TOTAL_STEPS
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedContent(
                        targetState = state.currentStepIndex,
                        label = "safetyPledgeStep",
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f)
                    ) { step ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.md)
                        ) {
                            item {
                                when (step) {
                                    in pledges.indices -> BooleanPledgeStepContent(
                                        spec = pledges[step],
                                        enabled = inputsEnabled,
                                        onAdvance = { vm.updateCurrentStep(step + 1) }
                                    )
                                    STEP_HAZARD -> ChoicePledgeStepContent(
                                        icon = Icons.Outlined.ReportProblem,
                                        gradient = GRADIENT_SAFETY_AMBER,
                                        titleRes = R.string.driver_safety_hazard_label,
                                        subtitleRes = R.string.driver_safety_hazard_helper,
                                        options = listOf(
                                            "LOW" to stringResource(R.string.driver_safety_hazard_low),
                                            "MEDIUM" to stringResource(R.string.driver_safety_hazard_medium),
                                            "HIGH" to stringResource(R.string.driver_safety_hazard_high)
                                        ),
                                        selectedLabel = form.hazardAwareness,
                                        enabled = inputsEnabled,
                                        onSelect = { label ->
                                            vm.updateHazardAwareness(label)
                                            vm.updateCurrentStep(step + 1)
                                        }
                                    )
                                    STEP_WEATHER -> ChoicePledgeStepContent(
                                        icon = Icons.Outlined.WbSunny,
                                        gradient = GRADIENT_SAFETY_SKY,
                                        titleRes = R.string.driver_safety_weather_label,
                                        subtitleRes = R.string.driver_safety_weather_helper,
                                        options = listOf(
                                            stringResource(R.string.driver_safety_weather_clear).let { it to it },
                                            stringResource(R.string.driver_safety_weather_cloudy).let { it to it },
                                            stringResource(R.string.driver_safety_weather_light_rain).let { it to it },
                                            stringResource(R.string.driver_safety_weather_heavy_rain).let { it to it },
                                            stringResource(R.string.driver_safety_weather_fog).let { it to it }
                                        ),
                                        selectedLabel = form.weatherCondition,
                                        enabled = inputsEnabled,
                                        onSelect = { label ->
                                            vm.updateWeatherCondition(label)
                                            vm.updateCurrentStep(step + 1)
                                        }
                                    )
                                    STEP_SLEEP -> SleepHoursStepContent(
                                        value = form.sleepHours,
                                        enabled = inputsEnabled,
                                        onChange = vm::updateSleepHours,
                                        onAdvance = { vm.updateCurrentStep(step + 1) }
                                    )
                                    STEP_NOTES -> NotesStepContent(
                                        routeRiskNote = form.routeRiskNote,
                                        onRouteRiskChange = vm::updateRouteRiskNote,
                                        incidentReport = form.incidentReport,
                                        onIncidentChange = vm::updateIncidentReport,
                                        enabled = inputsEnabled,
                                        onAdvance = { vm.updateCurrentStep(step + 1) }
                                    )
                                    STEP_CONFIRM -> FinalConfirmStepContent(
                                        value = form.confirmed,
                                        belumCount = form.belumPledgeCount(),
                                        enabled = inputsEnabled,
                                        onAnswer = vm::updateConfirmed,
                                        submitting = state.submitting,
                                        onSubmit = { vm.submit() }
                                    )
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
private fun StatusCard(
    statusLoading: Boolean,
    statusToday: Boolean?,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        statusLoading -> stringResource(R.string.driver_safety_status_loading)
        statusToday == true -> stringResource(R.string.driver_safety_status_submitted)
        statusToday == false -> stringResource(R.string.driver_safety_status_not_submitted)
        else -> stringResource(R.string.driver_safety_status_unknown)
    }
    val chipKind = when {
        statusLoading -> Dsv2StatusKind.Pending
        statusToday == true -> Dsv2StatusKind.Approved
        else -> Dsv2StatusKind.Pending
    }
    DriverStatusSummaryCard(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.driver_safety_status_title),
        statusText = statusText,
        statusKind = chipKind,
        description = stringResource(R.string.driver_safety_status_label, statusText),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PledgeStepScaffold(
    icon: ImageVector,
    gradient: List<Color>,
    statement: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        Spacer(Modifier.height(AppSpacing.md))
        PledgeGradientIconBadge(icon = icon, gradient = gradient, size = 96.dp, iconSize = 44.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
        ) {
            Text(
                text = statement,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        control()
    }
}

@Composable
private fun BooleanPledgeStepContent(
    spec: BooleanPledgeSpec,
    enabled: Boolean,
    onAdvance: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    PledgeStepScaffold(
        icon = spec.icon,
        gradient = spec.gradient,
        statement = stringResource(spec.statementRes)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
        ) {
            PledgeAnswerButton(
                text = stringResource(R.string.driver_safety_answer_yes),
                selected = spec.value == true,
                positive = true,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag(DriverMaestroTags.SAFETY_PLEDGE_YES),
                onClick = {
                    val wasUnanswered = spec.value == null
                    spec.onAnswer(true)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (wasUnanswered) onAdvance()
                }
            )
            PledgeAnswerButton(
                text = stringResource(R.string.driver_safety_answer_no),
                selected = spec.value == false,
                positive = false,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag(DriverMaestroTags.SAFETY_PLEDGE_NO),
                onClick = {
                    spec.onAnswer(false)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
        }
        if (spec.value == false) {
            Spacer(Modifier.height(AppSpacing.sm))
            SafetyBelumWarning(modifier = Modifier.padding(horizontal = AppSpacing.md))
        }
        if (spec.value != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            DriverPrimaryButton(
                text = stringResource(R.string.driver_safety_action_continue),
                onClick = onAdvance,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
            )
        }
    }
}

@Composable
private fun PledgeAnswerButton(
    text: String,
    selected: Boolean,
    positive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = when {
        selected && positive -> AppPalette.successContainerSolid
        selected && !positive -> AppPalette.errorContainerSolid
        else -> AppPalette.surfaceVariant
    }
    val border = when {
        selected && positive -> AppPalette.success
        selected && !positive -> AppPalette.error
        else -> AppPalette.outlineVariant
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) {
                if (positive) AppPalette.onSuccessContainer else AppPalette.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun SafetyBelumWarning(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppPalette.warningContainerSolid)
            .padding(AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ReportProblem,
            contentDescription = null,
            tint = AppPalette.warningIcon,
            modifier = Modifier.size(Dsv2Sizing.icon)
        )
        Text(
            text = stringResource(R.string.driver_safety_belum_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChoicePledgeStepContent(
    icon: ImageVector,
    gradient: List<Color>,
    titleRes: Int,
    subtitleRes: Int? = null,
    options: List<Pair<String, String>>,
    selectedLabel: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    PledgeStepScaffold(
        icon = icon,
        gradient = gradient,
        statement = stringResource(titleRes),
        subtitle = subtitleRes?.let { stringResource(it) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            options.forEach { (value, label) ->
                val selected = selectedLabel == value
                DriverSelectableSurfaceRow(
                    active = selected,
                    enabled = enabled,
                    onClick = { onSelect(value) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = AppPalette.success
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepHoursStepContent(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onAdvance: () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    PledgeStepScaffold(
        icon = Icons.Outlined.Bedtime,
        gradient = GRADIENT_SAFETY_CALM,
        statement = stringResource(R.string.driver_safety_sleep_hours_label)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(stringResource(R.string.driver_safety_sleep_hours_helper)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusEvent { if (it.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() } }
            )
            SkipOrContinueRow(enabled = enabled, onAdvance = onAdvance)
        }
    }
}

@Composable
private fun NotesStepContent(
    routeRiskNote: String,
    onRouteRiskChange: (String) -> Unit,
    incidentReport: String,
    onIncidentChange: (String) -> Unit,
    enabled: Boolean,
    onAdvance: () -> Unit
) {
    PledgeStepScaffold(
        icon = Icons.Outlined.EditNote,
        gradient = GRADIENT_SAFETY_RULE,
        statement = stringResource(R.string.driver_safety_notes_step_title)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            OutlinedTextField(
                value = routeRiskNote,
                onValueChange = onRouteRiskChange,
                label = { Text(stringResource(R.string.driver_safety_route_risk_label)) },
                supportingText = { Text(stringResource(R.string.driver_safety_route_risk_helper)) },
                enabled = enabled,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = incidentReport,
                onValueChange = onIncidentChange,
                label = { Text(stringResource(R.string.driver_safety_incident_label)) },
                supportingText = { Text(stringResource(R.string.driver_safety_incident_helper)) },
                enabled = enabled,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            SkipOrContinueRow(enabled = enabled, onAdvance = onAdvance)
        }
    }
}

@Composable
private fun SkipOrContinueRow(enabled: Boolean, onAdvance: () -> Unit) {
    DriverPrimaryButton(
        text = stringResource(R.string.driver_safety_action_continue),
        onClick = onAdvance,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FinalConfirmStepContent(
    value: Boolean?,
    belumCount: Int,
    enabled: Boolean,
    onAnswer: (Boolean) -> Unit,
    submitting: Boolean,
    onSubmit: () -> Unit
) {
    PledgeStepScaffold(
        icon = Icons.Outlined.Verified,
        gradient = GRADIENT_SAFETY_GOOD,
        statement = stringResource(R.string.driver_safety_confirmation_label)
    ) {
        if (belumCount > 0) {
            SafetyBelumSummary(
                belumCount = belumCount,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
            )
            Spacer(Modifier.height(AppSpacing.sm))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
        ) {
            PledgeAnswerButton(
                text = stringResource(R.string.driver_safety_answer_yes),
                selected = value == true,
                positive = true,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag(DriverMaestroTags.SAFETY_PLEDGE_YES),
                onClick = { onAnswer(true) }
            )
            PledgeAnswerButton(
                text = stringResource(R.string.driver_safety_answer_no),
                selected = value == false,
                positive = false,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag(DriverMaestroTags.SAFETY_PLEDGE_NO),
                onClick = { onAnswer(false) }
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        val hint = when {
            value == false -> stringResource(R.string.driver_error_safety_confirm_required)
            value == null -> stringResource(R.string.driver_error_safety_confirm_required)
            else -> null
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
            )
            Spacer(Modifier.height(AppSpacing.xs))
        }
        DriverPrimaryButton(
            text = stringResource(R.string.driver_safety_submit),
            onClick = onSubmit,
            enabled = value == true && !submitting,
            loading = submitting,
            spinnerOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md)
                .testTag(DriverMaestroTags.SAFETY_SUBMIT_BUTTON)
        )
    }
}

@Composable
private fun SafetyBelumSummary(belumCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppPalette.warningContainerSolid)
            .padding(AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ReportProblem,
            contentDescription = null,
            tint = AppPalette.warningIcon,
            modifier = Modifier.size(Dsv2Sizing.icon)
        )
        Text(
            text = stringResource(R.string.driver_safety_belum_summary_format, belumCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SafetyPledgeCompleteScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val scale = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0.4f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!reduceMotion) {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AppPalette.successContainerSolid)
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = AppPalette.success,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            text = stringResource(R.string.driver_success_safety_submitted),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = stringResource(R.string.driver_safety_once_per_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpacing.xl))
        DriverPrimaryButton(
            text = stringResource(R.string.driver_back_home),
            onClick = onBackHome,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SafetyLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dsv2Spacing.sm)
    ) {
        DriverCard(modifier = Modifier.fillMaxWidth(), secondary = true) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(AppSpacing.xl))
            Spacer(Modifier.height(AppSpacing.xs))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(AppSpacing.md))
        }
        DriverCard(modifier = Modifier.fillMaxWidth()) {
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(AppSpacing.lg))
            Spacer(Modifier.height(AppSpacing.sm))
            SkeletonBlock(Modifier.fillMaxWidth().height(AppSpacing.xxl * 3))
        }
    }
}
