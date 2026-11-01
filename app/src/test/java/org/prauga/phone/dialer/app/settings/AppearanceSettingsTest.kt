/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.LocalPvotGlassReduced
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsScaffold
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The appearance setting: what it stores, and what it asks PvotLib for. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppearanceSettingsTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        AppearanceSettings.prefs(context).edit().clear().commit()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }

    @Test
    fun glassIsOnAtTheDeviceDefaultToBeginWith() {
        val appearance = AppearanceSettings.current(context)
        assertThat(appearance.glass).isTrue()
        assertThat(appearance.intensity).isEqualTo(PvotGlassDefaults.defaultIntensity())
        assertThat(appearance.preferredIntensity).isEqualTo(PvotGlassDefaults.defaultIntensity())
    }

    @Test
    fun theChoiceIsStoredUnderItsKeys() {
        AppearanceSettings.setGlass(context, false)
        AppearanceSettings.setIntensity(context, 0.5f)
        val prefs = AppearanceSettings.prefs(context)
        assertThat(prefs.getBoolean("appearance_glass", true)).isFalse()
        assertThat(prefs.getFloat("appearance_glass_intensity", 0f)).isEqualTo(0.5f)
    }

    @Test
    fun glassOffAsksForSolid() {
        AppearanceSettings.setIntensity(context, 1f)
        AppearanceSettings.setGlass(context, false)
        assertThat(AppearanceSettings.current(context).preferredIntensity)
            .isEqualTo(PvotGlassDefaults.SolidIntensity)
    }

    @Test
    fun animationsOffAsksForSolid() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        assertThat(AppearanceSettings.current(context).animationsOff).isTrue()
        assertThat(AppearanceSettings.current(context).preferredIntensity)
            .isEqualTo(PvotGlassDefaults.SolidIntensity)
    }

    @Test
    fun theSwitchWritesThePreference() {
        show()
        compose.onNodeWithTag(TestTags.APPEARANCE_GLASS_SWITCH).assertIsOn().performClick()
        compose.waitForIdle()
        assertThat(AppearanceSettings.isGlass(context)).isFalse()
        compose.onNodeWithTag(TestTags.APPEARANCE_GLASS_SWITCH).assertIsOff()
    }

    @Test
    @Config(qualifiers = "+night")
    fun screenshotDark() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/appearance_dark.png")
    }

    @Test
    fun screenshotLight() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings/appearance_light.png")
    }

    /** Robolectric cannot run the glass shaders, so the screen is shown with the glass solid. */
    private fun show() {
        compose.setContent {
            SettingsTheme {
                CompositionLocalProvider(
                    LocalPvotGlassReduced provides false,
                    LocalPvotGlassIntensity provides PvotGlassDefaults.SolidIntensity,
                ) {
                    SettingsScaffold(
                        title = context.getString(R.string.appearance_settings_label),
                        onBack = {},
                    ) {
                        AppearanceSettingsList()
                    }
                }
            }
        }
    }
}
