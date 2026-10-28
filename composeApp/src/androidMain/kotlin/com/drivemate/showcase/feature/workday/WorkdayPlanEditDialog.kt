package com.drivemate.showcase.feature.workday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import com.drivemate.showcase.core.util.TimeUtil
import com.drivemate.showcase.theme.Dimens
import com.drivemate.showcase.ui.patterns.DmFormDialog
import kotlinx.datetime.TimeZone

@Composable
fun WorkdayPlanEditDialog(
    titleRes: Int,
    errorRes: Int,
    epochDay: Int,
    initialStartMillis: Long?,
    initialEndMillis: Long?,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit
) {
    val zone = TimeZone.of("Asia/Jakarta")
    val initialStartText = initialStartMillis?.let { TimeUtil.formatHm(it, zone) }.orEmpty()
    val initialEndText = initialEndMillis?.let { TimeUtil.formatHm(it, zone) }.orEmpty()
    var startText by rememberSaveable(initialStartText) { mutableStateOf(initialStartText) }
    var endText by rememberSaveable(initialEndText) { mutableStateOf(initialEndText) }

    val hmPattern = remember { Regex("^([01]\\d|2[0-3]):[0-5]\\d$") }
    val startValid = hmPattern.matches(startText.trim())
    val endValid = hmPattern.matches(endText.trim())
    val startMillis = if (startValid) TimeUtil.parseHm(startText.trim(), epochDay.toLong(), zone) else null
    val endMillis = if (endValid) TimeUtil.parseHm(endText.trim(), epochDay.toLong(), zone) else null
    val rangeValid = startMillis != null && endMillis != null && startMillis < endMillis
    val formValid = startValid && endValid && rangeValid

    DmFormDialog(
        title = stringResource(titleRes),
        confirmLabel = stringResource(R.string.action_ok),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = {
            val safeStart = startMillis
            val safeEnd = endMillis
            if (safeStart != null && safeEnd != null && safeStart < safeEnd) {
                onSave(safeStart, safeEnd)
            }
        },
        onDismiss = onDismiss,
        confirmEnabled = formValid
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text(stringResource(R.string.admin_data_status_in)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !startValid
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text(stringResource(R.string.admin_data_status_out)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !endValid || (startValid && endValid && !rangeValid)
            )
            if (!formValid) {
                Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
