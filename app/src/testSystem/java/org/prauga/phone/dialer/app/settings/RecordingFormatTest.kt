/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The system build offers the recording format, stored where CallRecorderService reads it. */
@RunWith(RobolectricTestRunner::class)
class RecordingFormatTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun choosingAFormatStoresItForTheRecorder() {
        val context: Application = ApplicationProvider.getApplicationContext()
        // The system build is granted the permission recording needs, so it offers the setting.
        shadowOf(context).grantPermissions(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
        val model = SoundSettingsModel(context)
        model.devicePrefs.edit().clear().commit()
        val key = context.getString(R.string.call_recording_format_key)
        val other = context.resources.getStringArray(R.array.call_recording_encoder_values).first { it != "0" }

        compose.setContent { SettingsTheme { SoundSettingsList(model) } }
        compose.onNodeWithTag(TestTags.SETTINGS_LIST).performScrollToNode(hasTestTag(TestTags.settingsRow(key)))
        compose.onNodeWithTag(TestTags.settingsRow(key)).performClick()
        compose.onNodeWithTag(TestTags.settingsOption(other)).performClick()
        compose.waitForIdle()

        // CallRecorderService reads <package>_preferences in device-protected storage.
        val prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(context.packageName + "_preferences", 0)
        assertThat(prefs.getString(key, null)).isEqualTo(other)
    }
}
