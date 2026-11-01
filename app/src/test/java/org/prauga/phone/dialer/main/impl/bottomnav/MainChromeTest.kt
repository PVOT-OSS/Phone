/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main screen's nav bar and dialpad button. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class MainChromeTest {

    @get:Rule val compose = createComposeRule()

    private val navBar = MainNavBarState().apply { selectTab(TabIndex.SPEED_DIAL) }
    private val fab = MainFabState()

    private fun setChrome(onFabClick: () -> Unit = {}) {
        compose.setContent {
            PvotAppTheme {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        MainFab(fab, onFabClick)
                    }
                    MainNavBar(navBar)
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
    fun selectedTabShowsItsLabel() {
        navBar.selectTab(TabIndex.CALL_LOG)
        setChrome()

        compose.onNodeWithText("Recents").assertExists()
        compose.onNodeWithContentDescription("Recents").assertExists()
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
    fun barHidesWhileSearching() {
        setChrome()
        compose.onNodeWithTag(TestTags.MAIN_NAV_BAR).assertExists()

        navBar.isVisible = false

        compose.onNodeWithTag(TestTags.MAIN_NAV_BAR).assertDoesNotExist()
    }

    @Test
    fun fabOpensTheDialpadAndHides() {
        var clicks = 0
        setChrome(onFabClick = { clicks++ })

        compose.onNodeWithContentDescription("key pad").performClick()
        assertThat(clicks).isEqualTo(1)

        fab.hide()
        compose.onNodeWithTag(TestTags.MAIN_FAB).assertDoesNotExist()
    }

    @Test
    fun screenshotDark() {
        navBar.showVoicemail(true)
        navBar.setNotificationCount(TabIndex.CALL_LOG, 3)
        navBar.setNotificationCount(TabIndex.VOICEMAIL, 120)
        setChrome()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/main/chrome_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        navBar.setNotificationCount(TabIndex.CALL_LOG, 3)
        setChrome()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/main/chrome_light.png")
    }
}
