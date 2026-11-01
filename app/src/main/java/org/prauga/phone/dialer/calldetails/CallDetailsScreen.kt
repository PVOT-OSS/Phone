/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.content.Intent
import android.provider.CallLog.Calls
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotGlassCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonDefaults
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotAvatarDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotListRowDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassMaterial
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.rememberPvotTopBarCompact
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.calllogutils.CallLogDates
import org.prauga.phone.dialer.calllogutils.CallLogDurations
import org.prauga.phone.dialer.calllogutils.CallTypeHelper
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.ui.PhoneBackground
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.TestTags

/** What the call details screen can ask its activity to do. */
internal interface CallDetailsActions {
    fun back()
    fun callBack(video: Boolean)
    fun message()
    fun copyNumber()
    fun editNumber()
    fun delete()
    fun showTranscript(entry: CallDetailsEntryUi)
    fun play(recording: CallRecording)
    fun recordingTime(recording: CallRecording): String
}

/**
 * The call details: who and how to reach them on a card of glass, each call, then copy, edit and
 * delete. The top bar turns to glass once the list scrolls under it.
 *
 * @param avatar The contact's picture at the size given; it opens Quick Contact when tapped
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CallDetailsScreen(
    ui: CallDetailsUi,
    assistedDialingCountryCode: Int?,
    callWithIntent: Intent?,
    actions: CallDetailsActions,
    avatar: @Composable (Dp) -> Unit,
) {
    var recordingsToPick by remember { mutableStateOf<List<CallRecording>?>(null) }
    val listState = rememberLazyListState()
    PhoneScaffold(
        background = PhoneBackground.Live,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            PvotTopBar(
                title = stringResource(R.string.call_details),
                onBack = actions::back,
                compact = rememberPvotTopBarCompact(listState),
            )
        },
    ) { padding ->
        PvotScreen(
            modifier = Modifier.fillMaxSize().testTag(TestTags.CALL_DETAILS),
            state = listState,
            contentPadding = PaddingValues(
                start = PvotScreenDefaults.HorizontalPadding,
                end = PvotScreenDefaults.HorizontalPadding,
                top = padding.calculateTopPadding() + PvotSpacing.S,
                bottom = PvotScreenDefaults.BottomSpacing + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item(key = "header") { Hero(ui, assistedDialingCountryCode, callWithIntent, actions, avatar) }
            item(key = "history") {
                PvotCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = PvotSpacing.XS)) {
                        for (entry in ui.entries) {
                            Entry(entry, actions, onPickRecording = { recordingsToPick = it })
                        }
                    }
                }
            }
            item(key = "actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                ) {
                    if (ui.hasNumber) {
                        PvotButton(stringResource(R.string.call_details_copy_number), onClick = actions::copyNumber,
                            style = PvotButtonStyle.Tonal)
                        PvotButton(stringResource(R.string.call_details_edit_number), onClick = actions::editNumber,
                            style = PvotButtonStyle.Tonal)
                    }
                    PvotButton(stringResource(R.string.delete), onClick = actions::delete, style = PvotButtonStyle.Destructive,
                        modifier = Modifier.testTag(TestTags.CALL_DETAILS_DELETE))
                }
            }
        }
    }

    PvotBottomSheet(visible = recordingsToPick != null, onDismissRequest = { recordingsToPick = null }) {
        for (recording in recordingsToPick.orEmpty()) {
            PvotSheetItem(title = actions.recordingTime(recording), onClick = {
                recordingsToPick = null
                actions.play(recording)
            })
        }
    }
}

/** Who the calls were with, large on a card of glass, and the ways to reach them. */
@Composable
private fun Hero(
    ui: CallDetailsUi,
    assistedDialingCountryCode: Int?,
    callWithIntent: Intent?,
    actions: CallDetailsActions,
    avatar: @Composable (Dp) -> Unit,
) {
    val context = LocalContext.current
    val header = ui.header
    PvotGlassCard(
        modifier = Modifier.fillMaxWidth(),
        style = PvotGlassDefaults.materialStyle(PvotGlassMaterial.Regular),
        contentPadding = PaddingValues(PvotSpacing.L),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
        ) {
            avatar(PvotAvatarDefaults.Hero)
            Text(
                text = header.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = PvotSpacing.S),
            )
            header.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
            header.network?.let {
                // The SIM's own highlight color, as the system shows it.
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = header.networkColor?.let(::Color) ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (header.usedAssistedDialing) {
                Text(
                    text = if (assistedDialingCountryCode != null && assistedDialingCountryCode > 0) {
                        stringResource(R.string.assisted_dialing_country_code_entry, assistedDialingCountryCode.toString())
                    } else {
                        stringResource(R.string.assisted_dialing_country_code_entry_failure)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            if (header.callbackAction != CallbackAction.NONE) {
                FlowRow(
                    modifier = Modifier.padding(top = PvotSpacing.M),
                    horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                ) {
                    val video = header.callbackAction == CallbackAction.IMS_VIDEO
                    PvotButton(
                        text = stringResource(if (video) R.string.video_call else R.string.call),
                        icon = painterResource(if (video) R.drawable.ic_video else R.drawable.ic_phone),
                        onClick = { actions.callBack(video) },
                        modifier = Modifier.testTag(TestTags.CALL_DETAILS_CALL_BACK),
                    )
                    if (ui.canMessage) {
                        PvotButton(
                            text = stringResource(R.string.call_details_message),
                            icon = painterResource(R.drawable.ic_message),
                            style = PvotButtonStyle.Tonal,
                            onClick = actions::message,
                            modifier = Modifier.testTag(TestTags.CALL_DETAILS_MESSAGE),
                        )
                    }
                    if (!video && callWithIntent != null) {
                        PvotButton(stringResource(R.string.pre_call_select_phone_account), style = PvotButtonStyle.Tonal,
                            onClick = { context.startActivity(callWithIntent) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(entry: CallDetailsEntryUi, actions: CallDetailsActions, onPickRecording: (List<CallRecording>) -> Unit) {
    val context = LocalContext.current
    val typeHelper = remember(context) { CallTypeHelper(context.resources) }
    val missed = CallTypeHelper.isMissedCallType(entry.callType)
    val accent = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val details = buildList {
        add(CallLogDates.formatDate(context, entry.date).toString())
        if (entry.showsDuration) add(CallLogDurations.formatDurationAndDataUsage(context, entry.duration, entry.dataUsage).toString())
    }.joinToString(" · ")
    PvotListRow(
        title = typeHelper.getCallTypeText(entry.callType, entry.isVideo, entry.isPulledExternally).toString(),
        supportingText = details,
        modifier = Modifier.testTag(TestTags.callDetailsEntry(entry.callId)),
        leading = { Icon(painterResource(iconFor(entry.callType)), contentDescription = null, tint = accent) },
    )
    val recordings = entry.recordings
    if (recordings.isNotEmpty() || entry.isRtt) {
        // Line the buttons' text up with the row title: row padding, icon and gap, less the
        // text button's own padding.
        val direction = LocalLayoutDirection.current
        val textStart = PvotListRowDefaults.ContentPadding.calculateStartPadding(direction) + IconSize + PvotSpacing.L -
            PvotButtonDefaults.TextContentPadding.calculateStartPadding(direction)
        FlowRow(Modifier.padding(start = textStart, end = PvotSpacing.L, bottom = PvotSpacing.S),
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
            if (recordings.isNotEmpty()) {
                PvotButton(
                    text = pluralStringResource(R.plurals.play_recordings, recordings.size, recordings.size),
                    style = PvotButtonStyle.Text,
                    onClick = { if (recordings.size == 1) actions.play(recordings.single()) else onPickRecording(recordings) },
                    modifier = Modifier.testTag(TestTags.callDetailsRecordings(entry.callId)),
                )
            }
            if (entry.isRtt) {
                if (entry.hasRttTranscript) {
                    PvotButton(stringResource(R.string.rtt_transcript_link), style = PvotButtonStyle.Text,
                        onClick = { actions.showTranscript(entry) })
                } else {
                    Text(stringResource(R.string.rtt_transcript_not_available), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun iconFor(callType: Int): Int = when (callType) {
    Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> R.drawable.ic_call_incoming
    Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing
    Calls.VOICEMAIL_TYPE -> R.drawable.ic_call_voicemail
    Calls.BLOCKED_TYPE -> R.drawable.ic_call_blocked
    else -> R.drawable.ic_call_missed
}

/** Material's icon size, which the call type icons use. */
private val IconSize = 24.dp
