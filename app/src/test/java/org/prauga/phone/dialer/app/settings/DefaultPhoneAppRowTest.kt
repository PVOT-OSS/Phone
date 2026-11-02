/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner

/** The settings row that makes Phone the default phone app after "Not now" on the first-run prompt. */
@RunWith(RobolectricTestRunner::class)
class DefaultPhoneAppRowTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val row = TestTags.settingsRow(DialerSettingsActivity.KEY_DEFAULT_PHONE_APP)

    @Test
    fun theRowShowsAndAsksForTheRoleWhenTapped() {
        var asked = 0
        show(DefaultPhoneAppPrompt(denied = false) { asked++ })
        compose.onNodeWithText(context.getString(R.string.settings_default_phone_app_summary)).assertIsDisplayed()
        compose.onNodeWithTag(row).performClick()
        assertThat(asked).isEqualTo(1)
    }

    @Test
    fun afterARefusalTheRowPointsToDefaultApps() {
        show(DefaultPhoneAppPrompt(denied = true) {})
        compose.onNodeWithText(context.getString(R.string.settings_default_phone_app_denied)).assertIsDisplayed()
    }

    @Test
    fun noRowWhilePhoneIsTheDefault() {
        show(null)
        compose.onNodeWithTag(row).assertDoesNotExist()
    }

    private fun show(prompt: DefaultPhoneAppPrompt?) {
        val entries = DialerSettingsActivity.settingsEntries(context)
        compose.setContent { SettingsTheme { SettingsRootList(entries, { _, _ -> }, prompt) } }
    }
}
