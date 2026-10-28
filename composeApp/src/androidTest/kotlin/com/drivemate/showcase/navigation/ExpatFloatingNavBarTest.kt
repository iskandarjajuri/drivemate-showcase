package com.drivemate.showcase.navigation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.drivemate.showcase.ui.DriveMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ExpatFloatingNavBarTest {
    @get:Rule
    val rule = createComposeRule()

    private fun tag(route: String) = ExpatNavTags.floatingNavItemTag(route)

    /** Deskripsi gabungan memuat semua angka (tidak bergantung pada bahasa aplikasi di perangkat). */
    private fun describes(vararg parts: String) = SemanticsMatcher("deskripsi memuat ${parts.toList()}") { node ->
        val cd = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
        parts.all { it in cd }
    }

    @Test
    fun fiveTabs_render_andNotificationsIsNotATab_andTappingSettingsSelectsSettingsRoute() {
        var selected: String? = null
        rule.setContent {
            DriveMateTheme {
                ExpatFloatingNavBarContent(
                    currentRoute = NavRoutes.ExpatHome.route,
                    onTabSelected = { selected = it }
                )
            }
        }
        rule.onNodeWithTag(tag(NavRoutes.ExpatHome.route)).assertIsDisplayed()
        rule.onNodeWithTag(tag(NavRoutes.ExpatReviewQueue.route)).assertIsDisplayed()
        rule.onNodeWithTag(tag(NavRoutes.ExpatDrivers.route)).assertIsDisplayed()
        rule.onNodeWithTag(tag(NavRoutes.ExpatChats.route)).assertIsDisplayed()
        rule.onNodeWithTag(tag(NavRoutes.ExpatSettings.route)).assertIsDisplayed()
        rule.onNodeWithTag(tag(NavRoutes.ExpatNotifications.route)).assertDoesNotExist()

        rule.onNodeWithTag(tag(NavRoutes.ExpatSettings.route)).performClick()
        rule.runOnIdle { assertEquals(NavRoutes.ExpatSettings.route, selected) }
    }

    @Test
    fun tappingAlreadySelectedTab_doesNotFire() {
        var selected: String? = null
        rule.setContent {
            DriveMateTheme {
                ExpatFloatingNavBarContent(
                    currentRoute = NavRoutes.ExpatSettings.route,
                    onTabSelected = { selected = it }
                )
            }
        }
        rule.onNodeWithTag(tag(NavRoutes.ExpatSettings.route)).performClick()
        rule.runOnIdle { assertNull(selected) }
    }

    @Test
    fun reviewTab_withProgress_describesPendingAndReviewedCounts() {
        rule.setContent {
            DriveMateTheme {
                ExpatFloatingNavBarContent(
                    currentRoute = NavRoutes.ExpatHome.route,
                    onTabSelected = {},
                    reviewRing = ExpatReviewRingState(pending = 16, reviewed = 19, dataReady = true)
                )
            }
        }
        rule.onNodeWithTag(tag(NavRoutes.ExpatReviewQueue.route)).assert(describes("16", "19", "35"))
    }

    @Test
    fun reviewTab_whenDataNotReady_hasNoCountsInDescription() {
        rule.setContent {
            DriveMateTheme {
                ExpatFloatingNavBarContent(
                    currentRoute = NavRoutes.ExpatHome.route,
                    onTabSelected = {},
                    reviewRing = ExpatReviewRingState(pending = 0, reviewed = 35, dataReady = false)
                )
            }
        }
        rule.onNodeWithTag(tag(NavRoutes.ExpatReviewQueue.route))
            .assert(SemanticsMatcher("tanpa angka palsu") { node ->
                val cd = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
                "35" !in cd
            })
    }
}
