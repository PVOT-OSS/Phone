/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Sound settings write where the rest of the app reads: system settings for ringing and dialpad
 * tones, and the device-protected preferences the in-call process reads for in-call alerts.
 */
@RunWith(RobolectricTestRunner::class)
class SoundSettingsTest {

    @get:Rule val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var model: SoundSettingsModel

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_SETTINGS)
        model = SoundSettingsModel(context)
        model.devicePrefs.edit().clear().commit()
    }

    @Test
    fun phoneMayWriteSystemSettingsInTheseTests() {
        assertThat(Settings.System.canWrite(context)).isTrue()
    }

    @Test
    fun dialpadTonesDefaultOnAndWriteTheSystemSetting() {
        assertThat(model.dialpadTones).isTrue()
        model.changeDialpadTones(false)
        assertThat(
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.DTMF_TONE_WHEN_DIALING,
                )
            )
            .isEqualTo(0)
        assertThat(model.dialpadTones).isFalse()
    }

    @Test
    fun dtmfToneLengthStoresTheIndex() {
        model.changeDtmfToneLength(1)
        assertThat(
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.DTMF_TONE_TYPE_WHEN_DIALING,
                )
            )
            .isEqualTo(1)
    }

    @Test
    fun refreshPicksUpOutsideChanges() {
        Settings.System.putInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 0)
        model.refresh()
        assertThat(model.dialpadTones).isFalse()
    }

    @Test
    fun inCallAlertsAreStoredForTheInCallProcess() {
        compose.setContent { SettingsTheme { SoundSettingsList(model) } }
        val dnd = TestTags.settingsRow(SoundSettingsModel.KEY_ENABLE_DND)
        compose.onNodeWithTag(TestTags.SETTINGS_LIST).performScrollToNode(hasTestTag(dnd))
        compose.onNodeWithTag(dnd).assertIsOff()
        if (model.hasVibrator) {
            val hangup = TestTags.settingsRow("incall_vibrate_hangup")
            compose.onNodeWithTag(TestTags.SETTINGS_LIST).performScrollToNode(hasTestTag(hangup))
            compose.onNodeWithTag(hangup).performClick().assertIsOn()
            // InCallVibrationHandler reads this file and key.
            val prefs =
                context
                    .createDeviceProtectedStorageContext()
                    .getSharedPreferences(context.packageName + "_preferences", 0)
            assertThat(prefs.getBoolean("incall_vibrate_hangup", false)).isTrue()
        }
    }
}
