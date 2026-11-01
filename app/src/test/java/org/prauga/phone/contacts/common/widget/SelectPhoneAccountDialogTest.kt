/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.widget

import android.app.Dialog
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The calling account picker: its rows, the default checkbox and how it closes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class SelectPhoneAccountDialogTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private val setDefault = mutableStateOf(false)

    private fun rows() = listOf(
        SelectPhoneAccountDialogFragment.AccountRow(
            "Work SIM",
            "+1 555-0100",
            null,
            ContextCompat.getDrawable(compose.activity, R.drawable.ic_launcher_foreground),
            true,
        ),
        SelectPhoneAccountDialogFragment.AccountRow("Personal SIM", "+1 555-0199", "Last used", null, true),
        SelectPhoneAccountDialogFragment.AccountRow("Travel SIM", null, "No service", null, false),
    )

    private fun show(setDefaultLabel: String? = "Always use this for calls"): Dialog {
        lateinit var dialog: Dialog
        compose.runOnUiThread {
            dialog = SelectPhoneAccountDialogFragment.createDialog(
                compose.activity,
                "Call with",
                rows(),
                setDefaultLabel,
                setDefault,
            ) { events += "selected $it default=${setDefault.value}" }
            dialog.setOnCancelListener { events += "cancelled" }
            dialog.show()
        }
        compose.waitForIdle()
        return dialog
    }

    @Test
    fun pickingAnAccountReportsItAndTheCheckbox() {
        val dialog = show()
        compose.onNodeWithText("Always use this for calls").performClick()
        compose.onNodeWithText("Personal SIM").performClick()
        assertThat(events).containsExactly("selected 1 default=true")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun aDisabledAccountCannotBePicked() {
        show()
        compose.onNode(hasText("Travel SIM", substring = true) and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun withoutSetDefaultThereIsNoCheckbox() {
        show(setDefaultLabel = null)
        compose.onNodeWithText("Always use this for calls").assertDoesNotExist()
    }

    @Test
    fun backCancels() {
        val dialog = show()
        compose.runOnUiThread { (dialog as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(events).containsExactly("cancelled")
    }

    @Test
    fun screenshotDark() {
        show()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/dialogs/select_phone_account_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/dialogs/select_phone_account_light.png")
    }
}
