/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner

/** The user build can't record calls, so sound settings don't offer a recording format. */
@RunWith(RobolectricTestRunner::class)
class NoRecordingSettingsTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun recordingFormatIsAbsent() {
        val context: Application = ApplicationProvider.getApplicationContext()
        val model = SoundSettingsModel(context)
        assertThat(model.showCallRecording).isFalse()
        compose.setContent { SettingsTheme { SoundSettingsList(model) } }
        val key = context.getString(R.string.call_recording_format_key)
        compose.onAllNodesWithTag(TestTags.settingsRow(key)).assertCountEquals(0)
    }
}
