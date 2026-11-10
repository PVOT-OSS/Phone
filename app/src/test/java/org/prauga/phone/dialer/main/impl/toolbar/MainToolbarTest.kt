/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.toolbar

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main toolbar's search bar, search field and options menu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class MainToolbarTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()
    private var voiceCallback: SearchBarListener.VoiceSearchResultCallback? = null
    private val listener =
        object : SearchBarListener {
            override fun onSearchBarClicked() {
                events += "bar"
            }

            override fun onSearchQueryUpdated(query: String) {
                events += "query $query"
            }

            override fun onSearchBackButtonClicked() {
                events += "back"
            }

            override fun onVoiceButtonClicked(
                callback: SearchBarListener.VoiceSearchResultCallback
            ) {
                events += "voice"
                voiceCallback = callback
            }

            override fun onMenuItemClicked(item: MainMenuItem) {
                events += "menu $item"
            }

            override fun onActivityPause() = Unit

            override fun onUserLeaveHint() = Unit

            override fun onCallPlacedFromSearch() = Unit

            override fun requestingPermission() = Unit
        }

    private lateinit var toolbar: MainToolbar

    private fun show() {
        compose.runOnUiThread {
            toolbar = MainToolbar(compose.activity).also { it.setSearchBarListener(listener) }
            // Laid out as the main screen lays it out: full width, its own height, on a dark
            // window.
            val window =
                FrameLayout(compose.activity).apply {
                    setBackgroundColor(compose.activity.getColor(android.R.color.background_dark))
                    addView(toolbar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                }
            compose.activity.setContentView(window)
        }
        compose.waitForIdle()
    }

    @Test
    fun theHeaderShowsTheSelectedTabsTitle() {
        show()
        compose.runOnUiThread { toolbar.setTitle(R.string.tab_title_call_history) }
        compose.onNodeWithText("Recent").assertExists()
    }

    @Test
    fun anOldFocusRequestIsNotReplayedWhenTheDialpadExpandsTheBar() {
        show()
        // Search opens and closes once, then the dialpad expands the bar without asking for focus.
        compose.runOnUiThread { toolbar.expand(false, Optional.empty(), true) }
        compose.waitForIdle()
        compose.runOnUiThread { toolbar.collapse(false) }
        compose.waitForIdle()
        compose.runOnUiThread { toolbar.expand(false, Optional.empty(), false) }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun theDialpadTabHidesTheBarWithoutMovingIt() {
        show()
        compose.runOnUiThread { toolbar.hideForDialpad() }
        assertThat(toolbar.isHiddenForDialpad()).isTrue()
        assertThat(toolbar.translationY).isEqualTo(0f)
        compose.runOnUiThread { toolbar.showAfterDialpad() }
        assertThat(toolbar.isHiddenForDialpad()).isFalse()
    }

    @Test
    fun typingSearches() {
        show()
        compose.runOnUiThread { toolbar.expand(false, Optional.empty(), false) }
        compose.onNode(hasSetTextAction()).performTextInput("ada")
        assertThat(events.last()).isEqualTo("query ada")
        assertThat(toolbar.getQuery()).isEqualTo("ada")
    }

    @Test
    fun expandingWithTextSearchesForIt() {
        show()
        compose.runOnUiThread { toolbar.expand(true, Optional.of("bob"), false) }
        assertThat(toolbar.isExpanded()).isTrue()
        assertThat(events).containsExactly("query bob")
    }

    @Test
    fun theDialpadsNumberMovesOverWithoutSearchingAgain() {
        show()
        compose.runOnUiThread {
            toolbar.expand(false, Optional.empty(), false)
            toolbar.transferQueryFromDialpad("555")
        }
        assertThat(toolbar.getQuery()).isEqualTo("555")
        assertThat(events).isEmpty()
    }

    @Test
    fun backCollapsesAndClears() {
        show()
        compose.runOnUiThread { toolbar.expand(false, Optional.of("ada"), false) }
        compose.onNodeWithTag(TestTags.SEARCH_BACK).performClick()
        assertThat(toolbar.isExpanded()).isFalse()
        assertThat(toolbar.getQuery()).isEmpty()
        assertThat(events).containsExactly("query ada", "back", "query ").inOrder()
    }

    @Test
    fun clearEmptiesTheField() {
        show()
        compose.runOnUiThread { toolbar.expand(false, Optional.of("ada"), false) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertThat(toolbar.getQuery()).isEmpty()
    }

    @Test
    fun aVoiceResultFillsTheSearch() {
        show()
        compose.runOnUiThread { toolbar.expand(false, Optional.empty(), false) }
        compose.onNodeWithTag(TestTags.SEARCH_VOICE).performClick()
        compose.runOnUiThread { voiceCallback!!.setResult("carol") }
        assertThat(toolbar.isExpanded()).isTrue()
        assertThat(events).containsExactly("voice", "query carol").inOrder()
    }

    @Test
    fun theAvatarOpensTheMenuWhichOffersClearFrequentsOnlyWithFrequents() {
        show()
        compose.onNodeWithTag(TestTags.APP_MENU).performClick()
        compose.onNodeWithText("Clear frequents").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()

        compose.runOnUiThread { toolbar.showClearFrequents(true) }
        compose.onNodeWithTag(TestTags.APP_MENU).performClick()
        compose.onNodeWithText("Clear frequents").performClick()
        assertThat(events).containsExactly("menu SETTINGS", "menu CLEAR_FREQUENTS").inOrder()
    }

    @Test
    fun screenshotCollapsedDark() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/toolbar/tab_header_dark.png")
    }

    @Test
    fun screenshotExpandedDark() {
        show()
        compose.runOnUiThread { toolbar.expand(false, Optional.of("Ada"), false) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/toolbar/search_field_dark.png")
    }
}
