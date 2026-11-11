/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main screen's rail on a wide window: tabs, badges and the selected tab's action. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w891dp-h411dp-night-xxhdpi")
class MainNavRailTest {

    @get:Rule val compose = createComposeRule()

    private val navBar = MainNavBarState().apply { selectTab(TabIndex.DIALPAD) }
    private val actions = mutableListOf<String>()
    private val navBarActions =
        object : MainNavBarActions {
            override fun onDial() {
                actions += "dial"
            }

            override fun onSearch() {
                actions += "search"
            }

            override fun onAddContact() {
                actions += "add"
            }
        }

    private fun setRail() {
        compose.setContent {
            MainChromeTheme {
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MainNavRail(navBar, navBarActions)
                }
            }
        }
    }

    @Test
    fun tappingATabSelectsIt() {
        setRail()
        compose.onNodeWithText("Contacts").performClick()
        assertThat(navBar.selectedTab).isEqualTo(TabIndex.CONTACTS)
    }

    @Test
    fun theButtonAtTheTopDoesTheSelectedTabsAction() {
        setRail()
        compose.onNodeWithContentDescription("Dial").performClick()
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        assertThat(actions).containsExactly("dial", "search").inOrder()
    }

    @Test
    fun screenshotDark() {
        setRail()
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/main/nav_rail_dark.png")
    }
}
