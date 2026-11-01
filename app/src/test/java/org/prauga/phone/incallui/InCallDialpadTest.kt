/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.EditText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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

/** The dialpad shown during a call. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class InCallDialpadTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(endCallSpace: Boolean = true) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val digits = LayoutInflater.from(context).inflate(R.layout.dialpad_digits, null) as EditText
        digits.setText("12#")
        compose.setContent {
            PhoneScreenTheme {
                InCallDialpad(digits, endCallSpace, onKey = { code, down -> events += "$code ${if (down) "down" else "up"}" }, onBack = { events += "back" })
            }
        }
    }

    @Test
    fun keysMapToTheirTones() {
        assertThat(dtmfCharOf(KeyEvent.KEYCODE_0)).isEqualTo('0')
        assertThat(dtmfCharOf(KeyEvent.KEYCODE_9)).isEqualTo('9')
        assertThat(dtmfCharOf(KeyEvent.KEYCODE_STAR)).isEqualTo('*')
        assertThat(dtmfCharOf(KeyEvent.KEYCODE_POUND)).isEqualTo('#')
        assertThat(dtmfCharOf(KeyEvent.KEYCODE_A)).isNull()
    }

    @Test
    fun aKeySendsItsToneWhileHeld() {
        show()
        compose.onNodeWithTag(TestTags.dialpadKey("7")).performClick()
        assertThat(events).containsExactly("${KeyEvent.KEYCODE_7} down", "${KeyEvent.KEYCODE_7} up").inOrder()
    }

    @Test
    fun holdingZeroIsNotAPlusDuringACall() {
        show()
        compose.onNodeWithTag(TestTags.dialpadKey("0")).performTouchInput { longClick() }
        assertThat(events).containsExactly("${KeyEvent.KEYCODE_0} down", "${KeyEvent.KEYCODE_0} up").inOrder()
    }

    @Test
    fun closeHidesTheDialpad() {
        show()
        compose.onNodeWithTag(TestTags.INCALL_DIALPAD_BACK).performClick()
        assertThat(events).containsExactly("back")
    }

    @Test
    fun screenshotDark() {
        show()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/incall/incall_dialpad_dark.png")
    }
}
