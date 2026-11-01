/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.postcall

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The post-call sheet: quick replies, the custom message and Send. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class PostCallSheetTest {

    @get:Rule val compose = createComposeRule()

    private val sent = mutableListOf<String>()
    private var dismissed = false

    private val messages =
        listOf(
            "This is urgent. Call me back.",
            "Call me back when you have some time.",
            "Not urgent, we can chat later.",
        )

    private fun show(charLimit: Int? = null) {
        compose.setContent {
            PhoneScreenTheme(fillBackground = false) {
                PostCallSheet(
                    messages,
                    charLimit,
                    onSend = { sent += it },
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun sendIsOffUntilThereIsAMessage() {
        show()
        compose.onNodeWithText("Send message").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("On my way")
        compose.onNodeWithText("Send message").assertIsEnabled().performClick()
        assertThat(sent).containsExactly("On my way")
    }

    @Test
    fun aQuickReplyFillsTheMessage() {
        show()
        compose.onNodeWithText("Not urgent, we can chat later.").performClick()
        compose.onNodeWithText("Send message").performClick()
        assertThat(sent).containsExactly("Not urgent, we can chat later.")
    }

    @Test
    fun theCharacterLimitCutsQuickReplies() {
        show(charLimit = 9)
        compose.onNodeWithText("This is urgent. Call me back.").performClick()
        compose.onNodeWithText("Send message").performClick()
        assertThat(sent).containsExactly("This is u")
    }

    @Test
    fun startsWithTheTitle() {
        show()
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose
            .onNodeWithText(context.getString(org.prauga.phone.calls.R.string.post_call_message))
            .assertExists()
        assertThat(dismissed).isFalse()
    }

    @Test
    fun screenshotDark() {
        show()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/post_call_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/post_call_light.png")
    }
}
