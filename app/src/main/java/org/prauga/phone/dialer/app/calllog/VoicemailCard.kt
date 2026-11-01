/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.text.TextUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotCardDefaults
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.list.PvotSwipeRow
import com.prauga.pvot.designsystem.components.list.PvotSwipeRowDefaults
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgress
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.app.voicemail.formatClipTime
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.TestTags

/** Size of the caller's avatar beside their name on a voicemail card. */
private val CallerAvatarSize = 20.dp

/**
 * A voicemail as a card: the transcript (or the caller, without one) as its title, the caller and
 * time under it, then call back and play. The open voicemail's card grows the playback progress,
 * speaker and share, and the row's other actions; swiping it away deletes it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun VoicemailCard(
    row: CallLogRowUi,
    playback: VoicemailPlaybackState,
    expanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    actions: CallHistoryActions,
    onRowAction: (RowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The presenter plays whichever voicemail is open, so the expanded card is the one playing.
    val isOpen = expanded
    val container by
        animateColorAsState(
            when {
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                expanded -> MaterialTheme.colorScheme.surfaceContainerHigh
                else ->
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = PvotCardDefaults.ContainerAlpha
                    )
            },
            tween(PvotDurations.Short),
            label = "voicemailCardColor",
        )
    PvotSwipeRow(
        modifier = modifier.padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.XS),
        endAction =
            PvotSwipeRowDefaults.destructiveAction(
                label = stringResource(R.string.call_history_delete),
                icon = painterResource(R.drawable.ic_trash),
                onSwipe = onDelete,
            ),
    ) {
        PvotCard(
            modifier = Modifier.fillMaxWidth().testTag(TestTags.callHistoryRow(row.group.rowId)),
            colors = PvotCardDefaults.colors(containerColor = container),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(PvotSpacing.L),
                verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
            ) {
                Text(
                    text = row.group.transcription ?: row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (row.group.isRead) null else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        SelectedTile(size = CallerAvatarSize)
                    } else {
                        ContactAvatar(
                            name = row.title,
                            photoUri = row.info.photoUri?.toString(),
                            colorKey = row.info.lookupKey ?: row.group.number,
                            size = CallerAvatarSize,
                        )
                    }
                    Spacer(Modifier.width(PvotSpacing.S))
                    // Without a transcript the title is already the caller, so the row only adds
                    // when.
                    if (row.group.transcription != null) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Text(
                        text =
                            if (row.group.transcription != null) "  ·  " + row.details
                            else row.details,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isOpen) Progress(playback)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (RowAction.CALL in row.actions) {
                        PvotIconButton(
                            icon = painterResource(R.drawable.ic_phone),
                            contentDescription =
                                TextUtils.expandTemplate(
                                        stringResource(R.string.description_call_action),
                                        row.title,
                                    )
                                    .toString(),
                            onClick = { onRowAction(RowAction.CALL) },
                            size = PvotIconButtonDefaults.SmallSize,
                            colors =
                                if (isOpen) PvotIconButtonDefaults.filledColors()
                                else PvotIconButtonDefaults.tonalColors(),
                            modifier =
                                Modifier.testTag(
                                    TestTags.callHistoryAction(row.group.rowId, RowAction.CALL.name)
                                ),
                        )
                    }
                    if (isOpen) {
                        Spacer(Modifier.width(PvotSpacing.S))
                        PvotIconButton(
                            icon =
                                painterResource(
                                    if (playback.isSpeakerOn) R.drawable.ic_volume
                                    else R.drawable.ic_volume_2
                                ),
                            contentDescription =
                                stringResource(
                                    if (playback.isSpeakerOn) R.string.voicemail_speaker_off
                                    else R.string.voicemail_speaker_on
                                ),
                            onClick = { playback.toggleSpeaker() },
                            size = PvotIconButtonDefaults.SmallSize,
                            checked = playback.isSpeakerOn,
                        )
                        if (playback.isShareVisible) {
                            Spacer(Modifier.width(PvotSpacing.S))
                            PvotIconButton(
                                icon = painterResource(R.drawable.ic_share),
                                contentDescription =
                                    stringResource(R.string.call_log_action_share_voicemail),
                                onClick = { playback.share() },
                                size = PvotIconButtonDefaults.SmallSize,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    PvotIconButton(
                        icon =
                            painterResource(
                                if (isOpen && playback.isPlaying) R.drawable.ic_player_pause
                                else R.drawable.ic_player_play
                            ),
                        contentDescription = stringResource(R.string.voicemail_play_start_pause),
                        onClick = { if (isOpen) playback.togglePlayback() else onPlay() },
                        enabled = !isOpen || playback.controlsEnabled,
                        colors = PvotIconButtonDefaults.filledColors(),
                        modifier =
                            Modifier.testTag(
                                if (isOpen) TestTags.VOICEMAIL_PLAY
                                else TestTags.callHistoryPrimary(row.group.rowId)
                            ),
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter =
                        expandVertically(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) +
                            fadeIn(tween(PvotDurations.Medium)),
                    exit =
                        shrinkVertically(tween(PvotDurations.Short)) +
                            fadeOut(tween(PvotDurations.Short)),
                ) {
                    // The rest of the row's actions, as on a call history row; Call is the button
                    // above.
                    FlowRow(
                        modifier =
                            Modifier.fillMaxWidth()
                                .testTag(TestTags.callHistoryActions(row.group.rowId)),
                        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                        verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                    ) {
                        for (action in row.actions) {
                            if (action == RowAction.CALL) continue
                            val (label, icon) = labelAndIcon(action, row)
                            PvotButton(
                                text = label,
                                onClick = { onRowAction(action) },
                                modifier =
                                    Modifier.testTag(
                                        TestTags.callHistoryAction(row.group.rowId, action.name)
                                    ),
                                style = PvotButtonStyle.Tonal,
                                icon = painterResource(icon),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The open voicemail's progress, seekable while it can play, with its position and length. */
@Composable
private fun Progress(playback: VoicemailPlaybackState) {
    Column(
        Modifier.fillMaxWidth().testTag(TestTags.VOICEMAIL_PLAYBACK),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
    ) {
        playback.statusText?.let { status ->
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val duration = playback.durationMs.coerceAtLeast(1)
        PvotLinearProgress(
            progress = { playback.positionMs.toFloat() / duration },
            modifier = Modifier.fillMaxWidth(),
            onSeek =
                if (playback.isSeekEnabled) {
                    { fraction -> playback.seekTo((fraction * duration).toInt()) }
                } else null,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatClipTime(playback.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatClipTime(playback.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
