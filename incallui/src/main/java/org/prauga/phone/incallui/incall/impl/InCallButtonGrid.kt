/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.incall.impl

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.collection.ArraySet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonColors
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.call.PvotCallAction
import com.prauga.pvot.designsystem.components.call.PvotCallActionButton
import com.prauga.pvot.designsystem.components.pvotGlassSurface
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotCallEnd
import com.prauga.pvot.designsystem.theme.PvotOnCallEnd
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.incallui.R
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
 * The call's controls: a glass panel of labelled round buttons, three to a row, and the End pill
 * under it. The first row is audio route, mute and keypad; the second the call's next buttons in
 * the order the chooser prefers, with More for the rest, which opens them in a sheet.
 *
 * The panel has a place for every button it can hold from its first frame, so it is as tall
 * before the call offers any buttons as after, and the contact grid above it stays put. A button
 * fades and grows into its place, a button that leaves fades out, and a place that changes
 * buttons crossfades between them.
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
    val layout = ControlsLayout.of(placed) { slots.ids[it] }
    val moreLabel = stringResource(R.string.incall_label_more)
    // One entry per place on the panel: a slot index, MORE_CELL, or null while the place is empty.
    val filled: List<Int?> = layout.panel + if (layout.more.isNotEmpty()) listOf(MORE_CELL) else emptyList()
    val places = filled + List((ControlsLayout.PANEL_SIZE - filled.size).coerceAtLeast(0)) { null }
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ControlsGap),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pvotGlassSurface(LocalPvotGlassBackdrop.current, PanelCorner, role = null)
                .animateContentSize(tween(PvotDurations.Medium, easing = PvotMotion.Settle))
                .padding(vertical = PvotSpacing.L, horizontal = PvotSpacing.S)
                .testTag(TestTags.INCALL_CAPSULE),
            verticalArrangement = Arrangement.spacedBy(PvotSpacing.L),
        ) {
            for (row in places.chunked(ColumnsPerRow)) {
                Row(Modifier.fillMaxWidth()) {
                    for (place in row) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            // Gives the place its height, so an empty one is as tall as a full one.
                            Column(Modifier.alpha(0f).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(ControlSize + PvotSpacing.S))
                                Text(text = moreLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                            PanelPlace(
                                cell = place?.let { if (it == MORE_CELL) moreCell(moreLabel) else slotCell(slots, it) },
                                onClick = { cell -> if (cell.slot == MORE_CELL) moreOpen = true else slots.buttons[cell.slot].performClick() },
                            )
                        }
                    }
                }
            }
        }
        EndCallButton(endCallEnabled, onEndCall)
    }
    if (moreOpen) {
        PvotBottomSheet(visible = true, onDismissRequest = { moreOpen = false }) {
            for (index in layout.more) {
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

/**
 * What one place on the panel shows, taken from its button so a place that changes buttons can
 * keep drawing the old one while it fades out.
 */
@Immutable
private data class PanelCell(
    /** The button's [InCallButtonIds] id, or [MORE_CELL]. */
    val id: Int,
    /** The slot the button is in, or [MORE_CELL]. */
    val slot: Int,
    @DrawableRes val iconRes: Int,
    val label: String,
    val description: String,
    val checked: Boolean?,
    val enabled: Boolean,
    val hiddenFromAccessibility: Boolean,
)

private fun slotCell(slots: InCallButtonSlots, slot: Int): PanelCell {
    val button = slots.buttons[slot]
    return PanelCell(
        id = slots.ids[slot],
        slot = slot,
        iconRes = panelIconFor(button.iconRes),
        label = button.label.toString(),
        description = (button.description ?: button.label).toString(),
        checked = button.checkedState,
        enabled = button.enabledState,
        hiddenFromAccessibility = button.hiddenFromAccessibility,
    )
}

private fun moreCell(label: String) = PanelCell(
    id = MORE_CELL,
    slot = MORE_CELL,
    iconRes = R.drawable.ic_dots_filled,
    label = label,
    description = label,
    checked = null,
    enabled = true,
    hiddenFromAccessibility = false,
)

/**
 * One place on the panel: its [cell] fades and grows in, fades out when the place empties, and
 * crossfades when another button takes the place.
 */
@Composable
private fun PanelPlace(cell: PanelCell?, onClick: (PanelCell) -> Unit) {
    // The last button stays while it fades out.
    var shown by remember { mutableStateOf(cell) }
    if (cell != null) shown = cell
    // What each button last showed, by id, so one fading out keeps its own look under the next.
    val latest = remember { mutableStateMapOf<Int, PanelCell>() }
    if (cell != null) latest[cell.id] = cell
    AnimatedVisibility(
        visible = cell != null,
        enter = fadeIn(CellSpec) + scaleIn(CellSpec, initialScale = CellEnterScale),
        exit = fadeOut(CellSpec) + scaleOut(CellSpec, targetScale = CellEnterScale),
    ) {
        val current = shown ?: return@AnimatedVisibility
        // Keyed by the button's id: its state changes in place, another button fades in over it.
        Crossfade(targetState = current.id, animationSpec = CellSpec, label = "panelPlace") { id ->
            latest[id]?.let { c -> ControlCell(c) { onClick(c) } }
        }
    }
}

/** The End pill: as wide as two of the panel's columns, with the hung-up handset and "End". */
@Composable
fun EndCallButton(enabled: Boolean, onEndCall: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.incall_content_description_end_call)
    PvotCallActionButton(
        action = PvotCallAction.End,
        icon = painterResource(R.drawable.ic_call_end),
        label = stringResource(R.string.incall_label_end),
        onClick = onEndCall,
        width = EndCallWidth,
        labelInside = true,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }.testTag(TestTags.INCALL_END_CALL),
    )
}

/**
 * Which placed slots go on the panel and which in the More sheet: audio, mute and keypad first,
 * then the chooser's next buttons; when they don't all fit, the last place on the panel is More.
 */
internal data class ControlsLayout(val panel: List<Int>, val more: List<Int>) {
    companion object {
        /** Buttons on the panel, More included. */
        const val PANEL_SIZE = 6

        fun of(placed: List<Int>, idOf: (Int) -> Int): ControlsLayout {
            val first = InCallButtonSlots.CAPSULE_BUTTONS.mapNotNull { id -> placed.firstOrNull { idOf(it) == id } }
            val rest = placed - first.toSet()
            val room = PANEL_SIZE - first.size
            return if (rest.size <= room) {
                ControlsLayout(first + rest, emptyList())
            } else {
                ControlsLayout(first + rest.take(room - 1), rest.drop(room - 1))
            }
        }
    }
}

/** A labelled round button; mute turns red while on, the others the accent. The label taps too. */
@Composable
private fun ControlCell(cell: PanelCell, onClick: () -> Unit) {
    LabelledControl(
        icon = painterResource(cell.iconRes),
        label = cell.label,
        description = cell.description,
        onClick = onClick,
        checked = cell.checked,
        enabled = cell.enabled,
        colors = if (cell.id == InCallButtonIds.BUTTON_MUTE) {
            // Muted is the call's one warning: the same red as End.
            PvotIconButtonDefaults.tonalColors(checkedContainerColor = PvotCallEnd, checkedContentColor = PvotOnCallEnd)
        } else {
            PvotIconButtonDefaults.tonalColors()
        },
        modifier = Modifier
            .then(if (cell.hiddenFromAccessibility) Modifier.semantics { hideFromAccessibility() } else Modifier)
            .testTag(if (cell.slot == MORE_CELL) TestTags.INCALL_MORE else TestTags.incallButton(cell.slot)),
    )
}

@Composable
private fun LabelledControl(
    icon: Painter,
    label: String,
    description: String,
    onClick: () -> Unit,
    checked: Boolean?,
    enabled: Boolean,
    colors: PvotIconButtonColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.widthIn(max = CellWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
    ) {
        PvotIconButton(
            icon = icon,
            contentDescription = description,
            onClick = onClick,
            size = ControlSize,
            iconSize = ControlIconSize,
            checked = checked,
            enabled = enabled,
            colors = colors,
            modifier = modifier,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else DisabledLabelAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            // The button already says this to TalkBack; the label is a bigger target for the finger.
            modifier = Modifier
                .semantics { hideFromAccessibility() }
                .clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick),
        )
    }
}

/** The filled icon for the panel's main buttons, the outline one otherwise, as the More sheet uses. */
@DrawableRes
internal fun panelIconFor(@DrawableRes iconRes: Int): Int = when (iconRes) {
    R.drawable.quantum_ic_volume_up_vd_theme_24 -> R.drawable.ic_speaker_filled
    R.drawable.quantum_ic_mic_off_vd_theme_24 -> R.drawable.ic_microphone_off_filled
    R.drawable.quantum_ic_dialpad_vd_theme_24 -> R.drawable.ic_dialpad_filled
    R.drawable.ic_addcall_white -> R.drawable.ic_plus_bold
    R.drawable.quantum_ic_pause_vd_theme_24 -> R.drawable.ic_pause_filled
    R.drawable.quantum_ic_phone_in_talk_vd_theme_24 -> R.drawable.ic_call_filled
    else -> pvotIconFor(iconRes)
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

/** The panel's buttons, large enough for a hurried tap during a call. */
private val ControlSize = 64.dp
private val ControlIconSize = 28.dp

/** Each column of the panel: a button and its label. */
private val CellWidth = 88.dp
private const val ColumnsPerRow = 3
private const val MORE_CELL = -2

/** How a button comes and goes from its place. */
private val CellSpec = tween<Float>(PvotDurations.Medium, easing = PvotMotion.Settle)
private const val CellEnterScale = 0.8f

/** Corner of the panel round the buttons. */
private val PanelCorner = 32.dp

/** Space between the panel and the End pill. */
private val ControlsGap = PvotSpacing.L + PvotSpacing.S

private val EndCallWidth = 144.dp

/**
 * Space under the end call button, above the navigation bar. The button under the controls and the
 * one over the dialpad both use it, so it stays put when the dialpad opens.
 */
internal val EndCallBottomGap = PvotSpacing.L + PvotSpacing.S

private const val DisabledLabelAlpha = 0.38f

