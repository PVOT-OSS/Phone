/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
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
    private val actions = object : AnswerActions {
        override fun onAnswer() { events += "answer" }
        override fun onReject() { events += "reject" }
        override fun onProgress(progress: Float) { this@AnswerControlsTest.progress += progress }
        override fun onSecondary() { events += "secondary" }
        override fun onAnswerAndRelease() { events += "answer and release" }
        override fun onSideHint(action: AnswerSideAction) { events += "hint ${action.hint}" }
    }

    private fun show(state: AnswerUiState) = compose.setContent {
        PhoneScreenTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AnswerControls(state, actions) }
        }
    }

    private fun swipeState() = AnswerUiState().apply {
        secondary = AnswerFragment.REJECT_WITH_SMS
        secondaryVisible = true
        answerAndRelease = AnswerFragment.ANSWER_AND_RELEASE
        answerAndReleaseVisible = true
    }

    @Test
    fun swipingUpAnswers() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput { swipe(center, center - Offset(0f, 900f), 300) }
        assertThat(events).containsExactly("answer")
        assertThat(progress.max()).isEqualTo(1f)
    }

    @Test
    fun swipingDownDeclines() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput { swipe(center, center + Offset(0f, 900f), 300) }
        assertThat(events).containsExactly("reject")
        assertThat(progress.min()).isEqualTo(-1f)
    }

    @Test
    fun aShortPullSpringsBackWithoutAnswering() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_PUCK).performTouchInput { swipe(center, center - Offset(0f, 120f), 200) }
        assertThat(events).isEmpty()
        assertThat(progress.last()).isEqualTo(0f)
    }

    @Test
    fun talkBackCanAnswerOrDeclineFromTheButton() {
        show(swipeState())
        val actions = compose.onNodeWithTag(TestTags.ANSWER_PUCK).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Answer", "Decline").inOrder()
        actions.first { it.label == "Decline" }.action()
        assertThat(events).containsExactly("reject")
    }

    @Test
    fun tappingASideButtonOnlyShowsItsHint() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performTouchInput { down(center); up() }
        assertThat(events).containsExactly("hint ${AnswerFragment.REJECT_WITH_SMS.hint}")
    }

    @Test
    fun swipingFromASideButtonDoesItsAction() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_AND_RELEASE).performTouchInput { swipe(center, center - Offset(400f, 400f), 300) }
        assertThat(events).containsExactly("answer and release")
    }

    @Test
    fun talkBackClicksASideButtonDirectly() {
        show(swipeState())
        compose.onNodeWithTag(TestTags.ANSWER_SECONDARY).performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly("secondary")
    }

    @Test
    fun theHintReplacesSwipeUpToAnswer() {
        val state = swipeState().apply { hint = "Swipe up to answer and hold ongoing call" }
        show(state)
        compose.onNodeWithText("Swipe up to answer and hold ongoing call").assertExists()
    }

    @Test
    fun twoButtonsAnswerAndDecline() {
        show(AnswerUiState().apply { twoButtons = true })
        compose.onNodeWithTag(TestTags.ANSWER_DECLINE).performClick()
        compose.onNodeWithTag(TestTags.ANSWER_ACCEPT).performClick()
        assertThat(events).containsExactly("reject", "answer").inOrder()
    }

    @Test
    fun screenshotDark() {
        show(swipeState())
        compose.mainClock.autoAdvance = false
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/incall/answer_controls_dark.png")
    }

}
