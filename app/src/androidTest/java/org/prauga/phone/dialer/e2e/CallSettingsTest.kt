/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.app.settings.DisplayOptions
import org.prauga.phone.dialer.app.settings.IncomingCallSettings
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.incallui.InCallPresenter
import org.prauga.phone.incallui.call.state.DialerCallState

/** Display options and incoming call settings take effect. */
@RunWith(AndroidJUnit4::class)
class CallSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val deviceRule = DeviceRule()
    private val keys = listOf(
        context.getString(R.string.display_options_theme_key),
        context.getString(R.string.display_options_force_portrait_key),
        IncomingCallSettings.KEY_ANSWER_GESTURE,
    )
    private val saved = prefs.all.filterKeys { it in keys }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @After
    fun restore() {
        prefs.edit {
            keys.forEach { remove(it) }
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        InCall.onMain { DisplayOptions.applyTheme(context) }
    }

    @Test
    fun themeChoiceSetsTheNightMode() {
        prefs.edit { putString(context.getString(R.string.display_options_theme_key), DisplayOptions.THEME_DARK) }
        InCall.onMain { DisplayOptions.applyTheme(context) }
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        prefs.edit { putString(context.getString(R.string.display_options_theme_key), DisplayOptions.THEME_LIGHT) }
        InCall.onMain { DisplayOptions.applyTheme(context) }
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())

        prefs.edit { remove(context.getString(R.string.display_options_theme_key)) }
        InCall.onMain { DisplayOptions.applyTheme(context) }
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun callScreenStaysInPortraitOnlyWhenAsked() {
        prefs.edit { putBoolean(context.getString(R.string.display_options_force_portrait_key), true) }
        connectedCall()
        CallTests.waitFor { if (requestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) true else null }
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.hangUpFromFarEnd()
        InCall.waitForNoCalls()

        prefs.edit { putBoolean(context.getString(R.string.display_options_force_portrait_key), false) }
        connectedCall()
        CallTests.waitFor { if (requestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) true else null }
    }

    @Test
    fun singleTapGestureShowsAnswerButtons() {
        prefs.edit { putString(IncomingCallSettings.KEY_ANSWER_GESTURE, IncomingCallSettings.GESTURE_TAP) }
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        CallTests.waitForRinging()
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }

        assertNotNull(
            "answer button not shown",
            deviceRule.device.wait(Until.findObject(By.res(context.packageName, TestTags.ANSWER_ACCEPT)), CallTests.TIMEOUT_MILLIS),
        )
    }

    private fun connectedCall() {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.answerFromFarEnd()
        InCall.waitForState(DialerCallState.ACTIVE)
    }

    private fun requestedOrientation(): Int? = InCall.onMain { InCallPresenter.getInstance().activity?.requestedOrientation }
}
