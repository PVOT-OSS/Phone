/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import android.content.Context
import android.view.View
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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main screen's nav bar: tabs, badges, the selected tab's action and the compact form. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class MainNavBarTest {

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

    private fun setChrome() {
        compose.setContent {
            MainChromeTheme {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    MainNavBar(navBar, navBarActions)
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

        compose.onNodeWithContentDescription("Recent").performClick()

        compose.onNodeWithText("Search").assertExists()
        compose.onNodeWithText("Dial").assertDoesNotExist()
    }

    @Test
    fun tappingThePillRunsTheTabsAction() {
        navBar.showVoicemail(true)
        setChrome()

        compose.onNodeWithContentDescription("Dial").performClick()
        compose.onNodeWithContentDescription("Recent").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Contacts").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithContentDescription("Voicemail").performClick()
        compose.onNodeWithContentDescription("Voicemail").performClick()

        assertThat(actions).containsExactly("dial", "search", "add").inOrder()
        assertThat(navBar.selectedTab).isEqualTo(TabIndex.VOICEMAIL)
    }

    @Test
    fun scrollingUpShrinksTheBarAndBackDownRestoresIt() {
        val behavior = CompactOnScrollBehavior(navBar)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = CoordinatorLayout(context)
        val child = View(context)

        behavior.onNestedPreScroll(parent, child, child, 0, 40, IntArray(2), ViewCompat.TYPE_TOUCH)
        assertThat(navBar.isCompact).isTrue()

        behavior.onNestedPreScroll(parent, child, child, 0, 2, IntArray(2), ViewCompat.TYPE_TOUCH)
        assertThat(navBar.isCompact).isTrue()

        behavior.onNestedPreScroll(parent, child, child, 0, -40, IntArray(2), ViewCompat.TYPE_TOUCH)
        assertThat(navBar.isCompact).isFalse()
    }

    @Test
    fun choosingATabRestoresTheCompactBar() {
        navBar.isCompact = true
        setChrome()

        compose.onNodeWithContentDescription("Contacts").performClick()

        assertThat(navBar.isCompact).isFalse()
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
    fun navBarHidesWhileSearching() {
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
            .captureRoboImage("src/test/screenshots/main/nav_bar_dark.png")
    }

    @Test
    fun screenshotCompact() {
        navBar.setNotificationCount(TabIndex.CALL_LOG, 3)
        navBar.isCompact = true
        setChrome()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/main/nav_bar_compact_dark.png")
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
            .captureRoboImage("src/test/screenshots/main/nav_bar_light.png")
    }
}
