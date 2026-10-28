package com.drivemate.showcase.feature.driver

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import com.drivemate.showcase.ui.DriveMateTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DriverFlowObservabilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun driverHome_exposesStableObservabilityTags() {
        var openedAttendance = false
        var openedChecklist = false
        var openedSafety = false

        rule.setContent {
            DriveMateTheme {
                DriverHomeScreen(
                    state = DriverUiState.Idle,
                    trackingState = DriverTrackingUiState(),
                    driverName = "Iskandar Jajuri",
                    driverEmail = "iskandar@example.com",
                    driverRole = "Driver",
                    checklistSubmitted = false,
                    safetySubmitted = false,
                    plannedStartMillis = null,
                    plannedEndMillis = null,
                    planSource = null,
                    recentWorkDays = emptyList(),
                    sopAccepted = null,
                    homeStateLoading = false,
                    onOpenWorkdayDetail = {},
                    checklistSubmittedAtMillis = null,
                    onOpenAttendance = { openedAttendance = true },
                    onOpenChecklist = { openedChecklist = true },
                    onOpenSafety = { openedSafety = true },
                    onOpenSop = {},
                    onOpenHistory = {},
                    onOpenChat = {},
                    onOpenAccount = {},
                    onRefresh = {},
                    onTapIn = {},
                    onTapOut = {},
                    onTrackingSync = { _, _, _ -> }
                )
            }
        }

        rule.onNodeWithTag(DriverMaestroTags.HOME_ROOT).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.TODAY_SECTION).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.DAILY_FLOW_SECTION).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.ATTENDANCE_ENTRY).assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithTag(DriverMaestroTags.HOME_CONTENT)
            .performScrollToNode(hasTestTag(DriverMaestroTags.SAFETY_ENTRY))
        rule.onNodeWithTag(DriverMaestroTags.SAFETY_ENTRY).assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithTag(DriverMaestroTags.HOME_CONTENT)
            .performScrollToNode(hasTestTag(DriverMaestroTags.CHECKLIST_ENTRY))
        rule.onNodeWithTag(DriverMaestroTags.CHECKLIST_ENTRY).assertIsDisplayed().assertHasClickAction()

        rule.onNodeWithTag(DriverMaestroTags.ATTENDANCE_ENTRY).performClick()
        rule.onNodeWithTag(DriverMaestroTags.SAFETY_ENTRY).performClick()
        rule.onNodeWithTag(DriverMaestroTags.CHECKLIST_ENTRY).performClick()

        rule.runOnIdle {
            assertTrue(openedAttendance)
            assertTrue(openedSafety)
            assertTrue(openedChecklist)
        }
    }

    @Test
    fun driverAttendance_exposesStableObservabilityTags() {
        var tappedIn = false

        rule.setContent {
            DriveMateTheme {
                DriverAttendanceScreen(
                    state = DriverUiState.Idle,
                    checklistSubmitted = true,
                    safetySubmitted = true,
                    plannedStartMillis = null,
                    plannedEndMillis = null,
                    planSource = null,
                    onTapIn = { tappedIn = true },
                    onTapOut = {},
                    onBack = {},
                    onBackHome = {},
                    onRefresh = {}
                )
            }
        }

        rule.onNodeWithTag(DriverMaestroTags.ATTENDANCE_ROOT).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.DAILY_STATUS).assertIsDisplayed()
        // Single CTA kontekstual -- Idle hanya menampilkan Tap In, Tap Out tidak ada di tree.
        rule.onNodeWithTag(DriverMaestroTags.TAP_IN_BUTTON).assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithTag(DriverMaestroTags.TAP_OUT_BUTTON).assertDoesNotExist()

        rule.onNodeWithTag(DriverMaestroTags.TAP_IN_BUTTON).performClick()

        rule.runOnIdle {
            assertTrue(tappedIn)
        }
    }
}
