package com.drivemate.showcase.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import org.junit.Rule
import org.junit.Test

private const val KEY = "test_result_key"

/**
 * Regression for a bug found in review: an earlier fix used
 * `rememberSaveable` to read-and-remove a one-shot navigation result.
 * Navigation Compose persists `rememberSaveable` state per back-stack entry
 * across navigate-away-and-back (the same mechanism that restores scroll
 * position), so the already-consumed value kept coming back every time the
 * destination was revisited -- the checklist-submitted snackbar would
 * reappear every time the driver returned to Home. `StateRestorationTester`
 * exercises the same save/restore primitive Navigation Compose relies on,
 * without needing a full NavHost.
 */
class RememberConsumedLongResultTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun consumedValue_doesNotReappear_afterSaveRestoreCycle() {
        val restorationTester = StateRestorationTester(rule)
        val handle = SavedStateHandle(mapOf(KEY to 123L))

        restorationTester.setContent {
            val value = rememberConsumedLongResult(handle, KEY)
            Text(text = value?.toString() ?: "none")
        }

        rule.onNodeWithText("123").assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("none").assertExists()
    }
}
