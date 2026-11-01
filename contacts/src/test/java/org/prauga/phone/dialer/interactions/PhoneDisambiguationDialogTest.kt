/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.interactions

import android.app.Dialog
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dialog asking which of a contact's numbers to call. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class PhoneDisambiguationDialogTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    private val choices = listOf(
        PhoneDisambiguationDialog.Choice("Mobile", "+1 555-0100"),
        PhoneDisambiguationDialog.Choice("Work", "+1 555-0199"),
    )

    private fun show(): Dialog {
        lateinit var dialog: Dialog
        compose.runOnUiThread {
            dialog = PhoneDisambiguationDialog.create(compose.activity, "Choose number", choices) { position, primary ->
                events += "$position primary=$primary"
            }
            dialog.show()
        }
        compose.waitForIdle()
        return dialog
    }

    @Test
    fun tappingANumberChoosesItAndCloses() {
        val dialog = show()
        compose.onNodeWithText("+1 555-0199").performClick()
        assertThat(events).containsExactly("1 primary=false")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun rememberingTheChoiceIsReported() {
        show()
        compose.onNodeWithText("Remember this choice").performClick()
        compose.onNodeWithText("Mobile").performClick()
        assertThat(events).containsExactly("0 primary=true")
    }

    @Test
    fun screenshotDark() {
        show()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/dialogs/phone_disambiguation_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/dialogs/phone_disambiguation_light.png")
    }
}
