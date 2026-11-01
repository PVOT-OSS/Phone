/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry.Channel
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The favorites tab. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class SpeedDialScreenTest {

    @get:Rule val compose = createComposeRule()

    private val mobile = channel("+15550101", "Mobile")
    private val work = channel("+15550102", "Work")
    private val ada =
        speedDialItem(1, "Ada Lovelace", channels = listOf(mobile, work), defaultChannel = null)
    private val bea = speedDialItem(2, "Bea Arthur")
    private val cy =
        speedDialItem(3, "Cy Twombly", channels = listOf(channel("+15550103", video = true)))
    private val dev = speedDialItem(4, "Dev Patel", starred = false)

    private var state by
        mutableStateOf<SpeedDialUiState>(SpeedDialUiState.Loaded(listOf(ada, bea, cy), listOf(dev)))

    private inner class RecordingActions : SpeedDialActions {
        val events = mutableListOf<String>()

        override fun call(channel: Channel, fromChooser: Boolean) {
            events += "call ${channel.number()}${if (fromChooser) " chosen" else ""}"
        }

        override fun message(number: String) {
            events += "message $number"
        }

        override fun openContact(item: SpeedDialUiItem) {
            events += "contact ${item.name()}"
        }

        override fun addFavorite() {
            events += "add"
        }

        override fun star(item: SpeedDialUiItem) {
            events += "star ${item.name()}"
        }

        override fun remove(item: SpeedDialUiItem) {
            events += "remove ${item.name()}"
            val loaded = state as SpeedDialUiState.Loaded
            state = loaded.copy(favorites = loaded.favorites - item)
        }

        override fun move(from: Int, to: Int) {
            events += "move $from $to"
            val loaded = state as SpeedDialUiState.Loaded
            state =
                loaded.copy(
                    favorites = loaded.favorites.toMutableList().apply { add(to, removeAt(from)) }
                )
        }

        override fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel) {
            events += "default ${item.name()} ${channel.number()}"
        }

        override fun turnOnPermission() {
            events += "permission"
        }

        override fun openAppMenu() {
            events += "menu"
        }
    }

    private val actions = RecordingActions()

    private fun show() = compose.setContent { PhoneScreenTheme { SpeedDialScreen(state, actions) } }

    @Test
    fun tappingAFavoriteWithOneNumberCallsIt() {
        show()
        compose.onNodeWithTag(TestTags.speedDialFavorite(2)).performClick()
        assertThat(actions.events).containsExactly("call +15550202")
    }

    @Test
    fun tappingAFavoriteWithSeveralNumbersAsksWhichAndCanRemember() {
        show()
        compose.onNodeWithTag(TestTags.speedDialFavorite(1)).performClick()
        compose.onNodeWithText("Choose call type").assertExists()

        compose.onNodeWithText("Remember this choice").performClick()
        compose.onNodeWithTag(TestTags.speedDialChannel("+15550102", false)).performClick()

        assertThat(actions.events)
            .containsExactly("default Ada Lovelace +15550102", "call +15550102 chosen")
            .inOrder()
        compose.onNodeWithText("Choose call type").assertDoesNotExist()
    }

    @Test
    fun longPressOffersRemove() {
        show()
        compose.onNodeWithTag(TestTags.speedDialFavorite(2)).performTouchInput { longClick() }
        compose.onNodeWithTag(TestTags.SPEED_DIAL_MENU_REMOVE).performClick()
        assertThat(actions.events).containsExactly("remove Bea Arthur")
    }

    private fun centerOf(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    /** Long-presses the favorite with [tag], then drags it by [delta] in small steps. */
    private fun dragFavorite(tag: String, delta: Offset, release: Boolean = true) {
        compose.onNodeWithTag(tag).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) { moveBy(delta / 20f) }
            if (release) up()
        }
    }

    @Test
    fun draggingAFavoriteOntoAnotherReorders() {
        show()
        dragFavorite(
            TestTags.speedDialFavorite(1),
            centerOf(TestTags.speedDialFavorite(3)) - centerOf(TestTags.speedDialFavorite(1)),
        )

        assertThat(actions.events.first()).startsWith("move 0")
        assertThat((state as SpeedDialUiState.Loaded).favorites.last().name())
            .isEqualTo("Ada Lovelace")
    }

    @Test
    fun draggingAFavoriteOntoRemoveRemovesIt() {
        show()
        val start = centerOf(TestTags.speedDialFavorite(2))
        dragFavorite(
            TestTags.speedDialFavorite(2),
            Offset(0f, 150f),
            release = false,
        ) // past the touch slop
        val target = centerOf(TestTags.SPEED_DIAL_REMOVE_TARGET)
        compose.onNodeWithTag(TestTags.speedDialFavorite(2)).performTouchInput {
            repeat(20) { moveBy((target - start - Offset(0f, 150f)) / 20f) }
            up()
        }
        assertThat(actions.events).contains("remove Bea Arthur")
    }

    @Test
    fun suggestionMenuAddsAFavorite() {
        show()
        compose.onNodeWithTag(TestTags.speedDialSuggestion(4)).performTouchInput { longClick() }
        compose.onNodeWithTag(TestTags.SPEED_DIAL_MENU_STAR).performClick()
        assertThat(actions.events).containsExactly("star Dev Patel")
    }

    @Test
    fun tappingASuggestionCallsIt() {
        show()
        compose.onNodeWithTag(TestTags.speedDialSuggestion(4)).performClick()
        assertThat(actions.events).containsExactly("call +15550204")
    }

    @Test
    fun addButtonPicksAContact() {
        show()
        compose.onNodeWithTag(TestTags.SPEED_DIAL_ADD).performClick()
        assertThat(actions.events).containsExactly("add")
    }

    @Test
    fun theAvatarOpensTheAppMenu() {
        show()
        compose.onNodeWithTag(TestTags.APP_MENU).performClick()
        assertThat(actions.events).containsExactly("menu")
    }

    @Test
    fun noFavoritesOffersToAddOne() {
        state = SpeedDialUiState.Empty
        show()
        compose.onNodeWithTag(TestTags.SPEED_DIAL_ADD).performClick()
        assertThat(actions.events).containsExactly("add")
    }

    @Test
    fun screenshotDark() {
        show()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/speeddial/speed_dial_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/speeddial/speed_dial_light.png")
    }
}
