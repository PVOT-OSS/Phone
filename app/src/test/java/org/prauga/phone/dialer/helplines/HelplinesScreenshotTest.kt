/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.helplines

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.prauga.pvot.coreui.UiState
import org.prauga.phone.dialer.ui.SolidGlassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.lineageos.lib.phone.spn.Item
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot of the helplines list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class HelplinesScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun helplinesDark() = screenshot("helplines_dark")

    @Test
    @Config(qualifiers = "+notnight")
    fun helplinesLight() = screenshot("helplines_light")

    @Test
    fun detailsSheetDark() {
        screenshot(null)
        compose.onNodeWithText("Crisis Text Line").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/helplines/helpline_details_dark.png")
    }

    private fun screenshot(name: String?) {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val items = listOf(
            Item().apply { setName("Crisis Text Line"); setNumber("741741"); setCategories("emotional_support|suicide") },
            Item().apply { setName("Poison Control"); setNumber("18002221222"); setLanguages("english|french") },
            Item().apply { setName("Veterans Crisis Line"); setNumber("988"); setCategories("veterans") },
        )
        val helplines = HelplineLoader.load(resources, listOf("us" to "310")) { items }
        compose.setContent {
            SolidGlassTheme { HelplinesScreen(UiState.Success(helplines), showHelpAtStart = false, onBack = {}, onCall = {}) }
        }
        if (name != null) compose.onRoot().captureRoboImage("src/test/screenshots/helplines/$name.png")
    }
}
