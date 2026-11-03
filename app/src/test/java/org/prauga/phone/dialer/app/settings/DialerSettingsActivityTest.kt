/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The settings activity hosts its screens in the glass scaffold: opening a screen puts it on the
 * back stack under its own title, back pops it, and a recreated activity keeps the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DialerSettingsActivityTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<DialerSettingsActivity>

    @Before
    fun launch() {
        // Robolectric cannot run the glass shaders, so the test draws the glass solid.
        AppearanceSettings.prefs(context).edit().clear().commit()
        AppearanceSettings.setGlass(context, false)
        scenario = ActivityScenario.launch(DialerSettingsActivity::class.java)
    }

    @After
    fun close() {
        scenario.close()
        AppearanceSettings.prefs(context).edit().clear().commit()
    }

    @Test
    fun opensOnTheSettingsList() {
        compose
            .onNodeWithTag(TestTags.settingsRow(DialerSettingsActivity.KEY_SCREENING))
            .assertIsDisplayed()
        assertTitle(context.getString(R.string.dialer_settings_label))
    }

    @Test
    fun aScreenOpensUnderItsTitleAndBackReturnsToTheList() {
        openAppearance()
        compose.onNodeWithTag(TestTags.APPEARANCE_GLASS_SWITCH).assertIsDisplayed()
        assertTitle(context.getString(R.string.appearance_settings_label))
        scenario.onActivity {
            assertThat(it.supportFragmentManager.backStackEntryCount).isEqualTo(1)
        }

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose
            .onNodeWithTag(TestTags.settingsRow(DialerSettingsActivity.KEY_SCREENING))
            .assertIsDisplayed()
        assertTitle(context.getString(R.string.dialer_settings_label))
        scenario.onActivity { assertThat(it.isFinishing).isFalse() }
    }

    @Test
    fun theTopBarBackPopsTheScreenFirst() {
        openAppearance()
        compose.onNode(hasTestTag(TestTags.SETTINGS_TOP_BAR)).assertIsDisplayed()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        scenario.onActivity { assertThat(it.isFinishing).isTrue() }
    }

    @Test
    fun aRecreatedActivityKeepsTheScreen() {
        openAppearance()
        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(TestTags.APPEARANCE_GLASS_SWITCH).assertIsDisplayed()
        scenario.onActivity {
            val shown = it.supportFragmentManager.findFragmentById(R.id.settings_content)
            assertThat(shown).isInstanceOf(AppearanceSettingsFragment::class.java)
            assertThat(shown!!.requireView().parent).isNotNull()
        }
    }

    private fun openAppearance() {
        compose
            .onNodeWithTag(TestTags.settingsRow(DialerSettingsActivity.KEY_APPEARANCE))
            .performClick()
        compose.waitForIdle()
    }

    private fun assertTitle(title: String) {
        compose
            .onNode(hasText(title).and(hasAnyAncestor(hasTestTag(TestTags.SETTINGS_TOP_BAR))))
            .assertExists()
    }
}
