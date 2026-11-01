/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.voicemail

import android.text.Html
import android.text.Spanned
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.slider.PvotSlider
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessage

/** Play, seek, speaker and share controls for the open voicemail. */
@Composable
fun VoicemailPlaybackControls(state: VoicemailPlaybackState, modifier: Modifier = Modifier) {
    var seekingTo by remember { mutableStateOf<Float?>(null) }
    Column(modifier.fillMaxWidth().testTag(TestTags.VOICEMAIL_PLAYBACK)) {
        state.statusText?.let { status ->
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = state::togglePlayback,
                enabled = state.controlsEnabled,
                modifier = Modifier.testTag(TestTags.VOICEMAIL_PLAY),
            ) {
                Icon(
                    painter = painterResource(if (state.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                    contentDescription = stringResource(R.string.voicemail_play_start_pause),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            val duration = state.durationMs.coerceAtLeast(1).toFloat()
            PvotSlider(
                value = seekingTo ?: state.positionMs.toFloat(),
                onValueChange = { value ->
                    if (seekingTo == null) state.startSeeking()
                    seekingTo = value
                    state.seekTo(value.toInt())
                },
                onValueChangeFinished = {
                    seekingTo?.let { state.finishSeeking(it.toInt()) }
                    seekingTo = null
                },
                valueRange = 0f..duration,
                enabled = state.isSeekEnabled,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { state.toggleSpeaker() }) {
                Icon(
                    painter = painterResource(if (state.isSpeakerOn) R.drawable.ic_volume else R.drawable.ic_volume_2),
                    contentDescription = stringResource(
                        if (state.isSpeakerOn) R.string.voicemail_speaker_off else R.string.voicemail_speaker_on,
                    ),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.M)) {
            Text(
                text = formatClipTime(state.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatClipTime(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isShareVisible) {
            PvotButton(
                text = stringResource(R.string.call_log_action_share_voicemail),
                onClick = { state.share() },
                style = PvotButtonStyle.Text,
                icon = painterResource(R.drawable.ic_share),
            )
        }
    }
}

/** A voicemail status message, such as a full inbox, with its actions. */
@Composable
fun VoicemailStatusCard(message: VoicemailErrorMessage, modifier: Modifier = Modifier) {
    val view = LocalView.current
    PvotCard(modifier = modifier.fillMaxWidth().padding(PvotSpacing.L).testTag(TestTags.VOICEMAIL_STATUS)) {
        Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
            Text(text = message.title.toString(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = message.description.toAnnotated(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MessageActions(message, view)
        }
    }
}

/** The carrier's visual voicemail terms, shown instead of the voicemail list until answered. */
@Composable
fun VoicemailTermsScreen(message: VoicemailErrorMessage, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PvotSpacing.L)
            .testTag(TestTags.VOICEMAIL_STATUS),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        message.imageResourceId?.let { image ->
            Image(painterResource(image), contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Text(text = message.title.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message.description.toAnnotated(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MessageActions(message, view)
    }
}

@Composable
private fun MessageActions(message: VoicemailErrorMessage, view: View) {
    val actions = message.actions.orEmpty()
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S), modifier = Modifier.fillMaxWidth()) {
        for (action in actions) {
            PvotButton(
                text = action.text.toString(),
                onClick = { action.listener.onClick(view) },
                style = if (action.isRaised) PvotButtonStyle.Filled else PvotButtonStyle.Text,
            )
        }
    }
}

/** Keeps the links of a styled message, such as "Learn more". */
private fun CharSequence?.toAnnotated(): AnnotatedString = when (this) {
    null -> AnnotatedString("")
    is Spanned -> AnnotatedString.fromHtml(Html.toHtml(this, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE))
    else -> AnnotatedString(toString())
}
