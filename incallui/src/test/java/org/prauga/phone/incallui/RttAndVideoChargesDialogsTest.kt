/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.incallui.video.impl.VideoChargesAlertDialogFragment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The RTT request and video charges dialogs shown during a call. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class RttAndVideoChargesDialogsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private val details = mutableStateOf<String?>(null)

    private fun show(create: () -> Dialog): Dialog {
        lateinit var dialog: Dialog
        compose.runOnUiThread {
            dialog = create()
            dialog.show()
        }
        compose.waitForIdle()
        return dialog
    }

    private fun rttRequest() = show {
        RttRequestDialogFragment.createDialog(
            compose.activity,
            details,
            { events += "accept" },
            { events += "decline" },
        )
    }

    private fun videoCharges() = show {
        VideoChargesAlertDialogFragment.createDialog(compose.activity) { events += "ok $it" }
    }

    @Test
    fun theRttRequestShowsWhoAsksOnceKnown() {
        rttRequest()
        compose.onNodeWithText("Ada wants to use RTT", substring = true).assertDoesNotExist()
        compose.runOnUiThread { details.value = "Ada wants to use RTT" }
        compose.onNodeWithText("Ada wants to use RTT").assertExists()
    }

    @Test
    fun joiningRttAccepts() {
        val dialog = rttRequest()
        compose.onNodeWithText("Join RTT").performClick()
        assertThat(events).containsExactly("accept")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun theVideoChargesDialogReportsDoNotShowAgain() {
        val dialog = videoCharges()
        compose.onNodeWithText("Do not show again").performClick()
        compose.onNodeWithText("OK").performClick()
        assertThat(events).containsExactly("ok true")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun screenshotRttRequestDark() {
        details.value = "Ada Lovelace wants to use RTT"
        rttRequest()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/rtt_request_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotRttRequestLight() {
        details.value = "Ada Lovelace wants to use RTT"
        rttRequest()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/rtt_request_light.png")
    }

    @Test
    fun screenshotVideoChargesDark() {
        videoCharges()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/video_charges_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotVideoChargesLight() {
        videoCharges()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/video_charges_light.png")
    }
}
