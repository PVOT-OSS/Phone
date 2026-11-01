/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A button beside the answer control: decline with a message, answer as audio, or answer and end the other call. */
data class AnswerSideAction(
    @DrawableRes val icon: Int,
    @StringRes val description: Int,
    @StringRes val accessibilityLabel: Int,
    @StringRes val hint: Int,
)

/** What the answer controls show; the answer fragment sets it. */
class AnswerUiState {
    /** Two plain buttons instead of the swipe, for TalkBack and split screen. */
    var twoButtons by mutableStateOf(false)
    @get:DrawableRes
    var answerIcon by mutableIntStateOf(R.drawable.ic_phone)

    /** The line above the answer control, or null for "Swipe up to answer". */
    var hint by mutableStateOf<CharSequence?>(null)
    /** What answering will end, said to the user; null when it ends nothing. */
    var incomingWillDisconnectText by mutableStateOf<CharSequence?>(null)

    var secondary by mutableStateOf<AnswerSideAction?>(null)
    var secondaryVisible by mutableStateOf(false)
    var answerAndRelease by mutableStateOf<AnswerSideAction?>(null)
    var answerAndReleaseVisible by mutableStateOf(false)
}

/** What the answer controls do; the answer fragment implements it. */
interface AnswerActions {
    fun onAnswer()
    fun onReject()

    /** How far the answer control is pulled: up to 1 toward answer, down to -1 toward decline. */
    fun onProgress(progress: Float)

    fun onSecondary()
    fun onAnswerAndRelease()

    /** A side button was tapped rather than swiped; its hint shows for a moment. */
    fun onSideHint(action: AnswerSideAction)
}

/** The bottom of the incoming call screen: the side buttons and the answer control. */
@Composable
fun AnswerControls(state: AnswerUiState, actions: AnswerActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        state.incomingWillDisconnectText?.let { text ->
            Text(
                text.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S),
            )
        }
        if (state.twoButtons) {
            TwoButtons(state, actions)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                SideButton(state.secondary.takeIf { state.secondaryVisible }, actions::onSecondary, actions, TestTags.ANSWER_SECONDARY)
                SwipeToAnswer(state, actions, Modifier.weight(1f))
                SideButton(state.answerAndRelease.takeIf { state.answerAndReleaseVisible }, actions::onAnswerAndRelease, actions, TestTags.ANSWER_AND_RELEASE)
            }
        }
    }
}

/**
 * Drag the button up to answer or down to decline, or fling it once it has moved; it springs back
 * if let go early. Declining needs less travel: the button sits close to the bottom of the screen.
 */
@Composable
private fun SwipeToAnswer(state: AnswerUiState, actions: AnswerActions, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val threshold = with(density) { SwipeDistance.toPx() }
    val declineThreshold = with(density) { DeclineDistance.toPx() }
    val flingVelocity = with(density) { FlingVelocity.toPx() }
    val offset = remember { Animatable(0f) }
    // The drag's own total: offset only catches up when its snap runs, which can be after the next delta.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // A small nudge every few seconds says the button moves.
    val nudge by rememberInfiniteTransition(label = "answerNudge").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 2400
                0f at 0
                0f at 1600
                -14f at 1800
                0f at 2100
            },
            RepeatMode.Restart,
        ),
        label = "nudge",
    )
    val answerLabel = stringResource(R.string.call_incoming_answer)
    val declineLabel = stringResource(R.string.call_incoming_decline)
    fun finish(answer: Boolean) {
        done = true
        if (answer) actions.onAnswer() else actions.onReject()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (state.hint ?: stringResource(R.string.call_incoming_swipe_to_answer)).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(1f - (abs(offset.value) / threshold).coerceIn(0f, 1f)),
        )
        Box(
            modifier = Modifier
                .padding(vertical = PvotSpacing.L)
                .offset { IntOffset(0, (offset.value + if (dragging || done) 0f else with(density) { nudge.dp.toPx() }).roundToInt()) }
                .size(PuckSize)
                .background(if (offset.value > declineThreshold / 2) MaterialTheme.colorScheme.error else AnswerGreen, CircleShape)
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (done) return@rememberDraggableState
                        val value = (dragOffset + delta).coerceIn(-threshold * 1.2f, declineThreshold * 1.2f)
                        dragOffset = value
                        scope.launch { offset.snapTo(value) }
                        actions.onProgress((-value / if (value < 0) threshold else declineThreshold).coerceIn(-1f, 1f))
                    },
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragging = true
                        dragOffset = offset.value
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        when {
                            done -> Unit
                            dragOffset <= -threshold -> finish(answer = true)
                            dragOffset >= declineThreshold -> finish(answer = false)
                            velocity <= -flingVelocity && dragOffset <= -threshold / 3 -> finish(answer = true)
                            velocity >= flingVelocity && dragOffset >= declineThreshold / 3 -> finish(answer = false)
                            else -> {
                                actions.onProgress(0f)
                                offset.animateTo(0f, spring())
                            }
                        }
                    },
                )
                .semantics {
                    role = Role.Button
                    contentDescription = answerLabel
                    customActions = listOf(
                        CustomAccessibilityAction(answerLabel) { finish(answer = true); true },
                        CustomAccessibilityAction(declineLabel) { finish(answer = false); true },
                    )
                }
                .testTag(TestTags.ANSWER_PUCK),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(state.answerIcon), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text(
            text = stringResource(R.string.call_incoming_swipe_to_reject),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(1f - (abs(offset.value) / threshold).coerceIn(0f, 1f)),
        )
    }
}

/** Decline and answer as two buttons, for TalkBack and split screen. */
@Composable
private fun TwoButtons(state: AnswerUiState, actions: AnswerActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.L),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundAction(R.drawable.ic_phone_off, stringResource(R.string.call_incoming_decline), MaterialTheme.colorScheme.error, TestTags.ANSWER_DECLINE) {
            actions.onReject()
        }
        RoundAction(state.answerIcon, stringResource(R.string.call_incoming_answer), AnswerGreen, TestTags.ANSWER_ACCEPT) {
            actions.onAnswer()
        }
    }
}

@Composable
private fun RoundAction(@DrawableRes icon: Int, label: String, color: Color, tag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(PuckSize)
                .background(color, CircleShape)
                .pointerInput(onClick) { detectTapGestures { onClick() } }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    onClick { onClick(); true }
                }
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = PvotSpacing.S))
    }
}

/**
 * A side button: swiping away from it does its action, so a pocket tap cannot; a tap shows its
 * hint instead. TalkBack users get a plain click.
 */
@Composable
private fun SideButton(action: AnswerSideAction?, onAction: () -> Unit, actions: AnswerActions, tag: String) {
    if (action == null) {
        Box(Modifier.size(SideSize))
        return
    }
    val density = LocalDensity.current
    val distance = with(density) { SideSwipeDistance.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val label = stringResource(action.accessibilityLabel)
    val description = stringResource(action.description)
    Box(
        modifier = Modifier
            .padding(bottom = PvotSpacing.L)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .size(SideSize)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .pointerInput(action) {
                detectTapGestures { actions.onSideHint(action) }
            }
            .pointerInput(action) {
                detectDragGestures(
                    onDragEnd = {
                        if (hypot(drag.x, drag.y) >= distance) onAction()
                        drag = Offset.Zero
                    },
                    onDragCancel = { drag = Offset.Zero },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label) { onAction(); true }
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(action.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** The answer button keeps the green people expect of it, in light and dark. */
private val AnswerGreen = Color(0xFF1E8E3E)
private val PuckSize = 80.dp
private val SideSize = 56.dp
private val SwipeDistance = 120.dp
private val DeclineDistance = 56.dp

/** How fast a released button must be moving, per second, to count as a fling. */
private val FlingVelocity = 1200.dp
private val SideSwipeDistance = 72.dp

