/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The manage conference screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class ConferenceManagerScreenTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val ada = ConferenceParticipant("1", "Ada Lovelace", "+15550100", null, "ada", onHold = false, canSeparate = true, canDisconnect = true)
    private val bob = ConferenceParticipant("2", null, "+15550199", null, "bob", onHold = true, canSeparate = false, canDisconnect = true)

    private fun show(vararg participants: ConferenceParticipant) = compose.setContent {
        PhoneScreenTheme {
            ConferenceManagerScreen(
                participants = participants.toList(),
                onSeparate = { events += "separate $it" },
                onDisconnect = { events += "disconnect $it" },
                onBack = { events += "back" },
            )
        }
    }

    @Test
    fun aParticipantCanBeSplitOutOrHungUpOn() {
        show(ada)
        compose.onNodeWithContentDescription("go private").performClick()
        compose.onNodeWithContentDescription("End call").performClick()
        assertThat(events).containsExactly("separate 1", "disconnect 1").inOrder()
    }

    @Test
    fun splittingOutIsOfferedOnlyWhenAllowed() {
        show(bob)
        compose.onNodeWithContentDescription("go private").assertDoesNotExist()
    }

    @Test
    fun aParticipantOnHoldSaysSo() {
        show(bob)
        compose.onNodeWithText("+15550199").assertExists()
        compose.onNodeWithText("On hold").assertExists()
    }

    @Test
    fun backLeaves() {
        show(ada)
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(events).containsExactly("back")
    }

    @Test
    fun screenshotDark() {
        show(ada, bob)
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/incall/manage_conference_dark.png")
    }
}
