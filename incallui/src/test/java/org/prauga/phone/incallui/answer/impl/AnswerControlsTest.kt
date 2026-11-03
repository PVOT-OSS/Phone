/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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

/** The controls of the incoming call screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class AnswerControlsTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val progress = mutableListOf<Float>()

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

            override fun onProgress(progress: Float) {
                this@AnswerControlsTest.progress += progress
            }

            override fun onSecondary(): Boolean {
                events += "secondary"
                return false
            }

            override fun onAnswerAndRelease(): Boolean {
                events += "answer and release"
                return true
            }

            override fun onSideHint(action: AnswerSideAction) {
                events += "hint ${action.hint}"
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

    private fun swipeState() =
        AnswerUiState().apply {
            secondary = AnswerFragment.REJECT_WITH_SMS
            secondaryVisible = true
            answerAndRelease = AnswerFragment.ANSWER_AND_RELEASE
            answerAndReleaseVisible = true
        }

    @Test
    fun swipingUpAnswers() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput {
            swipe(center, center - Offset(0f, 900f), 300)
        }
        assertThat(events).containsExactly("answer")
        assertThat(progress.max()).isEqualTo(1f)
    }

    @Test
    fun swipingDownDeclines() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput {
            swipe(center, center + Offset(0f, 900f), 300)
        }
        assertThat(events).containsExactly("reject")
        assertThat(progress.min()).isEqualTo(-1f)
    }

    @Test
    fun aPullDownToTheDeclineEndDeclines() {
        // The track leaves the same room below the button as above it.
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput {
            swipe(center, center + Offset(0f, 90f * density), 400)
        }
        assertThat(events).containsExactly("reject")
    }

    @Test
    fun aShortSlowPullDownSpringsBack() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput {
            swipe(center, center + Offset(0f, 30f * density), 600)
        }
        assertThat(events).doesNotContain("reject")
    }

    @Test
    fun aQuickFlingUpAnswers() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput {
            swipe(center, center - Offset(0f, 60f * density), 60)
        }
        assertThat(events).containsExactly("answer")
    }

    @Test
    fun aShortPullSpringsBackWithoutAnswering() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput {
            swipe(center, center - Offset(0f, 120f), 200)
        }
        assertThat(events).isEmpty()
        assertThat(progress.last()).isEqualTo(0f)
    }

    @Test
    fun talkBackCanAnswerOrDeclineFromTheButton() {
        show(swipeState())
        val actions =
            compose
                .onNodeWithTag(TestTags.ANSWER_PUCK)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Answer", "Decline").inOrder()
        actions.first { it.label == "Decline" }.action()
        assertThat(events).containsExactly("reject")
    }

    @Test
    fun tappingASideButtonOnlyShowsItsHint() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performTouchInput {
            down(center)
            up()
        }
        assertThat(events).containsExactly("hint ${AnswerFragment.REJECT_WITH_SMS.hint}")
    }

    @Test
    fun swipingFromASideButtonDoesItsAction() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_AND_RELEASE).performTouchInput {
            swipe(center, center - Offset(400f, 400f), 300)
        }
        assertThat(events).containsExactly("answer and release")
    }

    @Test
    fun talkBackClicksASideButtonDirectly() {
        show(swipeState())
        compose
            .onNodeWithTag(TestTags.ANSWER_SECONDARY)
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly("secondary")
    }

    @Test
    fun theHintReplacesSwipeUpToAnswer() {
        val state = swipeState().apply { hint = "Swipe up to answer and hold ongoing call" }
        show(state)
        compose.onNodeWithText("Swipe up to answer and hold ongoing call").assertExists()
    }

    /** The pickup pills, as for single tap answering, TalkBack and split screen. */
    private fun tapState() = swipeState().apply { twoButtons = true }

    @Test
    fun tappingAnswerAnswers() {
        show(tapState())
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("answer")
    }

    @Test
    fun tappingDeclineDeclines() {
        show(tapState())
        compose.onNodeWithTag(TestTags.ANSWER_DECLINE).performClick()
        assertThat(events).containsExactly("reject")
    }

    @Test
    fun onceSettledThePillsIgnoreFurtherTaps() {
        show(tapState())
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
        show(tapState())
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        settles = true
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("answer", "answer")
    }

    @Test
    fun tappingASideActionAboveThePillsDoesIt() {
        show(tapState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_AND_RELEASE).performClick()
        assertThat(events).containsExactly("secondary", "answer and release").inOrder()
    }

    @Test
    fun theMessageSheetLeavesTheCallToBeAnswered() {
        show(tapState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("secondary", "answer").inOrder()
    }

    @Test
    fun talkBackReadsAnswerAndDeclineAsButtons() {
        show(tapState())
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
    fun theSideActionsHaveLabelsUnderThem() {
        show(tapState())
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
                twoButtons = true
                secondary = AnswerFragment.ANSWER_VIDEO_AS_AUDIO
                secondaryVisible = true
            }
        )
        compose.onNodeWithText("Answer as audio").assertExists()
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performClick()
        assertThat(events).containsExactly("secondary")
    }

    @Test
    fun hiddenSideActionsKeepTheirPlaceSoThePillsStayPut() {
        val state = AnswerUiState().apply { twoButtons = true }
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
    fun screenshotDark() {
        show(swipeState())
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/answer_controls_dark.png")
    }

    @Test
    fun screenshotPickupDark() {
        show(
            tapState().apply {
                incomingWillDisconnectText = "Answering this call will end your other call"
            }
        )
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/answer_pickup_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotPickupLight() {
        show(tapState())
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/answer_pickup_light.png")
    }
}
