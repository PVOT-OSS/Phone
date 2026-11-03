/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.app.Dialog
import android.os.Looper
import androidx.activity.ComponentActivity
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The in-call dialogs: their buttons, checkboxes and how they close. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class InCallDialogsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    private fun show(create: () -> Dialog): Dialog {
        lateinit var dialog: Dialog
        compose.runOnUiThread {
            dialog = create()
            dialog.setOnCancelListener { events += "cancelled" }
            dialog.show()
        }
        compose.waitForIdle()
        return dialog
    }

    @Test
    fun sendingThePostDialTonesClosesTheDialog() {
        val dialog = show { InCallDialogs.postDial(compose.activity, "1234", { events += "send" }) }
        compose.onNodeWithText("Send the following tones?\n1234").assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertThat(events).containsExactly("send")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun declineCancelsThePostDialDialog() {
        val dialog = show { InCallDialogs.postDial(compose.activity, "1234", { events += "send" }) }
        compose.onNodeWithText("No").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(events).containsExactly("cancelled")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun aDisconnectMessageClosesWithItsButton() {
        val dialog = show { InCallDialogs.message(compose.activity, "Call failed") }
        compose.onNodeWithText("Call failed").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun theWifiHandoverDialogRemembersDoNotShow() {
        show { InCallDialogs.wifiHandoverFailed(compose.activity) { events += "ok $it" } }
        compose.onNodeWithText("Do not show this again").performClick()
        compose.onNodeWithText("OK").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(events).containsExactly("ok true", "cancelled").inOrder()
    }

    @Test
    fun theInternationalCallDialogReportsTheWarningChoice() {
        val dialog = show {
            InCallDialogs.internationalCallOnWifi(
                compose.activity,
                true,
                { events += "continue $it" },
                { events += "stop $it" },
            )
        }
        compose.onNodeWithText("Show warning every time").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertThat(events).containsExactly("stop false")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun theInternationalCallDialogOnlyClosesThroughItsButtons() {
        val dialog = show { InCallDialogs.internationalCallOnWifi(compose.activity, false, {}, {}) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertThat(dialog.isShowing).isTrue()
    }

    @Test
    fun screenshotVideoCallNotAvailableDark() {
        show { InCallDialogs.videoCallNotAvailable(compose.activity) {} }
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/incall/video_call_not_available_dark.png")
    }

    @Test
    fun screenshotInternationalCallDark() {
        show { InCallDialogs.internationalCallOnWifi(compose.activity, false, {}, {}) }
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/incall/international_call_on_wifi_dark.png")
    }
}
