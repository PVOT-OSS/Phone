/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the two onboarding steps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class OnboardingScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun roleDark() = capture(OnboardingState(roleDenied = true), "role_dark")

    @Test
    @Config(qualifiers = "+notnight")
    fun roleLight() = capture(OnboardingState(roleDenied = true), "role_light")

    @Test
    fun permissionsDark() = capture(
        OnboardingState(
            isDefaultDialer = true,
            granted = setOf(PermissionGroup.PHONE),
            blocked = setOf(PermissionGroup.CONTACTS),
        ),
        "permissions_dark",
    )

    private fun capture(state: OnboardingState, name: String) {
        compose.setContent { PhoneScreenTheme { SolidGlass { OnboardingScreen(state, NoActions) } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/onboarding/$name.png")
    }

    private object NoActions : OnboardingActions {
        override fun onRequestRole() = Unit
        override fun onOpenDefaultApps() = Unit
        override fun onRequestPermissions() = Unit
        override fun onOpenAppSettings() = Unit
        override fun onNotNow() = Unit
    }
}
