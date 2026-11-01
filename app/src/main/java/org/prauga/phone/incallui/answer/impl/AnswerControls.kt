/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.prauga.pvot.designsystem.components.call.PvotCallAction
import com.prauga.pvot.designsystem.components.call.PvotCallActionButton
import com.prauga.pvot.designsystem.components.pvotGlassSurface
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotCallAnswer
import com.prauga.pvot.designsystem.theme.PvotCallEnd
import com.prauga.pvot.designsystem.theme.PvotOnCallAnswer
import com.prauga.pvot.designsystem.theme.PvotOnCallEnd
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags

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
    var answerIcon by mutableIntStateOf(R.drawable.ic_call_filled)

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
                // The side buttons line up with the answer button between its two hints.
                verticalAlignment = Alignment.CenterVertically,
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
 * if let go early. It sits in a glass track with the two ends marked, the answer end above and the
 * decline end below, so both directions have the same room and can be seen. The button turns red,
 * with the hung-up handset, as it is pulled down.
 */
@Composable
private fun SwipeToAnswer(state: AnswerUiState, actions: AnswerActions, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val travel = with(density) { SwipeTravel.toPx() }
    val threshold = travel * ThresholdFraction
    val flingVelocity = with(density) { FlingVelocity.toPx() }
    val offset = remember { Animatable(0f) }
    // The drag's own total: offset only catches up when its snap runs, which can be after the next delta.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // Which end the button is past, for a tick as it crosses: -1 answer, 1 decline, 0 neither.
    var armed by remember { mutableIntStateOf(0) }
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
        haptics.performHapticFeedback(if (answer) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        if (answer) actions.onAnswer() else actions.onReject()
    }
    // How far toward decline, 0 to 1, and toward answer.
    val toDecline = (offset.value / threshold).coerceIn(0f, 1f)
    val toAnswer = (-offset.value / threshold).coerceIn(0f, 1f)
    val hintAlpha = 1f - (abs(offset.value) / threshold).coerceIn(0f, 1f)
    val puckColor = lerp(PvotCallAnswer, PvotCallEnd, toDecline)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (state.hint ?: stringResource(R.string.call_incoming_swipe_to_answer)).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(hintAlpha).padding(bottom = PvotSpacing.M),
        )
        Box(
            Modifier
                .width(TrackWidth)
                .height(PuckSize + SwipeTravel * 2)
                .pvotGlassSurface(LocalPvotGlassBackdrop.current, TrackWidth / 2, role = null)
                // On the track, which stays put, so the button follows the finger one to one.
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (done) return@rememberDraggableState
                        val value = (dragOffset + delta).coerceIn(-travel, travel)
                        dragOffset = value
                        scope.launch { offset.snapTo(value) }
                        actions.onProgress((-value / threshold).coerceIn(-1f, 1f))
                        val now = when {
                            value <= -threshold -> -1
                            value >= threshold -> 1
                            else -> 0
                        }
                        if (now != 0 && now != armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        armed = now
                    },
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragging = true
                        dragOffset = offset.value
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        armed = 0
                        when {
                            done -> Unit
                            dragOffset <= -threshold -> finish(answer = true)
                            dragOffset >= threshold -> finish(answer = false)
                            velocity <= -flingVelocity && dragOffset <= -threshold / 3 -> finish(answer = true)
                            velocity >= flingVelocity && dragOffset >= threshold / 3 -> finish(answer = false)
                            else -> {
                                actions.onProgress(0f)
                                offset.animateTo(0f, PvotMotion.bounceSpring())
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The two ends: what reaching each one does.
            TrackEnd(R.drawable.ic_call_filled, PvotCallAnswer, toAnswer, Modifier.align(Alignment.TopCenter))
            TrackEnd(R.drawable.ic_call_end, PvotCallEnd, toDecline, Modifier.align(Alignment.BottomCenter))
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (offset.value + if (dragging || done) 0f else with(density) { nudge.dp.toPx() }).roundToInt()) }
                    .graphicsLayer {
                        val grow = 1f + PuckGrowth * maxOf(toAnswer, toDecline)
                        scaleX = grow
                        scaleY = grow
                    }
                    .size(PuckSize)
                    .shadow(PvotSpacing.S, CircleShape, ambientColor = puckColor, spotColor = puckColor)
                    .background(puckColor, CircleShape)
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
                // The answer icon turns into the hung-up handset on the way down.
                Crossfade(targetState = toDecline > 0.5f, label = "puckIcon") { declining ->
                    Icon(
                        painter = painterResource(if (declining) R.drawable.ic_call_end else state.answerIcon),
                        contentDescription = null,
                        tint = if (declining) PvotOnCallEnd else PvotOnCallAnswer,
                        modifier = Modifier.size(PuckIconSize),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.call_incoming_swipe_to_reject),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(hintAlpha).padding(top = PvotSpacing.M),
        )
    }
}

/** One end of the answer track: its icon, brighter as the button comes closer ([reach] 0 to 1). */
@Composable
private fun TrackEnd(@DrawableRes icon: Int, color: Color, reach: Float, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color.copy(alpha = TrackEndAlpha + (1f - TrackEndAlpha) * reach),
        modifier = modifier
            .padding(vertical = (SwipeTravel - TrackEndSize) / 2)
            .size(TrackEndSize)
            .graphicsLayer {
                val grow = 1f + TrackEndGrowth * reach
                scaleX = grow
                scaleY = grow
            },
    )
}

/** Decline and answer as two labelled buttons, for TalkBack, split screen and single tap answering. */
@Composable
private fun TwoButtons(state: AnswerUiState, actions: AnswerActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.L),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PvotCallActionButton(
            action = PvotCallAction.Decline,
            icon = painterResource(R.drawable.ic_call_end),
            label = stringResource(R.string.call_incoming_decline),
            onClick = actions::onReject,
            size = PuckSize,
            showLabel = true,
            modifier = Modifier.testTag(TestTags.ANSWER_DECLINE),
        )
        PvotCallActionButton(
            action = PvotCallAction.Answer,
            icon = painterResource(state.answerIcon),
            label = stringResource(R.string.call_incoming_answer),
            onClick = actions::onAnswer,
            size = PuckSize,
            showLabel = true,
            modifier = Modifier.testTag(TestTags.ANSWER_ACCEPT),
        )
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
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .size(SideSize)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .pointerInput(action) {
                detectTapGestures {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    actions.onSideHint(action)
                }
            }
            .pointerInput(action) {
                detectDragGestures(
                    onDragEnd = {
                        if (hypot(drag.x, drag.y) >= distance) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onAction()
                        }
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

private val PuckSize = 80.dp
private val PuckIconSize = 34.dp
private val SideSize = 56.dp

/** How far the button can go each way in its track; it does its action past [ThresholdFraction] of it. */
private val SwipeTravel = 96.dp
private const val ThresholdFraction = 0.75f

/** How much the button grows as it reaches an end. */
private const val PuckGrowth = 0.08f

/** The track is a little wider than the button. */
private val TrackWidth = PuckSize + PvotSpacing.M

/** The icons at the track's ends: faint until the button comes close. */
private val TrackEndSize = 28.dp
private const val TrackEndAlpha = 0.45f
private const val TrackEndGrowth = 0.2f

/** How fast a released button must be moving, per second, to count as a fling. */
private val FlingVelocity = 800.dp
private val SideSwipeDistance = 72.dp

