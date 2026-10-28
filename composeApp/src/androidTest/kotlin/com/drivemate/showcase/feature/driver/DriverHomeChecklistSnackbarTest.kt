package com.drivemate.showcase.feature.driver

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import com.drivemate.showcase.R
import com.drivemate.showcase.ui.DriveMateTheme
import org.junit.Rule
import org.junit.Test

/**
 * Regression for a bug found in review: `checklistSubmittedAtMillis` used to
 * be observed reactively from the SavedStateHandle, and the screen's own
 * "consume" write (setting it back to null) fed back in as a new emission --
 * cancelling the `LaunchedEffect` that was still inside `showSnackbar`. The
 * snackbar appeared for at most one frame instead of staying up. The fix
 * (AppNavGraph) reads-and-removes the value once via `rememberSaveable`
 * instead of `collectAsState()`, so this test passes it as a plain, never-
 * changing value -- exactly what the fixed call site now does.
 */
@OptIn(ExperimentalTestApi::class)
class DriverHomeChecklistSnackbarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun checklistSubmittedSnackbar_staysVisible() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        rule.setContent {
            DriveMateTheme {
                DriverHomeScreen(
                    state = DriverUiState.Idle,
                    trackingState = DriverTrackingUiState(),
                    driverName = "Iskandar Jajuri",
                    driverEmail = "iskandar@example.com",
                    driverRole = "Driver",
                    checklistSubmitted = true,
                    safetySubmitted = false,
                    plannedStartMillis = null,
                    plannedEndMillis = null,
                    planSource = null,
                    recentWorkDays = emptyList(),
                    sopAccepted = null,
                    homeStateLoading = false,
                    onOpenWorkdayDetail = {},
                    checklistSubmittedAtMillis = 1_700_000_000_000L,
                    onOpenAttendance = {},
                    onOpenAccount = {},
                    onRefresh = {},
                    onOpenChecklist = {},
                    onOpenSafety = {},
                    onOpenSop = {},
                    onOpenHistory = {},
                    onOpenChat = {},
                    onTapIn = {},
                    onTapOut = {},
                    onTrackingSync = { _, _, _ -> }
                )
            }
        }

        val actionLabel = context.getString(R.string.driver_home_checklist_submitted_action)
        rule.waitUntilAtLeastOneExists(hasText(actionLabel), timeoutMillis = 3_000)

        // Regression guard: still there a beat later, not just for one frame.
        Thread.sleep(500)
        rule.onNodeWithText(actionLabel).assertExists()
    }
}
