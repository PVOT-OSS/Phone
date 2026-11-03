/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.theme.PvotDarkColorScheme
import com.prauga.pvot.designsystem.theme.PvotLightColorScheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.contacts.common.util.MaterialColorMapUtils
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.prauga.phone.incallui.ThemeColorManager
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phone's colors follow the Dynamic colors setting, in Compose and in what views still draw. */
@RunWith(RobolectricTestRunner::class)
class PhoneThemeTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        AppearanceSettings.prefs(context).edit().clear().commit()
    }

    @Test
    fun phonesOwnColorsUseTheSecondaryBlueAsTheAccent() {
        val scheme = showTheme()
        assertThat(scheme().primary).isEqualTo(PvotLightColorScheme.secondary)
        assertThat(scheme().background).isEqualTo(PvotLightColorScheme.background)
    }

    @Test
    fun turningDynamicColorsOnSwitchesTheOpenScreenToTheWallpapersColors() {
        val scheme = showTheme()
        AppearanceSettings.setDynamicColor(context, true)
        compose.waitForIdle()
        val dynamic = dynamicLightColorScheme(context)
        assertThat(scheme().primary).isEqualTo(dynamic.primary)
        assertThat(scheme().background).isEqualTo(dynamic.background)
        assertThat(scheme().surfaceContainer).isEqualTo(dynamic.surfaceContainer)

        AppearanceSettings.setDynamicColor(context, false)
        compose.waitForIdle()
        assertThat(scheme().primary).isEqualTo(PvotLightColorScheme.secondary)
    }

    @Test
    @Config(qualifiers = "night")
    fun theDarkModeHasItsOwnSchemeEitherWay() {
        assertThat(PhoneColors.scheme(context).background).isEqualTo(PvotDarkColorScheme.background)
        AppearanceSettings.setDynamicColor(context, true)
        assertThat(PhoneColors.scheme(context).background)
            .isEqualTo(dynamicDarkColorScheme(context).background)
    }

    @Test
    fun aFullScreenPaintsItsWindowInTheThemesBackground() {
        AppearanceSettings.setDynamicColor(context, true)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { PhoneScreenTheme {} }
        Robolectric.flushForegroundThreadScheduler()
        val window = activity.window.decorView.background as ColorDrawable
        assertThat(window.color).isEqualTo(dynamicLightColorScheme(context).background.toArgb())
    }

    @Test
    fun theCallScreenSitsOnTheThemesBackgroundWithDynamicColors() {
        AppearanceSettings.setDynamicColor(context, true)
        val colors = ThemeColorManager(MaterialColorMapUtils(context.resources))
        colors.onForegroundCallChanged(context, null)
        val background = dynamicLightColorScheme(context).background.toArgb()
        assertThat(colors.backgroundColorTop).isEqualTo(background)
        assertThat(colors.backgroundColorBottom).isEqualTo(background)
        assertThat(colors.backgroundColorSolid).isEqualTo(background)
    }

    /** Shows the theme and returns a reader for the scheme it provides. */
    private fun showTheme(): () -> ColorScheme {
        lateinit var scheme: ColorScheme
        compose.setContent { PhoneTheme { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        return { scheme }
    }
}
