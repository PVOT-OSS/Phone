/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.rtt

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
import org.prauga.phone.dialer.glidephotomanager.PhotoInfo
import org.prauga.phone.dialer.ui.SolidGlassTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The saved RTT conversation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class RttTranscriptScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun message(text: String, remote: Boolean, at: Long = 0L) =
        RttTranscriptMessage.newBuilder()
            .setContent(text)
            .setIsRemote(remote)
            .setTimestamp(at)
            .build()

    private val conversation =
        listOf(
            message("Hi, can you hear me?", remote = true),
            message("I'm typing because it's loud here", remote = true),
            message("Yes, all good", remote = false),
        )

    @Test
    fun theFullDateGoesUnderTheFirstRun() {
        assertThat(firstPositionToShowTimestamp(conversation)).isEqualTo(1)
        assertThat(firstPositionToShowTimestamp(listOf(message("a", true)))).isEqualTo(0)
        assertThat(firstPositionToShowTimestamp(emptyList())).isEqualTo(0)
    }

    @Test
    fun bothSidesAreShownAndBackLeaves() {
        var back = false
        compose.setContent {
            SolidGlassTheme {
                RttTranscriptScreen(
                    "Ada Lovelace",
                    PhotoInfo.newBuilder().setName("Ada Lovelace").build(),
                    conversation,
                ) {
                    back = true
                }
            }
        }
        compose.onNodeWithText("Hi, can you hear me?").assertExists()
        compose.onNodeWithText("Yes, all good").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(back).isTrue()
    }

    @Test fun screenshotDark() = screenshot("rtt_transcript_dark")

    @Test
    @Config(qualifiers = "+notnight")
    fun screenshotLight() = screenshot("rtt_transcript_light")

    private fun screenshot(name: String) {
        compose.setContent {
            SolidGlassTheme {
                RttTranscriptScreen(
                    "Ada Lovelace",
                    PhotoInfo.newBuilder().setName("Ada Lovelace").build(),
                    conversation,
                ) {}
            }
        }
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/rtt/$name.png")
    }
}
