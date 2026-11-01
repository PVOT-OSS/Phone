/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.incall.impl

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.collection.ArraySet
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.effects.pvotPressScale
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags

/** The six slots of the in-call button grid, three to a row. */
class InCallButtonSlots(context: Context) {
    val buttons: List<CheckableLabeledButton> = List(BUTTON_COUNT) { CheckableLabeledButton(context) }

    /** Hides the buttons from TalkBack while the dialpad covers them. */
    fun onDialpadVisibilityChange(isShowing: Boolean) {
        buttons.forEach { it.hiddenFromAccessibility = isShowing }
    }

    /**
     * Gives each allowed button a slot, in the order [buttonChooser] prefers, and empties the rest.
     * Returns how many slots the grid has.
     */
    fun place(
        buttonControllers: List<ButtonController>,
        buttonChooser: ButtonChooser?,
        voiceNetworkType: Int,
        phoneType: Int,
        rows: Int,
    ): Int {
        val allowed = ArraySet<Int>()
        val disabled = ArraySet<Int>()
        for (controller in buttonControllers) {
            if (controller.isAllowed) {
                allowed += controller.inCallButtonId
                if (!controller.isEnabled) disabled += controller.inCallButtonId
            }
        }
        buttonControllers.forEach { it.setButton(null) }
        val chooser = buttonChooser ?: ButtonChooserFactory.newButtonChooser(voiceNetworkType, false, phoneType)
        val slots = rows * BUTTONS_PER_ROW
        val placement = chooser.getButtonPlacement(slots, allowed, disabled)
        for (i in 0 until BUTTON_COUNT) {
            if (i >= placement.size) {
                buttons[i].setVisibility(android.view.View.INVISIBLE)
                continue
            }
            buttonControllers.first { it.inCallButtonId == placement[i] }.setButton(buttons[i])
        }
        return slots
    }

    companion object {
        const val BUTTON_COUNT = 6
        const val BUTTONS_PER_ROW = 3
    }
}

/** The in-call buttons: mute, speaker, keypad, hold and the rest, as round toggles with labels. */
@Composable
fun InCallButtonGrid(slots: InCallButtonSlots, rows: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PvotSpacing.L)) {
        for (row in slots.buttons.take(rows * InCallButtonSlots.BUTTONS_PER_ROW).chunked(InCallButtonSlots.BUTTONS_PER_ROW)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { button -> InCallButton(button, Modifier.testTag(TestTags.incallButton(slots.buttons.indexOf(button)))) }
            }
        }
    }
}

@Composable
private fun InCallButton(button: CheckableLabeledButton, modifier: Modifier = Modifier) {
    if (!button.visible) {
        Box(modifier.width(ButtonWidth).size(width = ButtonWidth, height = CircleSize))
        return
    }
    val source = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (button.checkedState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(PvotDurations.Short),
        label = "inCallButton",
    )
    val content = if (button.checkedState) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // The circle and its label are one target, as the old button was.
    Column(
        modifier = modifier
            .width(ButtonWidth)
            .pvotPressScale(source)
            .clickable(interactionSource = source, indication = null, enabled = button.enabledState, role = Role.Button) {
                button.performClick()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = (button.description ?: button.label).toString()
                toggleableState = ToggleableState(button.checkedState)
                if (button.hiddenFromAccessibility) hideFromAccessibility()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CircleSize)
                .clip(CircleShape)
                .background(container)
                .alpha(if (button.enabledState) 1f else DisabledAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(pvotIconFor(button.iconRes)), contentDescription = null, tint = content)
            if (button.showMoreIndicator) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(PvotSpacing.S)
                        .size(6.dp)
                        .background(content, CircleShape),
                )
            }
        }
        Text(
            text = button.label.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (button.enabledState) 1f else DisabledAlpha),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = PvotSpacing.S),
        )
    }
}

/** The Tabler icon for an in-call button icon the controllers set. */
@DrawableRes
internal fun pvotIconFor(@DrawableRes iconRes: Int): Int = when (iconRes) {
    R.drawable.ic_addcall_white -> R.drawable.ic_user_plus
    R.drawable.ic_sim_change_white -> R.drawable.ic_device_sim
    R.drawable.quantum_ic_call_merge_vd_theme_24 -> R.drawable.ic_arrow_merge
    R.drawable.quantum_ic_dialpad_vd_theme_24 -> R.drawable.ic_dialpad
    R.drawable.quantum_ic_group_vd_theme_24 -> R.drawable.ic_users
    R.drawable.quantum_ic_headset_vd_theme_24 -> R.drawable.ic_headphones
    R.drawable.quantum_ic_mic_off_vd_theme_24 -> R.drawable.ic_microphone_off
    R.drawable.quantum_ic_pause_vd_theme_24 -> R.drawable.ic_player_pause
    R.drawable.quantum_ic_phone_bluetooth_vd_theme_24 -> R.drawable.ic_bluetooth
    R.drawable.quantum_ic_phone_in_talk_vd_theme_24 -> R.drawable.ic_phone
    R.drawable.quantum_ic_record_white_36 -> R.drawable.ic_player_record
    R.drawable.quantum_ic_rtt_vd_theme_24 -> R.drawable.ic_message
    R.drawable.quantum_ic_swap_calls_vd_theme_24 -> R.drawable.ic_arrows_exchange
    R.drawable.quantum_ic_videocam_vd_theme_24 -> R.drawable.ic_video
    R.drawable.quantum_ic_volume_up_vd_theme_24 -> R.drawable.ic_volume
    0 -> R.drawable.ic_phone
    else -> iconRes
}

private val CircleSize = 64.dp
private val ButtonWidth = 96.dp
private const val DisabledAlpha = 0.38f

