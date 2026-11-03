/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import android.view.MotionEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
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
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
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
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.prauga.phone.incallui.R

/** Which side action a chip above the Decline and Answer buttons is. */
enum class AnswerSideSlot {
    Message,
    AnswerAsAudio,
    AnswerAndRelease,
}

/**
 * A side action of an incoming call: decline with a message, answer as audio, or answer and end the
 * other call. It sits beside the swipe, or above the pickup pills.
 */
data class AnswerSideAction(
    @DrawableRes val icon: Int,
    /** The text of its chip above the Decline and Answer buttons. */
    @StringRes val label: Int,
    /** What TalkBack reads for the button. */
    @StringRes val description: Int,
    /** What TalkBack says a double tap does. */
    @StringRes val accessibilityLabel: Int,
    /** What the line above the swipe says when the button is tapped instead of swiped. */
    @StringRes val hint: Int,
    val slot: AnswerSideSlot,
)

/** What the answer controls show; the answer fragment sets it. */
class AnswerUiState {
    /**
     * The Decline and Answer buttons instead of the swipe: single tap, TalkBack and split screen.
     */
    var twoButtons by mutableStateOf(false)
    @get:DrawableRes var answerIcon by mutableIntStateOf(R.drawable.ic_call_filled)

    /** The line above the answer control, or null for "Swipe up to answer". */
    var hint by mutableStateOf<CharSequence?>(null)
    /** What answering will end, said to the user; null when it ends nothing. */
    var incomingWillDisconnectText by mutableStateOf<CharSequence?>(null)

    var secondary by mutableStateOf<AnswerSideAction?>(null)
    var secondaryVisible by mutableStateOf(false)
    var answerAndRelease by mutableStateOf<AnswerSideAction?>(null)
    var answerAndReleaseVisible by mutableStateOf(false)

    /**
     * False once the call is no longer ringing, such as when the caller gave up, so nothing here
     * can answer or decline what is already gone.
     */
    var enabled by mutableStateOf(true)
}

/**
 * What the answer controls do; the answer fragment implements it. Answering and declining return
 * whether they settled the call, so the pills can take further taps again when one did not.
 */
interface AnswerActions {
    fun onAnswer(): Boolean

    fun onReject(): Boolean

    /** How far the answer control is pulled: up to 1 toward answer, down to -1 toward decline. */
    fun onProgress(progress: Float)

    /** Decline with a message, or answer a video call as audio. */
    fun onSecondary(): Boolean

    fun onAnswerAndRelease(): Boolean

    /** A side button was tapped rather than swiped; its hint shows for a moment. */
    fun onSideHint(action: AnswerSideAction)

    /** Every touch on the answer control, for telling a hand from a pocket. */
    fun onAnswerTouch(event: MotionEvent) = Unit
}

/**
 * The bottom of the incoming call screen: the side buttons and the swipe, or with
 * [AnswerUiState.twoButtons] the Decline and Answer buttons under a chip for each side action.
 */
@Composable
fun AnswerControls(state: AnswerUiState, actions: AnswerActions, modifier: Modifier = Modifier) {
    if (state.twoButtons) {
        PickupControls(state, actions, modifier)
        return
    }
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L),
            horizontalArrangement = Arrangement.SpaceBetween,
            // The side buttons line up with the answer button between its two hints.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(
                state.secondary.takeIf { state.secondaryVisible },
                { if (state.enabled) actions.onSecondary() },
                actions,
                TestTags.ANSWER_SECONDARY,
            )
            SwipeToAnswer(state, actions, Modifier.weight(1f))
            SideButton(
                state.answerAndRelease.takeIf { state.answerAndReleaseVisible },
                { if (state.enabled) actions.onAnswerAndRelease() },
                actions,
                TestTags.ANSWER_AND_RELEASE,
            )
        }
    }
}

/**
 * Decline on the left and Answer on the right, two large round buttons with their names under them,
 * and above them a chip for each extra the call offers. A scrim darkens the wallpaper behind it
 * all. Once a tap settles the call, or the call is gone, the controls ignore further taps,
 * including TalkBack's.
 */
@Composable
private fun PickupControls(state: AnswerUiState, actions: AnswerActions, modifier: Modifier) {
    var done by remember { mutableStateOf(false) }
    val live = state.enabled && !done
    fun settle(action: () -> Boolean) {
        if (!live) return
        done = true
        // Taps work again when the action did not take the call, such as a reply sheet dismissed.
        if (!action()) done = false
    }
    val sideActions =
        listOfNotNull(
            state.secondary.takeIf { state.secondaryVisible },
            state.answerAndRelease.takeIf { state.answerAndReleaseVisible },
        )
    val scrim = MaterialTheme.colorScheme.scrim
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    ScrimTop to scrim.copy(alpha = ScrimAlpha),
                    1f to scrim.copy(alpha = ScrimAlpha),
                )
            )
            .padding(top = ScrimFade)
            .padding(horizontal = PvotSpacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.L),
    ) {
        state.incomingWillDisconnectText?.let { text ->
            Text(
                text.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // The chips' row keeps its height when the call offers none, so the buttons stay put.
        Row(
            Modifier.height(ChipRowHeight),
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (action in sideActions) {
                SideChip(action, enabled = state.enabled) {
                    when (action.slot) {
                        AnswerSideSlot.AnswerAndRelease -> settle(actions::onAnswerAndRelease)
                        else -> settle(actions::onSecondary)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            PvotCallActionButton(
                action = PvotCallAction.Decline,
                icon = painterResource(R.drawable.ic_call_end),
                label = stringResource(R.string.call_incoming_decline),
                onClick = { settle(actions::onReject) },
                size = PickupSize,
                showLabel = true,
                enabled = state.enabled,
                modifier = Modifier.testTag(TestTags.ANSWER_DECLINE),
            )
            val scale = if (live) pulseScale() else 1f
            Box(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                PvotCallActionButton(
                    action = PvotCallAction.Answer,
                    icon = painterResource(state.answerIcon),
                    label = stringResource(R.string.call_incoming_answer),
                    onClick = { settle(actions::onAnswer) },
                    size = PickupSize,
                    showLabel = true,
                    enabled = state.enabled,
                    modifier = Modifier.testTag(TestTags.ANSWER_ACCEPT),
                )
            }
        }
    }
}

/** A chip for one extra the call offers, named by its label; TalkBack hears what it does. */
@Composable
private fun SideChip(action: AnswerSideAction, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(action.description)
    val accessibilityLabel = stringResource(action.accessibilityLabel)
    PvotButton(
        text = stringResource(action.label),
        icon = painterResource(action.icon),
        style = PvotButtonStyle.Tonal,
        enabled = enabled,
        onClick = onClick,
        modifier =
            Modifier.semantics {
                    contentDescription = description
                    onClick(accessibilityLabel) {
                        onClick()
                        true
                    }
                }
                .testTag(
                    if (action.slot == AnswerSideSlot.AnswerAndRelease) TestTags.ANSWER_AND_RELEASE
                    else TestTags.ANSWER_SECONDARY
                ),
    )
}

/** A gentle, endless breath for the Answer button; still while the user has animations off. */
@Composable
private fun pulseScale(): Float {
    val appearance by AppearanceSettings.rememberAppearance()
    if (appearance.animationsOff) return 1f
    val scale by
        rememberInfiniteTransition(label = "answerPulse")
            .animateFloat(
                initialValue = 1f,
                targetValue = PulseScale,
                animationSpec =
                    infiniteRepeatable(
                        tween(PulseMillis, easing = PvotMotion.Settle),
                        RepeatMode.Reverse,
                    ),
                label = "pulse",
            )
    return scale
}

/**
 * Drag the button up to answer or down to decline, or fling it once it has moved; it springs back
 * if let go early. It sits in a glass track with the two ends marked, the answer end above and the
 * decline end below, so both directions have the same room and can be seen. The button turns red,
 * with the hung-up handset, as it is pulled down.
 */
@Composable
private fun SwipeToAnswer(
    state: AnswerUiState,
    actions: AnswerActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val travel = with(density) { SwipeTravel.toPx() }
    val threshold = travel * ThresholdFraction
    val flingVelocity = with(density) { FlingVelocity.toPx() }
    val falsingThreshold = with(density) { FalsingThreshold.toPx() }
    // Whether this touch has moved far enough to count as a hand rather than a pocket.
    var aboveFalsingThreshold by remember { mutableStateOf(false) }
    val offset = remember { Animatable(0f) }
    // The drag's own total: offset only catches up when its snap runs, which can be after the next
    // delta.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // Which end the button is past, for a tick as it crosses: -1 answer, 1 decline, 0 neither.
    var armed by remember { mutableIntStateOf(0) }
    // A small nudge every few seconds says the button moves.
    val nudge by
        rememberInfiniteTransition(label = "answerNudge")
            .animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec =
                    infiniteRepeatable(
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
        if (!state.enabled) return
        done = true
        haptics.performHapticFeedback(
            if (answer) HapticFeedbackType.Confirm else HapticFeedbackType.Reject
        )
        if (answer) actions.onAnswer() else actions.onReject()
    }
    // How far toward decline, 0 to 1, and toward answer.
    val toDecline = (offset.value / threshold).coerceIn(0f, 1f)
    val toAnswer = (-offset.value / threshold).coerceIn(0f, 1f)
    val hintAlpha = 1f - (abs(offset.value) / threshold).coerceIn(0f, 1f)
    val puckColor = lerp(PvotCallAnswer, PvotCallEnd, toDecline)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text =
                (state.hint ?: stringResource(R.string.call_incoming_swipe_to_answer)).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(hintAlpha).padding(bottom = PvotSpacing.M),
        )
        Box(
            Modifier.width(TrackWidth)
                .height(PuckSize + SwipeTravel * 2)
                .pvotGlassSurface(LocalPvotGlassBackdrop.current, TrackWidth / 2, role = null)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var downY = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.motionEvent?.let(actions::onAnswerTouch)
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.changedToDown()) {
                                downY = change.position.y
                                aboveFalsingThreshold = false
                            } else if (abs(change.position.y - downY) >= falsingThreshold) {
                                aboveFalsingThreshold = true
                            }
                        }
                    }
                }
                // On the track, which stays put, so the button follows the finger one to one.
                .draggable(
                    // Nothing to answer or decline once the call is gone.
                    enabled = state.enabled,
                    state =
                        rememberDraggableState { delta ->
                            if (done) return@rememberDraggableState
                            val value = (dragOffset + delta).coerceIn(-travel, travel)
                            dragOffset = value
                            scope.launch { offset.snapTo(value) }
                            actions.onProgress((-value / threshold).coerceIn(-1f, 1f))
                            val now =
                                when {
                                    value <= -threshold -> -1
                                    value >= threshold -> 1
                                    else -> 0
                                }
                            if (now != 0 && now != armed)
                                haptics.performHapticFeedback(
                                    HapticFeedbackType.GestureThresholdActivate
                                )
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
                            // Too short a touch to be a hand: back to the middle, as for a miss.
                            !aboveFalsingThreshold -> {
                                actions.onProgress(0f)
                                offset.animateTo(0f, PvotMotion.bounceSpring())
                            }
                            dragOffset <= -threshold -> finish(answer = true)
                            dragOffset >= threshold -> finish(answer = false)
                            velocity <= -flingVelocity && dragOffset <= -threshold / 3 ->
                                finish(answer = true)
                            velocity >= flingVelocity && dragOffset >= threshold / 3 ->
                                finish(answer = false)
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
            TrackEnd(
                R.drawable.ic_call_filled,
                PvotCallAnswer,
                toAnswer,
                Modifier.align(Alignment.TopCenter),
            )
            TrackEnd(
                R.drawable.ic_call_end,
                PvotCallEnd,
                toDecline,
                Modifier.align(Alignment.BottomCenter),
            )
            Box(
                modifier =
                    Modifier.offset {
                            IntOffset(
                                0,
                                (offset.value +
                                        if (dragging || done) 0f
                                        else with(density) { nudge.dp.toPx() })
                                    .roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            val grow = 1f + PuckGrowth * maxOf(toAnswer, toDecline)
                            scaleX = grow
                            scaleY = grow
                        }
                        .size(PuckSize)
                        .shadow(
                            PvotSpacing.S,
                            CircleShape,
                            ambientColor = puckColor,
                            spotColor = puckColor,
                        )
                        .background(puckColor, CircleShape)
                        .semantics {
                            role = Role.Button
                            contentDescription = answerLabel
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(answerLabel) {
                                        finish(answer = true)
                                        true
                                    },
                                    CustomAccessibilityAction(declineLabel) {
                                        finish(answer = false)
                                        true
                                    },
                                )
                        }
                        .testTag(TestTags.ANSWER_PUCK),
                contentAlignment = Alignment.Center,
            ) {
                // The answer icon turns into the hung-up handset on the way down.
                Crossfade(targetState = toDecline > 0.5f, label = "puckIcon") { declining ->
                    Icon(
                        painter =
                            painterResource(
                                if (declining) R.drawable.ic_call_end else state.answerIcon
                            ),
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
private fun TrackEnd(
    @DrawableRes icon: Int,
    color: Color,
    reach: Float,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color.copy(alpha = TrackEndAlpha + (1f - TrackEndAlpha) * reach),
        modifier =
            modifier
                .padding(vertical = (SwipeTravel - TrackEndSize) / 2)
                .size(TrackEndSize)
                .graphicsLayer {
                    val grow = 1f + TrackEndGrowth * reach
                    scaleX = grow
                    scaleY = grow
                },
    )
}

/**
 * A side button: swiping away from it does its action, so a pocket tap cannot; a tap shows its hint
 * instead. TalkBack users get a plain click.
 */
@Composable
private fun SideButton(
    action: AnswerSideAction?,
    onAction: () -> Unit,
    actions: AnswerActions,
    tag: String,
) {
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
        modifier =
            Modifier.offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
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
                    onClick(label) {
                        onAction()
                        true
                    }
                }
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(action.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val PuckSize = 80.dp
private val PuckIconSize = 34.dp
private val SideSize = 56.dp

/** The Decline and Answer buttons' diameter: big enough to hit with a thumb without looking. */
private val PickupSize = 80.dp

/** The room the chips take, kept whether or not the call offers any. */
private val ChipRowHeight = 48.dp

/** How far above the controls the scrim starts, where it fades up to nothing. */
private val ScrimFade = 48.dp

/** Where, down the controls, the scrim reaches its full depth, and how deep that is. */
private const val ScrimTop = 0.4f
private const val ScrimAlpha = 0.6f

/** How far the Answer button grows on each breath, and how long one takes. */
private const val PulseScale = 1.06f
private const val PulseMillis = 1200

/**
 * How far the button can go each way in its track; it does its action past [ThresholdFraction] of
 * it.
 */
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

/** How far a touch must move before it can answer or decline, so a pocket can't. */
private val FalsingThreshold = 40.dp

/** How fast a released button must be moving, per second, to count as a fling. */
private val FlingVelocity = 800.dp
private val SideSwipeDistance = 72.dp
