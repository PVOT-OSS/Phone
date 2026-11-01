/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.helplines

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.prauga.pvot.coreui.UiState
import com.prauga.pvot.designsystem.theme.PvotAppTheme
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
    fun helplinesDark() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val items = listOf(
            Item().apply { setName("Crisis Text Line"); setNumber("741741"); setCategories("emotional_support|suicide") },
            Item().apply { setName("Poison Control"); setNumber("18002221222"); setLanguages("english|french") },
            Item().apply { setName("Veterans Crisis Line"); setNumber("988"); setCategories("veterans") },
        )
        val helplines = HelplineLoader.load(resources, listOf("us" to "310")) { items }
        compose.setContent {
            PvotAppTheme { HelplinesScreen(UiState.Success(helplines), showHelpAtStart = false, onBack = {}, onCall = {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/helplines/helplines_dark.png")
    }
}
