/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main screen's dock: tabs, badges and the selected tab's action. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class MainDockTest {

    @get:Rule val compose = createComposeRule()

    private val navBar = MainNavBarState().apply { selectTab(TabIndex.DIALPAD) }
    private val actions = mutableListOf<String>()
    private val dockActions =
        object : MainDockActions {
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

    private fun setChrome() {
        compose.setContent {
            PhoneTheme {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    MainDock(navBar, dockActions)
                }
            }
        }
    }

    @Test
    fun tappingATabSelectsIt() {
        setChrome()

        compose.onNodeWithContentDescription("Contacts").performClick()

        assertThat(navBar.selectedTab).isEqualTo(TabIndex.CONTACTS)
    }

    @Test
    fun theSelectedTabsPillNamesItsAction() {
        setChrome()
        compose.onNodeWithText("Dial").assertExists()

        compose.onNodeWithContentDescription("Recents").performClick()

        compose.onNodeWithText("Search").assertExists()
        compose.onNodeWithText("Dial").assertDoesNotExist()
    }

    @Test
    fun tappingThePillRunsTheTabsAction() {
        navBar.showVoicemail(true)
        setChrome()

        compose.onNodeWithContentDescription("Dialpad").performClick()
        compose.onNodeWithContentDescription("Recents").performClick()
        compose.onNodeWithContentDescription("Recents").performClick()
        compose.onNodeWithContentDescription("Contacts").performClick()
        compose.onNodeWithContentDescription("Contacts").performClick()
        compose.onNodeWithContentDescription("Voicemail").performClick()
        compose.onNodeWithContentDescription("Voicemail").performClick()

        assertThat(actions).containsExactly("dial", "search", "add").inOrder()
    }

    @Test
    fun missedCallsShowABadge() {
        navBar.setNotificationCount(TabIndex.CALL_LOG, 3)
        setChrome()

        compose.onNodeWithText("3").assertExists()
    }

    @Test
    fun voicemailTabAppearsWhenShown() {
        setChrome()
        compose.onNodeWithContentDescription("Voicemail").assertDoesNotExist()

        navBar.showVoicemail(true)

        compose.onNodeWithContentDescription("Voicemail").performClick()
        assertThat(navBar.selectedTab).isEqualTo(TabIndex.VOICEMAIL)
    }

    @Test
    fun dockHidesWhileSearching() {
        setChrome()
        compose.onNodeWithTag(TestTags.MAIN_NAV_BAR).assertExists()

        navBar.isVisible = false

        compose.onNodeWithTag(TestTags.MAIN_NAV_BAR).assertDoesNotExist()
    }

    @Test
    fun screenshotDark() {
        navBar.showVoicemail(true)
        navBar.setNotificationCount(TabIndex.CALL_LOG, 3)
        navBar.setNotificationCount(TabIndex.VOICEMAIL, 120)
        setChrome()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/main/dock_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        navBar.setNotificationCount(TabIndex.CALL_LOG, 3)
        setChrome()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/main/dock_light.png")
    }
}
