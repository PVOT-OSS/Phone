/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.LocalPvotGlassReduced
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsScaffold
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

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun screeningLight() = captureScreening("screening_light")

    @Test @Config(qualifiers = "+night") fun screeningDark() = captureScreening("screening_dark")

    @Test
    @Config(fontScale = 1.3f)
    fun screeningLargeText() = captureScreening("screening_large_text")

    @Test
    @Config(qualifiers = "+night")
    fun soundDark() {
        val model = SoundSettingsModel(context)
        // The screen reads the ringtone's title off the main thread; read it first so the capture
        // is stable.
        kotlinx.coroutines.runBlocking { model.loadRingtoneName() }
        compose.setContent { SettingsTheme { SoundSettingsList(model) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/sound_dark.png")
    }

    @Test fun rootLight() = captureRoot("root_light")

    @Test @Config(qualifiers = "+night") fun rootDark() = captureRoot("root_dark")

    @Test
    @Config(qualifiers = "+night")
    fun rootNotDefaultDark() =
        captureRoot("root_not_default_dark", DefaultPhoneAppPrompt(denied = false) {})

    @Test fun displayLight() = captureDisplay("display_light")

    @Test @Config(qualifiers = "+night") fun displayDark() = captureDisplay("display_dark")

    @Test fun accountsLight() = captureAccounts("accounts_light")

    @Test @Config(qualifiers = "+night") fun accountsDark() = captureAccounts("accounts_dark")

    private fun captureScreening(name: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putString("screening_action_private_or_hidden", "decline").commit()
        compose.setContent { SettingsTheme { ScreeningSettingsList(ScreeningSettings(prefs)) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/$name.png")
    }

    private fun captureRoot(name: String, defaultApp: DefaultPhoneAppPrompt? = null) {
        val entries = DialerSettingsActivity.settingsEntries(context)
        captureScreen(name, context.getString(R.string.dialer_settings_label)) {
            SettingsRootList(entries, { _, _ -> }, defaultApp)
        }
    }

    private fun captureDisplay(name: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        captureScreen(name, context.getString(R.string.display_options_title)) {
            DisplayOptionsList(prefs)
        }
    }

    private fun captureAccounts(name: String) {
        val component =
            ComponentName(
                "com.android.phone",
                "com.android.services.telephony.TelephonyConnectionService",
            )
        val accounts =
            listOf(
                PhoneAccountSelectionFragment.SimAccount(
                    PhoneAccountHandle(component, "1"),
                    "SIM 1",
                    "Carrier One",
                ),
                PhoneAccountSelectionFragment.SimAccount(
                    PhoneAccountHandle(component, "2"),
                    "SIM 2",
                    "Carrier Two",
                ),
            )
        captureScreen(name, context.getString(R.string.voicemail_settings_label)) {
            PhoneAccountList(accounts) {}
        }
    }

    /**
     * The whole screen in its scaffold. Robolectric cannot run the glass shaders, so the glass is
     * solid.
     */
    private fun captureScreen(name: String, title: String, content: @Composable () -> Unit) {
        compose.setContent {
            SettingsTheme {
                CompositionLocalProvider(
                    LocalPvotGlassReduced provides false,
                    LocalPvotGlassIntensity provides PvotGlassDefaults.SolidIntensity,
                ) {
                    SettingsScaffold(title = title, onBack = {}) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/$name.png")
    }
}
