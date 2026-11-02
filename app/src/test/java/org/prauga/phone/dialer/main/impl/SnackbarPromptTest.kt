/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneSnackbarHost
import org.prauga.phone.dialer.ui.PhoneTheme
import org.robolectric.RobolectricTestRunner

/** Post-call prompts show in the screen's own snackbar, which needs no AppCompat theme. */
@RunWith(RobolectricTestRunner::class)
class SnackbarPromptTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aPromptShowsAndRunsItsActionThenFinishes() {
        val hostState = SnackbarHostState()
        lateinit var prompt: SnackbarPrompt
        compose.setContent {
            prompt = SnackbarPrompt(rememberCoroutineScope(), hostState)
            PhoneTheme { PhoneSnackbarHost(hostState) }
        }
        var acted = 0
        var done = 0
        compose.runOnIdle { prompt.show("Send a message?", "Message", { acted++ }, true) { done++ } }

        compose.onNodeWithText("Send a message?").assertIsDisplayed()
        compose.onNodeWithText("Message").performClick()
        compose.waitForIdle()

        assertThat(acted).isEqualTo(1)
        assertThat(done).isEqualTo(1)
        compose.onNodeWithText("Send a message?").assertDoesNotExist()
    }
}
