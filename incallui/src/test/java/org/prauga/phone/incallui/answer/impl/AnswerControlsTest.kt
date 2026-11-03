/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The controls of the incoming call screen: Decline and Answer, and the chips above them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class AnswerControlsTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    /** Whether the fragment takes the next answer or decline; false stands for one that failed. */
    private var settles = true
    private val actions =
        object : AnswerActions {
            override fun onAnswer(): Boolean {
                events += "answer"
                return settles
            }

            override fun onReject(): Boolean {
                events += "reject"
                return settles
            }

            override fun onSecondary(): Boolean {
                events += "secondary"
                return false
            }

            override fun onAnswerAndRelease(): Boolean {
                events += "answer and release"
                return true
            }
        }

    private fun show(state: AnswerUiState) =
        compose.setContent {
            PhoneScreenTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    AnswerControls(state, actions)
                }
            }
        }

    private fun fullState() =
        AnswerUiState().apply {
            secondary = AnswerFragment.REJECT_WITH_SMS
            secondaryVisible = true
            answerAndRelease = AnswerFragment.ANSWER_AND_RELEASE
            answerAndReleaseVisible = true
        }

    @Test
    fun tappingAnswerAnswers() {
        show(fullState())
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("answer")
    }

    @Test
    fun tappingDeclineDeclines() {
        show(fullState())
        compose.onNodeWithTag(TestTags.ANSWER_DECLINE).performClick()
        assertThat(events).containsExactly("reject")
    }

    @Test
    fun onceSettledTheControlsIgnoreFurtherTaps() {
        show(fullState())
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_DECLINE).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_AND_RELEASE).performClick()
        compose
            .onNodeWithTag(TestTags.ANSWER_ACCEPT)
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly("answer")
    }

    @Test
    fun whenAnsweringFailsTheNextTapTriesAgain() {
        settles = false
        show(fullState())
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        settles = true
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("answer", "answer")
    }

    @Test
    fun tappingASideActionDoesIt() {
        show(fullState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_AND_RELEASE).performClick()
        assertThat(events).containsExactly("secondary", "answer and release").inOrder()
    }

    @Test
    fun theMessageSheetLeavesTheCallToBeAnswered() {
        show(fullState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("secondary", "answer").inOrder()
    }

    @Test
    fun talkBackReadsAnswerAndDeclineAsButtons() {
        show(fullState())
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        compose
            .onNodeWithTag(TestTags.ANSWER_ACCEPT)
            .assert(button)
            .assertContentDescriptionEquals("Answer")
        compose
            .onNodeWithTag(TestTags.ANSWER_DECLINE)
            .assert(button)
            .assertContentDescriptionEquals("Decline")
        compose
            .onNodeWithTag(TestTags.ANSWER_DECLINE)
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly("reject")
    }

    @Test
    fun theChipsSayWhatTheyDo() {
        show(fullState())
        compose.onNodeWithText("Message").assertExists()
        compose.onNodeWithText("Answer and end current").assertExists()
        compose
            .onNodeWithTag(TestTags.ANSWER_SECONDARY)
            .assertContentDescriptionEquals("Reject with message")
    }

    @Test
    fun aVideoCallOffersAnsweringAsAudio() {
        show(
            AnswerUiState().apply {
                secondary = AnswerFragment.ANSWER_VIDEO_AS_AUDIO
                secondaryVisible = true
            }
        )
        compose.onNodeWithText("Answer as audio").assertExists()
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        assertThat(events).containsExactly("secondary")
    }

    @Test
    fun hiddenChipsKeepTheirRoomSoTheButtonsStayPut() {
        val state = AnswerUiState()
        show(state)
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).assertDoesNotExist()
        val before = compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).getUnclippedBoundsInRoot()
        compose.runOnIdle {
            state.secondary = AnswerFragment.REJECT_WITH_SMS
            state.secondaryVisible = true
            state.answerAndRelease = AnswerFragment.ANSWER_AND_RELEASE
            state.answerAndReleaseVisible = true
        }
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).assertExists()
        assertThat(compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).getUnclippedBoundsInRoot())
            .isEqualTo(before)
    }

    @Test
    fun onceTheCallIsGoneTheControlsDoNothing() {
        val state = fullState()
        show(state)
        compose.runOnIdle { state.enabled = false }
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).assertIsNotEnabled()
        compose.onNodeWithTag(TestTags.ANSWER_DECLINE).assertIsNotEnabled()
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).assertIsNotEnabled()
        compose.onNodeWithTag(TestTags.ANSWER_AND_RELEASE).assertIsNotEnabled()
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_DECLINE).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        compose
            .onNodeWithTag(TestTags.ANSWER_AND_RELEASE)
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).isEmpty()
    }

    @Test
    fun screenshotDark() {
        show(
            fullState().apply {
                incomingWillDisconnectText = "Answering this call will end your other call"
            }
        )
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/answer_controls_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show(fullState())
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/answer_controls_light.png")
    }
}
