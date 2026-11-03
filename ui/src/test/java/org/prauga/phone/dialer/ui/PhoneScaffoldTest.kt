/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.LocalPvotGlassReduced
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The frame every screen sits in. Robolectric cannot run the glass shaders, so the glass is solid
 * here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScaffoldTest {

    @get:Rule val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            PhoneScreenTheme {
                CompositionLocalProvider(
                    LocalPvotGlassReduced provides false,
                    LocalPvotGlassIntensity provides PvotGlassDefaults.SolidIntensity,
                ) {
                    content()
                }
            }
        }
    }

    @Test
    fun theContentIsPaddedBelowTheTopBar() {
        var padding: PaddingValues? = null
        show {
            PhoneScaffold(topBar = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("bar")) }) {
                padding = it
            }
        }
        compose.waitForIdle()
        assertThat(padding!!.calculateTopPadding()).isEqualTo(64.dp)
    }

    @Test
    fun theBarsGetTheBackdropAndTheContentDoesNot() {
        var barBackdrop: PvotGlassBackdrop? = null
        var contentBackdrop: PvotGlassBackdrop? = null
        var contentRead = false
        show {
            PhoneScaffold(navBar = { barBackdrop = LocalPvotGlassBackdrop.current }) {
                contentBackdrop = LocalPvotGlassBackdrop.current
                contentRead = true
            }
        }
        compose.waitForIdle()
        assertThat(barBackdrop).isNotNull()
        assertThat(contentRead).isTrue()
        assertThat(contentBackdrop).isNull()
    }

    @Test
    fun aPhotoBackgroundWithoutAPhotoDrawsTheScreen() {
        show {
            PhoneScaffold(background = PhoneBackground.Photo(blurred = null)) {
                Text("Hello", Modifier.testTag("content"))
            }
        }
        compose.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "+night") fun screenshotDark() = screenshot("scaffold_dark")

    @Test fun screenshotLight() = screenshot("scaffold_light")

    private fun screenshot(name: String) {
        show {
            PhoneScaffold(topBar = { PvotTopBar(title = "Recents", compact = true) }) { padding ->
                PvotScreen(
                    contentPadding =
                        PaddingValues(
                            top = padding.calculateTopPadding() + 16.dp,
                            start = 16.dp,
                            end = 16.dp,
                        )
                ) {
                    items(3) { Text("Row $it") }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/foundations/$name.png")
    }
}
