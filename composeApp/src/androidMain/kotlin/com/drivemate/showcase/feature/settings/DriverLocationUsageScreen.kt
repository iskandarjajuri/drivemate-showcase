package com.drivemate.showcase.feature.settings

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.exclude
import com.drivemate.showcase.ui.dsv2.DmScreenHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drivemate.showcase.R
import com.drivemate.showcase.components.DMTopAppBar
import com.drivemate.showcase.ui.AppSpacing
import com.drivemate.showcase.ui.SoftCard

/** Read-only info page -- there is no toggle here by design (see spec/PR notes). */
@Composable
fun DriverLocationUsageScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { DmScreenHeader(title = stringResource(R.string.driver_location_usage_title), onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.statusBars))
                .padding(AppSpacing.md)
        ) {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.driver_location_usage_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
