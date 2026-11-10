/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.content.Intent
import android.provider.CallLog.Calls
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotCardDefaults
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotGlassCardDefaults
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonDefaults
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.call.PvotCallButton
import com.prauga.pvot.designsystem.components.call.PvotCallButtonDefaults
import com.prauga.pvot.designsystem.components.list.PvotAvatarDefaults
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListGroupDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotListRowDefaults
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.components.list.PvotSwipeRow
import com.prauga.pvot.designsystem.components.list.PvotSwipeRowDefaults
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.rememberPvotTopBarCompact
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.assisteddialing.ui.AssistedDialingSettingActivity
import org.prauga.phone.dialer.calllogutils.CallLogDates
import org.prauga.phone.dialer.calllogutils.CallLogDurations
import org.prauga.phone.dialer.calllogutils.CallTypeHelper
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.UriThumbnail

/** What the call details screen can ask its activity to do. */
internal interface CallDetailsActions {
    fun back()

    fun callBack(video: Boolean)

    fun message()

    fun copyNumber()

    fun editNumber()

    fun delete()

    /** Blocks the number, or unblocks it; the screen asks before blocking. */
    fun setBlocked(block: Boolean) = Unit

    /**
     * Saves the number as a new contact, or adds it to an existing one when [newContact] is false.
     */
    fun addContact(newContact: Boolean) = Unit

    /** Sets a reminder to call back. */
    fun remind() = Unit

    /** Deletes one call; the screen asks first. */
    fun deleteCall(entry: CallDetailsEntryUi)

    fun showTranscript(entry: CallDetailsEntryUi)

    fun play(recording: CallRecording)

    fun recordingTime(recording: CallRecording): String
}

/**
 * The call details: who and how to reach them on a card of glass, each call, then copy, edit, a
 * call-back reminder, saving the number, block and delete. The top bar turns to glass once the list
 * scrolls under it.
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
    blocked: Boolean? = null,
    canAddContact: Boolean = false,
    canRemind: Boolean = false,
) {
    var recordingsToPick by remember { mutableStateOf<List<CallRecording>?>(null) }
    var askBlock by remember { mutableStateOf(false) }
    var askDeleteCall by remember { mutableStateOf<CallDetailsEntryUi?>(null) }
    var askDeleteAll by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    PhoneScaffold(
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
            contentPadding =
                PaddingValues(
                    start = PvotScreenDefaults.HorizontalPadding,
                    end = PvotScreenDefaults.HorizontalPadding,
                    top = padding.calculateTopPadding() + PvotSpacing.S,
                    bottom =
                        PvotScreenDefaults.BottomSpacing +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            // The calls are separate items on one card, so the screen's own gap would split it.
            verticalArrangement = Arrangement.Top,
        ) {
            item(key = "header", contentType = "header") {
                Hero(ui, assistedDialingCountryCode, callWithIntent, actions, avatar)
                Spacer(Modifier.height(PvotSpacing.M))
            }
            // One lazy item per call, dressed as one PvotListGroup: the title, then each call on
            // its slice of the card, so a long history composes only what is on screen.
            item(key = "history_title", contentType = "history_title") {
                CallHistoryTitle(stringResource(R.string.call_log_activity_title))
            }
            itemsIndexed(
                items = ui.entries,
                key = { _, entry -> entry.callId },
                contentType = { _, _ -> "call" },
            ) { index, entry ->
                // The card's fill over the background, so a swiped row covers its gap exactly.
                val rowFill =
                    MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = PvotCardDefaults.ContainerAlpha)
                        .compositeOver(MaterialTheme.colorScheme.background)
                CallHistorySlice(first = index == 0, last = index == ui.entries.lastIndex) {
                    // Swiping a call away, either way, asks first; the last call means the whole
                    // history.
                    val delete =
                        PvotSwipeRowDefaults.destructiveAction(
                            label = stringResource(R.string.delete),
                            icon = painterResource(R.drawable.ic_trash),
                            onSwipe = { askDeleteCall = entry },
                            removesRow = false,
                        )
                    PvotSwipeRow(
                        startAction = delete,
                        endAction = delete,
                        containerColor = rowFill,
                    ) {
                        Column {
                            Entry(entry, actions, onPickRecording = { recordingsToPick = it })
                        }
                    }
                }
            }
            item(key = "history_end", contentType = "history_end") {
                Spacer(Modifier.height(PvotSpacing.M))
            }
            item(key = "actions") {
                val danger =
                    PvotListRowDefaults.themeColors()
                        .copy(
                            titleColor = MaterialTheme.colorScheme.error,
                            iconColor = MaterialTheme.colorScheme.error,
                        )
                PvotListGroup {
                    if (ui.hasNumber) {
                        item {
                            PvotPreferenceRow(
                                title = stringResource(R.string.call_details_copy_number),
                                onClick = actions::copyNumber,
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_copy),
                                        contentDescription = null,
                                    )
                                },
                                iconStyle = PvotRowIconStyle.Plain,
                                showChevron = false,
                            )
                        }
                        item {
                            PvotPreferenceRow(
                                title = stringResource(R.string.call_details_edit_number),
                                onClick = actions::editNumber,
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_pencil),
                                        contentDescription = null,
                                    )
                                },
                                iconStyle = PvotRowIconStyle.Plain,
                                showChevron = false,
                            )
                        }
                    }
                    if (canRemind) {
                        item {
                            PvotPreferenceRow(
                                title = stringResource(R.string.callback_reminder_action),
                                onClick = actions::remind,
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_bell),
                                        contentDescription = null,
                                    )
                                },
                                iconStyle = PvotRowIconStyle.Plain,
                                showChevron = false,
                            )
                        }
                    }
                    if (canAddContact) {
                        item {
                            PvotPreferenceRow(
                                title = stringResource(R.string.search_shortcut_create_new_contact),
                                onClick = { actions.addContact(newContact = true) },
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_user_plus),
                                        contentDescription = null,
                                    )
                                },
                                iconStyle = PvotRowIconStyle.Plain,
                                showChevron = false,
                            )
                        }
                        item {
                            PvotPreferenceRow(
                                title = stringResource(R.string.search_shortcut_add_to_contact),
                                onClick = { actions.addContact(newContact = false) },
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_user),
                                        contentDescription = null,
                                    )
                                },
                                iconStyle = PvotRowIconStyle.Plain,
                                showChevron = false,
                            )
                        }
                    }
                    if (ui.hasNumber && blocked != null) {
                        item {
                            PvotPreferenceRow(
                                title =
                                    stringResource(
                                        if (blocked) R.string.call_log_action_unblock_number
                                        else R.string.call_log_action_block_number
                                    ),
                                onClick = {
                                    if (blocked) actions.setBlocked(false) else askBlock = true
                                },
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_ban),
                                        contentDescription = null,
                                    )
                                },
                                iconStyle = PvotRowIconStyle.Plain,
                                showChevron = false,
                                colors = if (blocked) PvotListRowDefaults.themeColors() else danger,
                            )
                        }
                    }
                    item {
                        PvotPreferenceRow(
                            title = stringResource(R.string.delete),
                            onClick = { askDeleteAll = true },
                            modifier = Modifier.testTag(TestTags.CALL_DETAILS_DELETE),
                            icon = {
                                Icon(
                                    painterResource(R.drawable.ic_trash),
                                    contentDescription = null,
                                )
                            },
                            iconStyle = PvotRowIconStyle.Plain,
                            showChevron = false,
                            colors = danger,
                        )
                    }
                }
            }
        }
    }

    askDeleteCall?.let { entry ->
        val lastCall = ui.entries.size == 1
        PvotDialog(
            visible = true,
            onDismissRequest = { askDeleteCall = null },
            title =
                stringResource(
                    if (lastCall) R.string.call_details_delete_all_title
                    else R.string.call_details_delete_call_title
                ),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.delete),
                onClick = {
                    askDeleteCall = null
                    if (lastCall) actions.delete() else actions.deleteCall(entry)
                },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { askDeleteCall = null },
            )
        }
    }
    if (askBlock) {
        PvotDialog(
            visible = true,
            onDismissRequest = { askBlock = false },
            title = stringResource(R.string.block_number_confirmation_title, ui.header.title),
            message = stringResource(R.string.block_report_number_alert_details),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.block_number_ok),
                onClick = {
                    askBlock = false
                    actions.setBlocked(true)
                },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { askBlock = false },
            )
        }
    }
    if (askDeleteAll) {
        PvotDialog(
            visible = true,
            onDismissRequest = { askDeleteAll = false },
            title = stringResource(R.string.call_details_delete_all_title),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.delete),
                onClick = {
                    askDeleteAll = false
                    actions.delete()
                },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { askDeleteAll = false },
            )
        }
    }

    PvotBottomSheet(
        visible = recordingsToPick != null,
        onDismissRequest = { recordingsToPick = null },
    ) {
        for (recording in recordingsToPick.orEmpty()) {
            PvotSheetItem(
                title = actions.recordingTime(recording),
                onClick = {
                    recordingsToPick = null
                    actions.play(recording)
                },
            )
        }
    }
}

/** The history group's title, as [PvotListGroup] draws one over its card. */
@Composable
private fun CallHistoryTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = PvotListGroupDefaults.colors().titleColor,
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = PvotSpacing.L, end = PvotSpacing.L, bottom = PvotSpacing.S)
                .semantics { heading() },
    )
}

/**
 * One call's slice of the history card: the card's corners (the glass card's radius, the same as
 * `PvotShapes.Card`) on the [first] and [last] slice, a hairline above every slice but the first,
 * and the card's padding round the rows, so the slices stack into the one card a [PvotListGroup]
 * would draw.
 */
@Composable
private fun CallHistorySlice(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val corner = PvotGlassCardDefaults.CornerRadius
    val shape =
        RoundedCornerShape(
            topStart = if (first) corner else 0.dp,
            topEnd = if (first) corner else 0.dp,
            bottomStart = if (last) corner else 0.dp,
            bottomEnd = if (last) corner else 0.dp,
        )
    val direction = LocalLayoutDirection.current
    val inner = PvotListGroupDefaults.ContentPadding
    PvotCard(modifier = Modifier.fillMaxWidth(), shape = shape) {
        Column(
            Modifier.padding(
                start = inner.calculateStartPadding(direction),
                end = inner.calculateEndPadding(direction),
                top = if (first) inner.calculateTopPadding() else 0.dp,
                bottom = if (last) inner.calculateBottomPadding() else 0.dp,
            )
        ) {
            if (!first) {
                Box(
                    Modifier.padding(horizontal = PvotListGroupDefaults.DividerInset)
                        .fillMaxWidth()
                        .height(PvotListGroupDefaults.DividerThickness)
                        .background(PvotListGroupDefaults.colors().dividerColor)
                )
            }
            content()
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
    // Solid: nothing scrolls under the header, so glass here would only be its tint.
    PvotCard(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(PvotSpacing.L)) {
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
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                header.network?.let {
                    // The SIM's own highlight color, as the system shows it.
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            header.networkColor?.let(::Color)
                                ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (header.usedAssistedDialing) {
                    Text(
                        text =
                            if (
                                assistedDialingCountryCode != null && assistedDialingCountryCode > 0
                            ) {
                                stringResource(
                                    R.string.assisted_dialing_country_code_entry,
                                    assistedDialingCountryCode.toString(),
                                )
                            } else {
                                stringResource(R.string.assisted_dialing_country_code_entry_failure)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        // Opens assisted dialing's settings, to change the home country.
                        modifier =
                            Modifier.clickable {
                                context.startActivity(
                                    Intent(context, AssistedDialingSettingActivity::class.java)
                                )
                            },
                    )
                }
                if (header.callbackAction != CallbackAction.NONE) {
                    // The ways to reach them, as one row of labelled round buttons; Call leads.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.L),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val video = header.callbackAction == CallbackAction.IMS_VIDEO
                        PvotCallButton(
                            icon =
                                painterResource(
                                    if (video) R.drawable.ic_video else R.drawable.ic_phone
                                ),
                            label =
                                stringResource(if (video) R.string.video_call else R.string.call),
                            onClick = { actions.callBack(video) },
                            modifier = Modifier.testTag(TestTags.CALL_DETAILS_CALL_BACK),
                            colors =
                                PvotCallButtonDefaults.colors()
                                    .copy(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                        )
                        if (ui.canMessage) {
                            PvotCallButton(
                                icon = painterResource(R.drawable.ic_message),
                                label = stringResource(R.string.call_details_message),
                                onClick = actions::message,
                                modifier = Modifier.testTag(TestTags.CALL_DETAILS_MESSAGE),
                            )
                        }
                        if (!video && callWithIntent != null) {
                            PvotCallButton(
                                icon = painterResource(R.drawable.ic_device_sim),
                                label = stringResource(R.string.pre_call_select_phone_account),
                                onClick = { context.startActivity(callWithIntent) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(
    entry: CallDetailsEntryUi,
    actions: CallDetailsActions,
    onPickRecording: (List<CallRecording>) -> Unit,
) {
    val context = LocalContext.current
    val typeHelper = remember(context) { CallTypeHelper(context.resources) }
    val missed = CallTypeHelper.isMissedCallType(entry.callType)
    val accent =
        if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val duration =
        if (entry.showsDuration) {
            CallLogDurations.formatDurationAndDataUsage(context, entry.duration, entry.dataUsage)
                .toString()
        } else {
            null
        }
    PvotListRow(
        title = {
            Text(
                typeHelper
                    .getCallTypeText(entry.callType, entry.isVideo, entry.isPulledExternally)
                    .toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // The date, then a badge for a Wi-Fi call and one for an HD call.
        supportingText = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
            ) {
                Text(
                    CallLogDates.formatDate(context, entry.date).toString(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.isWifi) EntryBadge(R.drawable.ic_wifi, R.string.description_wifi_call)
                if (entry.isHd) EntryBadge(R.drawable.ic_badge_hd, R.string.description_hd_call)
            }
        },
        modifier = Modifier.testTag(TestTags.callDetailsEntry(entry.callId)),
        leading = {
            // The call's type in a circle, red for a missed call, as the row's one picture.
            Box(
                Modifier.size(PvotListRowDefaults.LeadingSize)
                    .background(
                        if (missed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconFor(entry.callType)),
                    contentDescription = null,
                    tint = if (missed) MaterialTheme.colorScheme.onErrorContainer else accent,
                    modifier = Modifier.size(TypeIconSize),
                )
            }
        },
        trailing = duration?.let { { Text(it, style = MaterialTheme.typography.labelLarge) } },
    )
    val direction = LocalLayoutDirection.current
    // Where the row's title starts: row padding, then the icon and its gap.
    val titleStart =
        PvotListRowDefaults.ContentPadding.calculateStartPadding(direction) +
            PvotListRowDefaults.LeadingSize +
            PvotSpacing.L
    val composer = ComposerDetails.describe(context, entry.isUrgent, entry.composerNote)
    if (composer != null || entry.composerPicture != null) {
        Column(
            Modifier.padding(start = titleStart, end = PvotSpacing.L, bottom = PvotSpacing.S),
            verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
        ) {
            composer?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TestTags.callDetailsComposer(entry.callId)),
                )
            }
            entry.composerPicture?.let {
                UriThumbnail(
                    it.toUri(),
                    stringResource(R.string.call_details_composer_picture),
                    ComposerPictureSize,
                )
            }
        }
    }
    val recordings = entry.recordings
    if (recordings.isNotEmpty() || entry.isRtt) {
        // Line the buttons' text up with the row title, less the text button's own padding.
        val textStart =
            titleStart - PvotButtonDefaults.TextContentPadding.calculateStartPadding(direction)
        FlowRow(
            Modifier.padding(start = textStart, end = PvotSpacing.L, bottom = PvotSpacing.S),
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
        ) {
            if (recordings.isNotEmpty()) {
                PvotButton(
                    text =
                        pluralStringResource(
                            R.plurals.play_recordings,
                            recordings.size,
                            recordings.size,
                        ),
                    style = PvotButtonStyle.Text,
                    onClick = {
                        if (recordings.size == 1) actions.play(recordings.single())
                        else onPickRecording(recordings)
                    },
                    modifier = Modifier.testTag(TestTags.callDetailsRecordings(entry.callId)),
                )
            }
            if (entry.isRtt) {
                if (entry.hasRttTranscript) {
                    PvotButton(
                        stringResource(R.string.rtt_transcript_link),
                        style = PvotButtonStyle.Text,
                        onClick = { actions.showTranscript(entry) },
                    )
                } else {
                    Text(
                        stringResource(R.string.rtt_transcript_not_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun iconFor(callType: Int): Int =
    when (callType) {
        Calls.INCOMING_TYPE,
        Calls.ANSWERED_EXTERNALLY_TYPE -> R.drawable.ic_call_incoming
        Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing
        Calls.VOICEMAIL_TYPE -> R.drawable.ic_call_voicemail
        Calls.BLOCKED_TYPE -> R.drawable.ic_call_blocked
        else -> R.drawable.ic_call_missed
    }

/** Size of the call type icon in its circle. */
private val TypeIconSize = 20.dp

/** Size of a picture sent with a call. */
private val ComposerPictureSize = 120.dp

/** A small icon after a call's date: how the call was carried. */
@Composable
private fun EntryBadge(@DrawableRes icon: Int, @StringRes description: Int) {
    Icon(
        painterResource(icon),
        contentDescription = stringResource(description),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}
