/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.rtt.impl

import android.telecom.CallAudioState
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.rtt.RttTranscript
import org.prauga.phone.dialer.rtt.RttTranscriptMessage
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The RTT chat: how typing becomes bubbles, and what the screen's buttons do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class RttChatScreenTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions =
        object : RttChatActions {
            override fun onSubmit() {
                events += "submit"
            }

            override fun onEndCall() {
                events += "end"
            }

            override fun onMenuOpen() {
                events += "menu"
            }

            override fun onMute(muted: Boolean) {
                events += "mute $muted"
            }

            override fun onSpeaker() {
                events += "speaker"
            }

            override fun onDialpad(show: Boolean) {
                events += "dialpad $show"
            }

            override fun onAddCall() {
                events += "add"
            }

            override fun onSwap() {
                events += "swap"
            }

            override fun onSwitchToVoice() {
                events += "voice"
            }

            override fun onAudioRoute(route: Int) {
                events += "route $route"
            }

            override fun onScrollBack() = Unit

            override fun onEmergencyMessage(message: String) {
                events += "emergency $message"
            }
        }

    @Test
    fun typingGrowsOneBubbleUntilSubmitted() {
        val messages = RttChatMessages()
        messages.addLocalMessage("He")
        messages.addLocalMessage(messages.computeChangeOfLocalMessage("Hello"))
        assertThat(messages.bubbles).containsExactly(RttBubble("Hello", isRemote = false))

        messages.submitLocalMessage()
        messages.addLocalMessage(messages.computeChangeOfLocalMessage("Bye"))
        assertThat(messages.bubbles.map { it.content }).containsExactly("Hello", "Bye").inOrder()
    }

    @Test
    fun erasingEveryCharacterRemovesTheBubble() {
        val messages = RttChatMessages()
        messages.addLocalMessage("Hi")
        messages.addLocalMessage(messages.computeChangeOfLocalMessage(""))
        assertThat(messages.bubbles).isEmpty()
    }

    @Test
    fun theOtherSideIsMergedAndCanReopenOurLastMessage() {
        val messages = RttChatMessages()
        messages.addLocalMessage("Hi")
        messages.submitLocalMessage()
        messages.addRemoteMessage("Hey")
        messages.addRemoteMessage(" there")
        assertThat(messages.bubbles)
            .containsExactly(RttBubble("Hi", false), RttBubble("Hey there", true))
            .inOrder()
        assertThat(messages.retrieveLastLocalMessage()).isEqualTo("Hi")
    }

    @Test
    fun restoringGivesBackAnUnfinishedMessage() {
        val transcript =
            RttTranscript.newBuilder()
                .addMessages(
                    RttTranscriptMessage.newBuilder()
                        .setContent("Hello")
                        .setIsRemote(true)
                        .setIsFinished(true)
                )
                .addMessages(
                    RttTranscriptMessage.newBuilder()
                        .setContent("Typ")
                        .setIsRemote(false)
                        .setIsFinished(false)
                )
                .build()
        val messages = RttChatMessages()
        assertThat(messages.onRestoreRttChat(transcript)).isEqualTo("Typ")
        assertThat(messages.getRttTranscriptMessageList().map { it.content })
            .containsExactly("Hello", "Typ")
            .inOrder()
    }

    private fun show(state: RttChatUiState, messages: RttChatMessages = RttChatMessages()) {
        val input = RttChatHost.createInput(ApplicationProvider.getApplicationContext())
        compose.setContent { PhoneScreenTheme { RttChatScreen(state, messages, input, actions) } }
    }

    @Test
    fun theHeaderEndsTheCallAndOpensTheMenu() {
        show(RttChatUiState().apply { name = "Ada Lovelace" })
        compose.onNodeWithTag(TestTags.RTT_END_CALL).performClick()
        compose.onNodeWithTag(TestTags.RTT_MENU).performClick()
        assertThat(events).containsExactly("end", "menu").inOrder()
    }

    @Test
    fun anEmergencyCallOffersReadyMessages() {
        val state = RttChatUiState().apply { inputVisible = true }
        show(state)
        compose
            .onNodeWithTag(TestTags.rttEmergency(R.string.rtt_emergency_police))
            .assertDoesNotExist()
        state.emergency = true
        compose.onNodeWithTag(TestTags.rttEmergency(R.string.rtt_emergency_fire)).assertExists()
        compose.onNodeWithTag(TestTags.rttEmergency(R.string.rtt_emergency_medical)).assertExists()
        compose.onNodeWithTag(TestTags.rttEmergency(R.string.rtt_emergency_police)).performClick()
        assertThat(events).containsExactly("emergency I require police assistance.")
    }

    @Test
    fun theInputShowsOnceTheCallIsActive() {
        val state = RttChatUiState()
        show(state)
        compose.onNodeWithTag(TestTags.RTT_INPUT).assertDoesNotExist()
        state.inputVisible = true
        state.submitEnabled = true
        compose.onNodeWithTag(TestTags.RTT_SUBMIT).performClick()
        assertThat(events).containsExactly("submit")
    }

    @Test
    fun theMenuTogglesAndOffersSwapOnlyWhenPossible() {
        val state =
            RttChatUiState().apply {
                audioState =
                    CallAudioState(
                        false,
                        CallAudioState.ROUTE_EARPIECE,
                        CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
                    )
                menuOpen = true
            }
        show(state)
        compose.onNodeWithText("Mute").performClick()
        compose.onNodeWithText("Speaker").performClick()
        compose.onNodeWithText("Swap").assertDoesNotExist()
        state.swapEnabled = true
        compose.onNodeWithText("Swap").performClick()
        assertThat(events).containsExactly("mute true", "speaker", "swap").inOrder()
    }

    @Test
    fun theAudioMenuPicksARoute() {
        show(
            RttChatUiState().apply {
                audioState =
                    CallAudioState(
                        false,
                        CallAudioState.ROUTE_EARPIECE,
                        CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
                    )
                audioMenuOpen = true
            }
        )
        compose.onNodeWithText("Speaker").performClick()
        assertThat(events).containsExactly("route ${CallAudioState.ROUTE_SPEAKER}")
    }

    @Test
    fun screenshotDark() {
        val messages =
            RttChatMessages().apply {
                showAdvisory()
                addRemoteMessage("Hi, can you hear me?")
                addLocalMessage("Yes, typing because it's loud")
            }
        show(
            RttChatUiState().apply {
                name = "Ada Lovelace"
                inputVisible = true
                submitEnabled = false
            },
            messages,
        )
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/rtt/rtt_chat_dark.png")
    }
}
