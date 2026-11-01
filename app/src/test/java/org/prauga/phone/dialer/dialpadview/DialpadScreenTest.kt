/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.dialpadview

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.EditText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Compose dialpad: keys, delete, call button and the options sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class DialpadScreenTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val callbacks =
        object : DialpadCallbacks {
            override fun onKey(keyCode: Int, pressed: Boolean) {
                events += "key $keyCode ${if (pressed) "down" else "up"}"
            }

            override fun onKeyLongPress(keyCode: Int): Boolean {
                events += "long $keyCode"
                return keyCode == KeyEvent.KEYCODE_0
            }

            override fun onDelete() {
                events += "delete"
            }

            override fun onDeleteLongPress() {
                events += "clear"
            }

            override fun onCall() {
                events += "call"
            }

            override fun onMenuOpen() {
                events += "menu"
            }

            override fun onAddPause() {
                events += "pause"
            }

            override fun onAddWait() {
                events += "wait"
            }

            override fun onCallWithNote() {
                events += "note"
            }

            override fun onSelectAccount(handle: PhoneAccountHandle) {
                events += "account ${handle.id}"
            }
        }

    private val state = DialpadUiState()

    private fun show() {
        val digits =
            LayoutInflater.from(ApplicationProvider.getApplicationContext<Context>())
                .inflate(R.layout.dialpad_digits, null) as EditText
        compose.setContent { PhoneScreenTheme { DialpadScreen(state, digits, callbacks) } }
    }

    @Test
    fun aKeyGoesDownThenUp() {
        show()
        compose.onNodeWithTag(TestTags.dialpadKey("5")).performClick()
        assertThat(events)
            .containsExactly("key ${KeyEvent.KEYCODE_5} down", "key ${KeyEvent.KEYCODE_5} up")
            .inOrder()
    }

    @Test
    fun holdingZeroAsksForAPlus() {
        show()
        compose.onNodeWithTag(TestTags.dialpadKey("0")).performTouchInput { longClick() }
        assertThat(events)
            .containsExactly(
                "key ${KeyEvent.KEYCODE_0} down",
                "long ${KeyEvent.KEYCODE_0}",
                "key ${KeyEvent.KEYCODE_0} up",
            )
            .inOrder()
    }

    @Test
    fun holdingOneAsksForVoicemail() {
        show()
        compose.onNodeWithTag(TestTags.dialpadKey("1")).performTouchInput { longClick() }
        assertThat(events).contains("long ${KeyEvent.KEYCODE_1}")
        assertThat(events.last()).isEqualTo("key ${KeyEvent.KEYCODE_1} up")
    }

    @Test
    fun holdingAPlainKeyIsNotALongPress() {
        show()
        compose.onNodeWithTag(TestTags.dialpadKey("5")).performTouchInput { longClick() }
        assertThat(events)
            .containsExactly("key ${KeyEvent.KEYCODE_5} down", "key ${KeyEvent.KEYCODE_5} up")
            .inOrder()
    }

    @Test
    fun deleteRemovesOneAndHoldingClears() {
        state.deleteEnabled = true
        show()
        compose.onNodeWithTag(TestTags.DIALPAD_DELETE).performClick()
        compose.onNodeWithTag(TestTags.DIALPAD_DELETE).performTouchInput { longClick() }
        assertThat(events).containsExactly("delete", "clear").inOrder()
    }

    @Test
    fun deleteDoesNothingWithoutDigits() {
        show()
        compose.onNodeWithTag(TestTags.DIALPAD_DELETE).performClick()
        assertThat(events).isEmpty()
    }

    @Test
    fun theMenuButtonOpensTheOptions() {
        show()
        compose.onNodeWithTag(TestTags.DIALPAD_MENU).assertDoesNotExist()
        state.menuButtonVisible = true
        compose.onNodeWithTag(TestTags.DIALPAD_MENU).performClick()
        assertThat(events).containsExactly("menu")
    }

    @Test
    fun theOptionsActAndClose() {
        state.menuEnabled = true
        state.callWithNoteSupported = true
        state.accounts +=
            DialpadAccount(
                PhoneAccountHandle(ComponentName("p", "c"), "sim2"),
                "SIM 2",
                selected = false,
            )
        state.menuOpen = true
        show()
        compose.onNodeWithText("Add 2-sec pause").performClick()
        assertThat(state.menuOpen).isFalse()

        state.menuOpen = true
        compose.onNodeWithText("SIM 2").performClick()
        state.menuOpen = true
        compose.onNodeWithText("Call with a note").performClick()
        assertThat(events).containsExactly("pause", "account sim2", "note").inOrder()
    }

    @Test
    fun theOptionsNeedANumber() {
        state.menuOpen = true
        show()
        compose.onNodeWithText("Add wait").assertIsNotEnabled()
        compose.onNodeWithText("Call with a note").assertDoesNotExist()
    }

    @Test
    fun screenshotDark() {
        state.deleteEnabled = true
        state.menuButtonVisible = true
        state.hint = "Can't make emergency calls over WiFi"
        show()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/dialpad/dialpad_dark.png")
    }
}
