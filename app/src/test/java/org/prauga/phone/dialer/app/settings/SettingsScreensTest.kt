/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.LocalPvotGlassReduced
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsChrome
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.app.settings.ui.SettingsScaffold
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.screening.CallerCategory
import org.prauga.phone.dialer.screening.CategoryAction
import org.prauga.phone.dialer.screening.ScreeningSettings
import org.prauga.phone.dialer.screening.ScreeningSettingsList
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner

/**
 * The Compose settings screens read and write the same preference keys and values as the
 * PreferenceFragmentCompat screens they replaced, so existing choices carry over.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: SharedPreferences

    @Before
    fun clearPrefs() {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
    }

    @Test
    fun screeningShowsRingUntilTheUserChooses() {
        showScreening()
        compose.onNodeWithText(context.getString(R.string.screening_category_first_time)).assertIsDisplayed()
        assertThat(prefs.getString("screening_action_first_time", null)).isNull()
        assertThat(ScreeningSettings(prefs).actionFor(CallerCategory.FIRST_TIME)).isEqualTo(CategoryAction.RING)
    }

    @Test
    fun choosingDeclineStoresTheOldListPreferenceValue() {
        showScreening()
        compose.onNodeWithTag(TestTags.settingsRow("screening_action_first_time")).performClick()
        compose.onNodeWithTag(TestTags.settingsOption(CategoryAction.DECLINE.toString())).performClick()
        compose.waitForIdle()
        // ListPreference stored CategoryAction names in lower case under screening_action_<category>.
        assertThat(prefs.getString("screening_action_first_time", null)).isEqualTo("decline")
        assertThat(prefs.getString("screening_action_private_or_hidden", null)).isNull()
    }

    @Test
    fun screeningShowsAnExistingChoice() {
        prefs.edit().putString("screening_action_possibly_faked", "silence").commit()
        showScreening()
        compose.onNodeWithTag(TestTags.settingsRow("screening_action_possibly_faked"), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.screening_action_silence)).assertIsDisplayed()
    }

    @Test
    fun displayOptionsStoreTheArrayValue() {
        val key = context.getString(R.string.display_options_sort_list_by_key)
        val values = context.resources.getStringArray(R.array.contact_sort_order_values)
        compose.setContent {
            SettingsTheme {
                StringChoiceRow(
                    prefs = prefs,
                    title = R.string.display_options_sort_list_by,
                    key = R.string.display_options_sort_list_by_key,
                    default = R.string.display_options_sort_by_given_name_value,
                    entries = R.array.contact_sort_order_entries,
                    values = R.array.contact_sort_order_values,
                )
            }
        }
        val other = values.first { it != context.getString(R.string.display_options_sort_by_given_name_value) }
        compose.onNodeWithTag(TestTags.settingsRow(key)).performClick()
        compose.onNodeWithTag(TestTags.settingsOption(other)).performClick()
        compose.waitForIdle()
        assertThat(prefs.getString(key, null)).isEqualTo(other)
    }

    @Test
    fun topLevelListHasTheCoreEntries() {
        val keys = DialerSettingsActivity.settingsEntries(context).map { it.key }
        assertThat(keys).containsAtLeast(
            DialerSettingsActivity.KEY_SOUNDS,
            DialerSettingsActivity.KEY_QUICK_RESPONSES,
            DialerSettingsActivity.KEY_SCREENING,
        ).inOrder()
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun theListStartsUnderTheTopBarAndFoldsItOnceScrolled() {
        val chrome = SettingsChrome()
        compose.setContent {
            SettingsTheme {
                // Robolectric cannot run the glass shaders, so the test draws the glass solid.
                CompositionLocalProvider(
                    LocalPvotGlassReduced provides false,
                    LocalPvotGlassIntensity provides PvotGlassDefaults.SolidIntensity,
                ) {
                    SettingsScaffold(title = "Settings", onBack = {}, chrome = chrome) {
                        SettingsList { items(40) { Text("Row $it") } }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertThat(chrome.compact).isFalse()
        assertThat(chrome.contentPadding.calculateTopPadding()).isGreaterThan(0.dp)
        compose.onNodeWithTag(TestTags.SETTINGS_LIST).performScrollToIndex(20)
        compose.waitForIdle()
        assertThat(chrome.compact).isTrue()
    }

    @Test
    fun cancellingAChoiceKeepsTheStoredValue() {
        showScreening()
        compose.onNodeWithTag(TestTags.settingsRow("screening_action_first_time")).performClick()
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()
        assertThat(prefs.getString("screening_action_first_time", null)).isNull()
    }

    private fun showScreening() {
        compose.setContent { SettingsTheme { ScreeningSettingsList(ScreeningSettings(prefs)) } }
    }
}
