/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.incall.impl

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.collection.ArraySet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCapsule
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.call.PvotCallAction
import com.prauga.pvot.designsystem.components.call.PvotCallActionButton
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.incall.protocol.InCallButtonIds

/**
 * The in-call buttons, one slot per button the call can offer. [place] records which button each
 * slot holds, so [InCallControls] can put audio, mute and the keypad on the capsule and the rest in
 * the More sheet.
 */
class InCallButtonSlots(context: Context) {
    val buttons: List<CheckableLabeledButton> = List(BUTTON_COUNT) { CheckableLabeledButton(context) }

    /** The [InCallButtonIds] each slot holds, or [NO_BUTTON]. */
    val ids: SnapshotStateList<Int> = mutableStateListOf<Int>().apply { repeat(BUTTON_COUNT) { add(NO_BUTTON) } }

    /** Hides the buttons from TalkBack while the dialpad covers them. */
    fun onDialpadVisibilityChange(isShowing: Boolean) {
        buttons.forEach { it.hiddenFromAccessibility = isShowing }
    }

    /** Gives each allowed button a slot, in the order [buttonChooser] prefers, and empties the rest. */
    fun place(
        buttonControllers: List<ButtonController>,
        buttonChooser: ButtonChooser?,
        voiceNetworkType: Int,
        phoneType: Int,
    ) {
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
        val placement = chooser.getButtonPlacement(BUTTON_COUNT, allowed, disabled)
        for (i in 0 until BUTTON_COUNT) {
            if (i >= placement.size) {
                buttons[i].setVisibility(android.view.View.INVISIBLE)
                ids[i] = NO_BUTTON
                continue
            }
            ids[i] = placement[i]
            buttonControllers.first { it.inCallButtonId == placement[i] }.setButton(buttons[i])
        }
    }

    companion object {
        /** Room for every button a call can offer. */
        const val BUTTON_COUNT = InCallButtonIds.BUTTON_COUNT
        const val NO_BUTTON = -1

        /** The buttons on the capsule, in order; the rest are in the More sheet. */
        val CAPSULE_BUTTONS = listOf(InCallButtonIds.BUTTON_AUDIO, InCallButtonIds.BUTTON_MUTE, InCallButtonIds.BUTTON_DIALPAD)
    }
}

/**
 * The call's controls: a capsule of round buttons (audio route, mute, keypad and More, which opens
 * the call's other actions in a sheet) and the End pill under it.
 */
@Composable
fun InCallControls(
    slots: InCallButtonSlots,
    endCallEnabled: Boolean,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreOpen by remember { mutableStateOf(false) }
    val placed = slots.ids.indices.filter { slots.ids[it] != InCallButtonSlots.NO_BUTTON && slots.buttons[it].visible }
    val onCapsule = InCallButtonSlots.CAPSULE_BUTTONS.mapNotNull { id -> placed.firstOrNull { slots.ids[it] == id } }
    val inMore = placed - onCapsule.toSet()
    val endDescription = stringResource(R.string.incall_content_description_end_call)
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.L),
    ) {
        if (onCapsule.isNotEmpty() || inMore.isNotEmpty()) {
            PvotCapsule(modifier = Modifier.testTag(TestTags.INCALL_CAPSULE)) {
                for (index in onCapsule) CapsuleButton(slots, index)
                if (inMore.isNotEmpty()) {
                    PvotIconButton(
                        icon = painterResource(R.drawable.ic_dots_vertical),
                        contentDescription = stringResource(R.string.incall_label_more),
                        onClick = { moreOpen = true },
                        size = ControlSize,
                        modifier = Modifier.testTag(TestTags.INCALL_MORE),
                    )
                }
            }
        }
        PvotCallActionButton(
            action = PvotCallAction.End,
            icon = painterResource(R.drawable.ic_phone_off),
            label = stringResource(R.string.incall_label_end),
            onClick = onEndCall,
            labelInside = true,
            enabled = endCallEnabled,
            modifier = Modifier
                .semantics { contentDescription = endDescription }
                .testTag(TestTags.INCALL_END_CALL),
        )
    }
    if (moreOpen) {
        PvotBottomSheet(visible = true, onDismissRequest = { moreOpen = false }) {
            for (index in inMore) {
                val button = slots.buttons[index]
                PvotSheetItem(
                    title = button.label.toString(),
                    subtitle = if (button.checkedState) stringResource(R.string.incall_button_on) else null,
                    onClick = {
                        moreOpen = false
                        button.performClick()
                    },
                    enabled = button.enabledState,
                    icon = { Icon(painterResource(pvotIconFor(button.iconRes)), contentDescription = null) },
                    modifier = Modifier.testTag(TestTags.incallButton(index)),
                )
            }
        }
    }
}

/** A button on the capsule; mute turns red while on, the others the accent. */
@Composable
private fun CapsuleButton(slots: InCallButtonSlots, index: Int) {
    val button = slots.buttons[index]
    val isMute = slots.ids[index] == InCallButtonIds.BUTTON_MUTE
    PvotIconButton(
        icon = painterResource(pvotIconFor(button.iconRes)),
        contentDescription = (button.description ?: button.label).toString(),
        onClick = button::performClick,
        size = ControlSize,
        checked = button.checkedState,
        enabled = button.enabledState,
        colors = if (isMute) {
            PvotIconButtonDefaults.tonalColors(
                checkedContainerColor = MaterialTheme.colorScheme.error,
                checkedContentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            PvotIconButtonDefaults.tonalColors()
        },
        modifier = Modifier
            .then(if (button.hiddenFromAccessibility) Modifier.semantics { hideFromAccessibility() } else Modifier)
            .testTag(TestTags.incallButton(index)),
    )
}

/** The Tabler icon for an in-call button icon the controllers set. */
@DrawableRes
internal fun pvotIconFor(@DrawableRes iconRes: Int): Int = when (iconRes) {
    R.drawable.ic_addcall_white -> R.drawable.ic_user_plus
    R.drawable.ic_sim_change_white -> R.drawable.ic_device_sim
    R.drawable.quantum_ic_call_merge_vd_theme_24 -> R.drawable.ic_arrow_merge
    R.drawable.quantum_ic_dialpad_vd_theme_24 -> R.drawable.ic_dialpad
    R.drawable.quantum_ic_forward_vd_theme_24 -> R.drawable.ic_arrow_forward_up
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

/** The capsule's buttons, large enough for a hurried tap during a call. */
private val ControlSize = PvotIconButtonDefaults.LargeSize

