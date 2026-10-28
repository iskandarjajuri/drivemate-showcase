package com.drivemate.showcase.feature.expat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivemate.showcase.feature.expat.settings.ExpatSettingsTags
import com.drivemate.showcase.ui.DriveMateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpatBellActionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun click_invokesOpenNotifications() {
        var clicks = 0
        rule.setContent { DriveMateTheme { ExpatBellAction(unread = 0, onClick = { clicks++ }) } }
        rule.onNodeWithTag(ExpatSettingsTags.BELL).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun badge_capsAtNinePlus_andDescriptionCarriesTheCount() {
        rule.setContent { DriveMateTheme { ExpatBellAction(unread = 16, onClick = {}) } }
        rule.onNodeWithText("9+").assertIsDisplayed()
        rule.onNodeWithTag(ExpatSettingsTags.BELL).assert(SemanticsMatcher("memuat 16") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty().contains("16")
        })
    }

    @Test
    fun noBadge_whenNothingUnread() {
        rule.setContent { DriveMateTheme { ExpatBellAction(unread = 0, onClick = {}) } }
        rule.onNodeWithText("9+").assertDoesNotExist()
        rule.onNodeWithText("0").assertDoesNotExist()
    }
}
