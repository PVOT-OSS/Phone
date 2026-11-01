/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.hold

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureRoboImage
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The banner for the call on hold. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class OnHoldBannerTest {

    @get:Rule val compose = createComposeRule()

    private val info = SecondaryInfo.builder().setShouldShow(true).setName("Ada Lovelace").build()

    @Test
    fun showsWhoIsOnHold() {
        compose.setContent { PhoneScreenTheme(fillBackground = false) { OnHoldBanner(info) } }
        compose.onNodeWithText("Ada Lovelace").assertExists()
        compose.onNodeWithText("On hold").assertExists()
    }

    @Test
    fun showsWhoIsOnHoldOverGlass() {
        compose.setContent {
            PhoneScreenTheme(fillBackground = false) {
                CompositionLocalProvider(
                    LocalPvotGlassBackdrop provides rememberPvotGlassBackdrop()
                ) {
                    OnHoldBanner(info)
                }
            }
        }
        compose.onNodeWithText("Ada Lovelace").assertExists()
        compose.onNodeWithText("On hold").assertExists()
    }

    @Test
    fun screenshotDark() {
        compose.setContent { PhoneScreenTheme(fillBackground = false) { OnHoldBanner(info) } }
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/on_hold_banner_dark.png")
    }
}
