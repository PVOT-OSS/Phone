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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.call.PvotCallAction
import com.prauga.pvot.designsystem.components.call.PvotCallActionButton
import com.prauga.pvot.designsystem.components.call.PvotCallActionButtonDefaults
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.prauga.phone.incallui.R

/** Where a side action sits in the row above the pickup pills; a hidden one leaves its place empty. */
enum class AnswerSideSlot { Message, AnswerAsAudio, AnswerAndRelease }

/** A button above the pickup pills: decline with a message, answer as audio, or answer and end the other call. */
data class AnswerSideAction(
    @DrawableRes val icon: Int,
    /** The short label under the button. */
    @StringRes val label: Int,
    /** What TalkBack reads for the button. */
    @StringRes val description: Int,
    /** What TalkBack says a double tap does. */
    @StringRes val accessibilityLabel: Int,
    val slot: AnswerSideSlot,
)

/** What the answer controls show; the answer fragment sets it. */
class AnswerUiState {
    @get:DrawableRes
    var answerIcon by mutableIntStateOf(R.drawable.ic_call_filled)

    /** What answering will end, said to the user; null when it ends nothing. */
    var incomingWillDisconnectText by mutableStateOf<CharSequence?>(null)

    var secondary by mutableStateOf<AnswerSideAction?>(null)
    var secondaryVisible by mutableStateOf(false)
    var answerAndRelease by mutableStateOf<AnswerSideAction?>(null)
    var answerAndReleaseVisible by mutableStateOf(false)
}

/**
 * What the answer controls do; the answer fragment implements it. Each returns whether it
 * settled the call, so the controls can take further taps again when one did not.
 */
interface AnswerActions {
    fun onAnswer(): Boolean
    fun onReject(): Boolean

    /** Decline with a message, or answer a video call as audio. */
    fun onSecondary(): Boolean
    fun onAnswerAndRelease(): Boolean
}

/**
 * The bottom of the incoming call screen: the side actions in a row, each in its own place, and
 * the Decline and Answer pills under them, side by side. Once a tap settles the call the controls
 * ignore further taps, including TalkBack's.
 */
@Composable
fun AnswerControls(state: AnswerUiState, actions: AnswerActions, modifier: Modifier = Modifier) {
    var done by remember { mutableStateOf(false) }
    fun settle(action: () -> Boolean) {
        if (done) return
        done = true
        // Taps work again when the action did not take the call, such as a reply sheet dismissed.
        if (!action()) done = false
    }
    val sideActions = listOfNotNull(
        state.secondary.takeIf { state.secondaryVisible },
        state.answerAndRelease.takeIf { state.answerAndReleaseVisible },
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L),
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (slot in AnswerSideSlot.entries) {
                val action = sideActions.firstOrNull { it.slot == slot }
                SidePlace(action) {
                    when (slot) {
                        AnswerSideSlot.AnswerAndRelease -> settle(actions::onAnswerAndRelease)
                        else -> settle(actions::onSecondary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M)) {
            PickupPill(
                action = PvotCallAction.Decline,
                icon = R.drawable.ic_call_end,
                label = stringResource(R.string.call_incoming_decline),
                onClick = { settle(actions::onReject) },
                modifier = Modifier.testTag(TestTags.ANSWER_DECLINE),
            )
            PickupPill(
                action = PvotCallAction.Answer,
                icon = state.answerIcon,
                label = stringResource(R.string.call_incoming_answer),
                onClick = { settle(actions::onAnswer) },
                pulse = !done,
                modifier = Modifier.testTag(TestTags.ANSWER_ACCEPT),
            )
        }
    }
}

/** A Decline or Answer pill, half the row wide; the Answer one breathes until a tap settles the call. */
@Composable
private fun RowScope.PickupPill(
    action: PvotCallAction,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val scale = if (pulse) pulseScale() else 1f
    BoxWithConstraints(Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }) {
        PvotCallActionButton(
            action = action,
            icon = painterResource(icon),
            label = label,
            onClick = onClick,
            width = maxWidth,
            labelInside = true,
            modifier = modifier,
        )
    }
}

/** A gentle, endless breath for the Answer pill; still while the user has animations off. */
@Composable
private fun pulseScale(): Float {
    val appearance by AppearanceSettings.rememberAppearance()
    if (appearance.animationsOff) return 1f
    val scale by rememberInfiniteTransition(label = "answerPulse").animateFloat(
        initialValue = 1f,
        targetValue = PulseScale,
        animationSpec = infiniteRepeatable(tween(PulseMillis, easing = PvotMotion.Settle), RepeatMode.Reverse),
        label = "pulse",
    )
    return scale
}

/**
 * A place for a side action: its button with the label under, or, with no [action], the same
 * room left empty so the pills stay where they are whatever the call offers.
 */
@Composable
private fun SidePlace(action: AnswerSideAction?, onClick: () -> Unit) {
    Column(Modifier.width(SideWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(SideSize), contentAlignment = Alignment.Center) {
            if (action != null) {
                val accessibilityLabel = stringResource(action.accessibilityLabel)
                PvotIconButton(
                    icon = painterResource(action.icon),
                    contentDescription = stringResource(action.description),
                    onClick = onClick,
                    size = SideSize,
                    modifier = Modifier
                        .semantics { onClick(accessibilityLabel) { onClick(); true } }
                        .testTag(if (action.slot == AnswerSideSlot.AnswerAndRelease) TestTags.ANSWER_AND_RELEASE else TestTags.ANSWER_SECONDARY),
                )
            }
        }
        Text(
            text = if (action != null) stringResource(action.label) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Two lines, taken or not, so the pills under the row stay put whatever the call offers.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // The button already says this to TalkBack; an empty place says nothing.
            modifier = Modifier
                .padding(top = PvotSpacing.S)
                .then(if (action == null) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier.semantics { hideFromAccessibility() }),
        )
    }
}

/** The side actions' buttons, and the room each one keeps for its label. */
private val SideSize = 56.dp
private val SideWidth = 112.dp

/** How far the Answer pill grows on each breath, and how long one takes. */
private const val PulseScale = 1.04f
private const val PulseMillis = 1200
