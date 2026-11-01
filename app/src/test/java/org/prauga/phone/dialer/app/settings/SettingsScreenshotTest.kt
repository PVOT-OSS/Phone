/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.screening.ScreeningSettings
import org.prauga.phone.dialer.screening.ScreeningSettingsList
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the Compose settings screens, in light, dark and with large text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun screeningLight() = captureScreening("screening_light")

    @Test
    @Config(qualifiers = "+night")
    fun screeningDark() = captureScreening("screening_dark")

    @Test
    @Config(fontScale = 1.3f)
    fun screeningLargeText() = captureScreening("screening_large_text")

    @Test
    @Config(qualifiers = "+night")
    fun soundDark() {
        val model = SoundSettingsModel(ApplicationProvider.getApplicationContext())
        // The screen reads the ringtone's title off the main thread; read it first so the capture is stable.
        kotlinx.coroutines.runBlocking { model.loadRingtoneName() }
        compose.setContent { SettingsTheme { SoundSettingsList(model) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/sound_dark.png")
    }

    private fun captureScreening(name: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        prefs.edit().clear().putString("screening_action_private_or_hidden", "decline").commit()
        compose.setContent { SettingsTheme { ScreeningSettingsList(ScreeningSettings(prefs)) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/$name.png")
    }
}
