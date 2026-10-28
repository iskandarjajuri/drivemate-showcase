package com.drivemate.showcase.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.drivemate.showcase.feature.driver.DriverMaestroTags
import com.drivemate.showcase.feature.driver.DriverTugasNavState
import com.drivemate.showcase.ui.DriveMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DriverFloatingNavBarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun allFiveTabs_render_andTappingTugas_selectsTugasRoute() {
        var selectedRoute: String? = null

        rule.setContent {
            DriveMateTheme {
                DriverFloatingNavBarContent(
                    currentRoute = NavRoutes.DriverHome.route,
                    tugasState = DriverTugasNavState(doneCount = 1, totalCount = 2),
                    tappedIn = false,
                    celebrate = false,
                    onCelebrationPlayed = {},
                    onTabSelected = { route -> selectedRoute = route }
                )
            }
        }

        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverHome.route)).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverAttendance.route)).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverTugas.route)).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverChats.route)).assertIsDisplayed()
        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverHistory.route)).assertIsDisplayed()

        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverTugas.route)).performClick()

        rule.runOnIdle {
            assertEquals(NavRoutes.DriverTugas.route, selectedRoute)
        }
    }

    @Test
    fun tappingAlreadySelectedTab_doesNotFireOnTabSelected() {
        var selectedRoute: String? = null

        rule.setContent {
            DriveMateTheme {
                DriverFloatingNavBarContent(
                    currentRoute = NavRoutes.DriverHome.route,
                    tugasState = DriverTugasNavState(doneCount = 0, totalCount = 2),
                    tappedIn = false,
                    celebrate = false,
                    onCelebrationPlayed = {},
                    onTabSelected = { route -> selectedRoute = route }
                )
            }
        }

        rule.onNodeWithTag(DriverMaestroTags.floatingNavItemTag(NavRoutes.DriverHome.route)).performClick()

        rule.runOnIdle {
            assertNull(selectedRoute)
        }
    }
}
