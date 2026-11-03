/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.call.PvotCallAction
import com.prauga.pvot.designsystem.components.call.PvotCallActionButton
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.prauga.phone.incallui.R

/** Which extra the pickup screen offers above the Decline and Answer buttons. */
enum class AnswerSideSlot {
    Message,
    AnswerAsAudio,
    AnswerAndRelease,
}

/**
 * A chip above the Decline and Answer buttons: decline with a message, answer as audio, or answer
 * and end the other call.
 */
data class AnswerSideAction(
    @DrawableRes val icon: Int,
    /** The chip's text. */
    @StringRes val label: Int,
    /** What TalkBack reads for the chip. */
    @StringRes val description: Int,
    /** What TalkBack says a double tap does. */
    @StringRes val accessibilityLabel: Int,
    val slot: AnswerSideSlot,
)

/** What the answer controls show; the answer fragment sets it. */
class AnswerUiState {
    @get:DrawableRes var answerIcon by mutableIntStateOf(R.drawable.ic_call_filled)

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
 * What the answer controls do; the answer fragment implements it. Each returns whether it settled
 * the call, so the controls can take further taps again when one did not.
 */
interface AnswerActions {
    fun onAnswer(): Boolean

    fun onReject(): Boolean

    /** Decline with a message, or answer a video call as audio. */
    fun onSecondary(): Boolean

    fun onAnswerAndRelease(): Boolean
}

/**
 * The bottom of the incoming call screen: Decline on the left and Answer on the right, two large
 * round buttons with their names under them, and above them a chip for each extra the call offers.
 * A scrim darkens the wallpaper behind it all. Once a tap settles the call, or the call is gone,
 * the controls ignore further taps, including TalkBack's.
 */
@Composable
fun AnswerControls(state: AnswerUiState, actions: AnswerActions, modifier: Modifier = Modifier) {
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
