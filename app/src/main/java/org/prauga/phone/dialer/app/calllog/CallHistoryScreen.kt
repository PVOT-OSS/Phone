/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotLoadingContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotMenuItem
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.list.PvotLetterTileDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotSwipeRow
import com.prauga.pvot.designsystem.components.list.PvotSwipeRowDefaults
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.compat.telephony.TelephonyManagerCompat
import org.prauga.phone.dialer.reminder.ReminderDelay
import org.prauga.phone.dialer.reminder.ReminderTimeDialog
import org.prauga.phone.dialer.reminder.reminderAt
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.PhoneSnackbarHost
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.fullWidthRow
import org.prauga.phone.dialer.ui.rememberVividColors

/** What a call history list does when the user acts. */
interface CallHistoryActions {
    fun primaryAction(row: CallLogRowUi)

    fun rowAction(row: CallLogRowUi, action: RowAction)

    /** The row was opened; [playNow] when its play button opened it. Marks it as read. */
    fun opened(row: CallLogRowUi, playNow: Boolean = false)

    /** The open row was closed. */
    fun closed(row: CallLogRowUi) = Unit

    /** The row was swiped away: hide it, and delete it unless [undoDelete] follows. */
    fun deleteLater(group: CallLogGroup)

    fun undoDelete(group: CallLogGroup)

    /** Deletes the selected voicemails at once, after the user confirmed. */
    fun deleteSelected(groups: List<CallLogGroup>) = Unit

    /** Voicemails are being selected, or not any more; [exit] ends the selection. */
    fun selectionModeChanged(enabled: Boolean, exit: () -> Unit) = Unit

    fun block(row: CallLogRowUi)

    fun unblock(row: CallLogRowUi)

    fun turnOnPermission()

    /** The empty list's action: open the dialpad. */
    fun emptyAction()

    /** Sets a reminder to call [row] back at [atMillis]. */
    fun remind(row: CallLogRowUi, atMillis: Long) = Unit
}

/**
 * A call history list: calls under Today, Yesterday and Older, grouped by caller. Tapping a call
 * calls back and its info button opens the details; a voicemail opens as a card.
 *
 * @param emptyMessage What to say when there are no calls
 * @param showEmptyAction Whether the empty list offers "Make a call"
 * @param emptyIcon The empty list's icon
 * @param header Shown above the calls, such as a voicemail status message
 * @param fullScreen Shown instead of the list while not null, such as the carrier's terms
 * @param voicemailPlayback The voicemail tab's playback, which shows voicemails as cards
 * @param selectable Whether a long press starts selecting rows to delete together
 */
@Composable
fun CallHistoryScreen(
    state: CallHistoryUiState,
    @StringRes emptyMessage: Int,
    showEmptyAction: Boolean,
    actions: CallHistoryActions,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + PvotSpacing.L,
    @DrawableRes emptyIcon: Int = R.drawable.ic_history,
    header: (@Composable () -> Unit)? = null,
    fullScreen: (@Composable () -> Unit)? = null,
    voicemailPlayback: VoicemailPlaybackState? = null,
    selectable: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Only the list scrolls under a bar over the screen; the other states start below it.
    val belowBar = Modifier.padding(top = topPadding)
    // The Undo snackbar outlives the list: swiping the last row away swaps the list for the empty
    // state, and Undo must still be there to bring the row back.
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.call_history_deleted)
    val undoLabel = stringResource(R.string.snackbar_undo)
    val onDeleted: (CallLogGroup) -> Unit = { group ->
        actions.deleteLater(group)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) actions.undoDelete(group)
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            fullScreen != null -> Box(belowBar) { fullScreen() }
            state is CallHistoryUiState.Loading -> PvotLoadingContent(belowBar)
            state is CallHistoryUiState.NeedsPermission ->
                PvotEmptyContent(
                    message = stringResource(R.string.permission_no_calllog),
                    modifier = belowBar.padding(PvotSpacing.L),
                    icon = { Icon(painterResource(emptyIcon), contentDescription = null) },
                    actionLabel = stringResource(R.string.permission_single_turn_on),
                    onAction = actions::turnOnPermission,
                )
            else -> {
                val loaded = state as? CallHistoryUiState.Loaded
                val visible = loaded?.visible.orEmpty()
                if (visible.isEmpty()) {
                    Column(belowBar.fillMaxSize()) {
                        header?.invoke()
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyHistory(emptyMessage, emptyIcon, showEmptyAction, actions)
                        }
                    }
                } else {
                    HistoryList(
                        groups = visible,
                        rows = loaded?.rows.orEmpty(),
                        actions = actions,
                        onDeleted = onDeleted,
                        header = header,
                        voicemailPlayback = voicemailPlayback,
                        selectable = selectable,
                        listState = listState,
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                    )
                }
            }
        }
        PhoneSnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + PvotSpacing.S),
        )
    }
}

@Composable
private fun EmptyHistory(
    @StringRes message: Int,
    @DrawableRes icon: Int,
    showAction: Boolean,
    actions: CallHistoryActions,
) {
    PvotEmptyContent(
        message = stringResource(message),
        modifier = Modifier.padding(PvotSpacing.L),
        icon = { Icon(painterResource(icon), contentDescription = null) },
        actionLabel = if (showAction) stringResource(R.string.call_log_all_empty_action) else null,
        onAction = if (showAction) actions::emptyAction else null,
    )
}

@Composable
private fun HistoryList(
    groups: List<CallLogGroup>,
    rows: Map<Long, CallLogRowUi>,
    actions: CallHistoryActions,
    onDeleted: (CallLogGroup) -> Unit,
    header: (@Composable () -> Unit)?,
    voicemailPlayback: VoicemailPlaybackState?,
    selectable: Boolean,
    listState: LazyListState,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    var expandedRowId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    // The dialogs name a row by id, so they come back after a rotation with the row's new state.
    var confirmBlockId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remindLaterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmUnblockId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    // Rows vanish once deleted; keep only selections that are still on screen.
    val selection = selected.filterTo(mutableSetOf()) { id -> groups.any { it.rowId == id } }
    val selecting = selection.isNotEmpty()

    fun select(next: Set<Long>) {
        val wasSelecting = selected.isNotEmpty()
        selected = next
        if (wasSelecting != next.isNotEmpty()) {
            actions.selectionModeChanged(next.isNotEmpty()) { selected = emptySet() }
        }
    }
    // Back ends the selection before it leaves the screen.
    BackHandler(enabled = selecting) { select(emptySet()) }

    fun toggle(row: CallLogRowUi) {
        val group = row.group
        if (expandedRowId == group.rowId) {
            expandedRowId = null
            actions.closed(row)
        } else {
            expandedRowId = group.rowId
            actions.opened(row)
        }
    }

    // On a wide window a voicemail opens in a pane beside the list instead of inside it.
    val twoPane =
        voicemailPlayback != null &&
            LocalConfiguration.current.screenWidthDp >= TwoPaneMinWidthDp &&
            groups.isNotEmpty() &&
            groups.all { it.voicemailUri != null }
    val voicemailCard: @Composable (CallLogRowUi, Boolean) -> Unit = { row, expanded ->
        val group = row.group
        VoicemailCard(
            row = row,
            playback = checkNotNull(voicemailPlayback),
            expanded = expanded,
            isSelected = group.rowId in selection,
            onClick = { if (selecting) select(selection.toggled(group.rowId)) else toggle(row) },
            onLongClick =
                if (selectable) {
                    { select(selection + group.rowId) }
                } else null,
            onPlay = {
                expandedRowId = group.rowId
                actions.opened(row, playNow = true)
            },
            onDelete = {
                if (expandedRowId == group.rowId) {
                    expandedRowId = null
                    actions.closed(row)
                }
                onDeleted(group)
            },
            actions = actions,
            // The Delete button removes every call in the row, so it asks first.
            onRowAction = { action: RowAction ->
                when (action) {
                    RowAction.BLOCK -> confirmBlockId = group.rowId
                    RowAction.UNBLOCK -> confirmUnblockId = group.rowId
                    RowAction.DELETE -> confirmDeleteId = group.rowId
                    else -> actions.rowAction(row, action)
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Spacer(Modifier.height(topPadding))
            SelectionBar(
                count = selection.size,
                allSelected = selection.size == groups.size,
                onSelectAll = { all ->
                    select(if (all) groups.map { it.rowId }.toSet() else emptySet())
                },
                onDelete = { confirmDeleteSelected = true },
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            PvotScreen(
                modifier = Modifier.weight(1f).fillMaxHeight().testTag(TestTags.CALL_HISTORY_LIST),
                state = listState,
                contentPadding =
                    PaddingValues(
                        top = (if (selecting) 0.dp else topPadding) + PvotSpacing.S,
                        bottom = bottomPadding,
                    ),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                if (header != null) item(key = "header") { header() }
                // How many headers each day has had so far: calls out of date order can bring a day
                // back, and its second header needs a key of its own.
                val dayHeaders = mutableMapOf<Int, Int>()
                groups.forEachIndexed { index, group ->
                    val previousDay = groups.getOrNull(index - 1)?.dayGroup
                    // An item of its own: when the row under it goes, it stays where it is instead
                    // of
                    // moving into the next row and pushing it down.
                    if (group.dayGroup != previousDay) {
                        val repeat = dayHeaders.merge(group.dayGroup, 1, Int::plus)!! - 1
                        val key =
                            if (repeat == 0) "day_${group.dayGroup}"
                            else "day_${group.dayGroup}_$repeat"
                        item(key = key, contentType = "day") {
                            DayHeader(group.dayGroup, Modifier.animateItem())
                        }
                    }
                    // A row that is not resolved yet (the list moved on) shows with the next state.
                    val row = rows[group.rowId] ?: return@forEachIndexed
                    item(key = group.rowId, contentType = "row") {
                        Column(Modifier.animateItem()) {
                            val expanded = expandedRowId == group.rowId && !selecting
                            val onLongClick: (() -> Unit)? =
                                if (selectable) {
                                    { select(selection + group.rowId) }
                                } else null
                            val onDelete: () -> Unit = {
                                if (expandedRowId == group.rowId) {
                                    expandedRowId = null
                                    actions.closed(row)
                                }
                                onDeleted(group)
                            }
                            if (voicemailPlayback != null && group.voicemailUri != null) {
                                voicemailCard(row, expanded && !twoPane)
                            } else {
                                HistoryRow(
                                    row = row,
                                    isSelected = group.rowId in selection,
                                    // Calling back or opening the details reads a missed call, so
                                    // the tab's badge drops; an emergency number only calls from
                                    // its swipe, never from a stray tap.
                                    onClick = {
                                        when {
                                            selecting -> select(selection.toggled(group.rowId))
                                            row.canCallBack && !row.isEmergency -> {
                                                actions.opened(row)
                                                actions.primaryAction(row)
                                            }
                                            RowAction.DETAILS in row.actions -> {
                                                actions.opened(row)
                                                actions.rowAction(row, RowAction.DETAILS)
                                            }
                                        }
                                    },
                                    onLongClick = onLongClick,
                                    onDetails = {
                                        if (selecting) {
                                            select(selection.toggled(group.rowId))
                                        } else {
                                            actions.opened(row)
                                            actions.rowAction(row, RowAction.DETAILS)
                                        }
                                    },
                                    onCall = {
                                        actions.opened(row)
                                        actions.primaryAction(row)
                                    },
                                    onRemind = { delay ->
                                        actions.remind(row, delay.dueAt(System.currentTimeMillis()))
                                    },
                                    onRemindLater = { remindLaterId = group.rowId },
                                    onDelete = onDelete,
                                )
                            }
                        }
                    }
                }
            }
            if (twoPane) {
                VerticalDivider()
                VoicemailPane(
                    row = expandedRowId?.let(rows::get)?.takeIf { !selecting },
                    topPadding = topPadding,
                    bottomPadding = bottomPadding,
                    card = voicemailCard,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }

    confirmDeleteId?.let(rows::get)?.let { row ->
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmDeleteId = null },
            title = stringResource(R.string.call_log_delete_entry_title),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.delete),
                onClick = {
                    confirmDeleteId = null
                    if (expandedRowId == row.group.rowId) {
                        expandedRowId = null
                        actions.closed(row)
                    }
                    actions.deleteSelected(listOf(row.group))
                },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { confirmDeleteId = null },
            )
        }
    }
    remindLaterId?.let(rows::get)?.let { row ->
        ReminderTimeDialog(
            name = row.title,
            onPick = { time ->
                remindLaterId = null
                actions.remind(row, reminderAt(time, System.currentTimeMillis()))
            },
            onDismiss = { remindLaterId = null },
        )
    }
    confirmBlockId?.let(rows::get)?.let { row ->
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmBlockId = null },
            title = stringResource(R.string.block_number_confirmation_title, row.displayNumber),
            message = stringResource(R.string.block_report_number_alert_details),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.block_number_ok),
                onClick = {
                    confirmBlockId = null
                    actions.block(row)
                },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { confirmBlockId = null },
            )
        }
    }
    confirmUnblockId?.let(rows::get)?.let { row ->
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmUnblockId = null },
            title = stringResource(R.string.unblock_report_number_alert_title, row.displayNumber),
        ) {
            PvotDialogAction(
                label = stringResource(R.string.unblock_number_ok),
                onClick = {
                    confirmUnblockId = null
                    actions.unblock(row)
                },
                style = PvotDialogActionStyle.Primary,
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { confirmUnblockId = null },
            )
        }
    }
    if (confirmDeleteSelected) {
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmDeleteSelected = false },
            title =
                if (voicemailPlayback != null) {
                    pluralStringResource(
                        R.plurals.delete_voicemails_confirmation_dialog_title,
                        selection.size,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.delete_calls_confirmation_dialog_title,
                        selection.size,
                        selection.size,
                    )
                },
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.voicemailMultiSelectDeleteConfirm),
                onClick = {
                    confirmDeleteSelected = false
                    actions.deleteSelected(groups.filter { it.rowId in selection })
                    select(emptySet())
                },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(
                label = stringResource(R.string.voicemailMultiSelectDeleteCancel),
                onClick = { confirmDeleteSelected = false },
            )
        }
    }
}

private fun Set<Long>.toggled(id: Long) = if (id in this) this - id else this + id

/** The open voicemail beside the list on a wide window, or a word on what to do. */
@Composable
private fun VoicemailPane(
    row: CallLogRowUi?,
    topPadding: Dp,
    bottomPadding: Dp,
    card: @Composable (CallLogRowUi, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (row == null) {
        Box(modifier.padding(PvotSpacing.L), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.voicemail_select_to_listen),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding + PvotSpacing.S, bottom = bottomPadding)
            .testTag(TestTags.VOICEMAIL_PANE)
    ) {
        card(row, true)
    }
}

/** How wide a window must be for voicemails to open in a pane beside the list. */
private const val TwoPaneMinWidthDp = 600

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = PvotSpacing.S, vertical = PvotSpacing.XS)
                .testTag(TestTags.CALL_HISTORY_SELECTION),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = allSelected, onCheckedChange = onSelectAll)
        Text(
            text = stringResource(R.string.voicemailMultiSelectActionBarTitle, count.toString()),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        PvotButton(
            text = stringResource(R.string.voicemailMultiSelectDeleteConfirm),
            onClick = onDelete,
            style = PvotButtonStyle.Destructive,
            icon = painterResource(R.drawable.ic_trash),
        )
    }
}

@Composable
private fun DayHeader(dayGroup: Int, modifier: Modifier = Modifier) {
    val title =
        when (dayGroup) {
            CallLogGroupBuilder.DAY_GROUP_TODAY -> R.string.call_log_header_today
            CallLogGroupBuilder.DAY_GROUP_YESTERDAY -> R.string.call_log_header_yesterday
            else -> R.string.call_log_header_other
        }
    PvotSectionHeader(
        title = stringResource(title),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = PvotSpacing.L, end = PvotSpacing.L, top = PvotSpacing.L),
    )
}

/** Whether tapping the row calls back: a call, video call or the voicemail number. */
private val CallLogRowUi.canCallBack: Boolean
    get() =
        primaryAction == PrimaryAction.CALL ||
            primaryAction == PrimaryAction.CALL_IN_APP ||
            primaryAction == PrimaryAction.VIDEO_CALL ||
            primaryAction == PrimaryAction.CALL_VOICEMAIL

/**
 * A call: tapping or swiping toward the end calls back, the info button opens the call details, and
 * swiping toward the start stands the row open on a callback reminder menu and Delete, as on iOS;
 * swiping all the way deletes it, with Undo.
 */
@Composable
private fun HistoryRow(
    row: CallLogRowUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDetails: () -> Unit,
    onCall: () -> Unit,
    onRemind: (ReminderDelay) -> Unit,
    onRemindLater: () -> Unit,
    onDelete: () -> Unit,
) {
    val vivid = rememberVividColors()
    PvotSwipeRow(
        modifier = Modifier.fullWidthRow(),
        containerColor = MaterialTheme.colorScheme.background,
        thresholdFraction = SwipeThresholdFraction,
        dragResistance = SwipeDragResistance,
        startAction =
            if (row.canCallBack) {
                PvotSwipeRowDefaults.action(
                    label = stringResource(R.string.call),
                    icon = painterResource(R.drawable.ic_phone),
                    onSwipe = onCall,
                )
            } else null,
        endButtons =
            listOfNotNull(
                if (row.canCallBack && !row.isEmergency) {
                    PvotSwipeRowDefaults.action(
                        label = stringResource(R.string.callback_reminder_menu_title),
                        icon = painterResource(R.drawable.ic_clock),
                        onSwipe = {},
                        containerColor = vivid.primary,
                        contentColor = vivid.onPrimary,
                        menu =
                            listOf(
                                PvotMenuItem(
                                    stringResource(R.string.callback_reminder_menu_in_an_hour),
                                    { onRemind(ReminderDelay.IN_AN_HOUR) },
                                ),
                                PvotMenuItem(
                                    stringResource(R.string.callback_reminder_menu_tonight),
                                    { onRemind(ReminderDelay.THIS_EVENING) },
                                ),
                                PvotMenuItem(
                                    stringResource(R.string.callback_reminder_menu_tomorrow),
                                    { onRemind(ReminderDelay.TOMORROW_MORNING) },
                                ),
                                PvotMenuItem(
                                    stringResource(R.string.callback_reminder_menu_later),
                                    onRemindLater,
                                ),
                            ),
                    )
                } else null,
                PvotSwipeRowDefaults.destructiveAction(
                    label = stringResource(R.string.call_history_delete),
                    icon = painterResource(R.drawable.ic_trash),
                    onSwipe = onDelete,
                    containerColor = vivid.error,
                    contentColor = vivid.onError,
                ),
            ),
    ) {
        PvotListRow(
            title = {
                Text(
                    text = row.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (row.group.isRead) null else FontWeight.Bold,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            textDirection =
                                if (row.titleIsNumber) TextDirection.Ltr else TextDirection.Content
                        ),
                    color =
                        if (row.group.callType == Calls.MISSED_TYPE && !row.group.isRead) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            },
            modifier = Modifier.testTag(TestTags.callHistoryRow(row.group.rowId)),
            supportingText = { RowDetails(row) },
            leading = {
                if (isSelected) {
                    SelectedTile()
                } else {
                    ContactAvatar(
                        name = row.title,
                        photoUri = row.info.photoUri?.toString(),
                        colorKey = row.info.lookupKey ?: row.group.number,
                    )
                }
            },
            trailing = {
                if (RowAction.DETAILS in row.actions) {
                    PvotIconButton(
                        icon = painterResource(R.drawable.ic_info_small),
                        contentDescription = stringResource(R.string.call_log_action_details),
                        onClick = onDetails,
                        size = PvotIconButtonDefaults.SmallSize,
                        iconSize = PvotIconButtonDefaults.IconSize,
                        colors =
                            PvotIconButtonDefaults.tonalColors()
                                .copy(contentColor = MaterialTheme.colorScheme.primary),
                        modifier =
                            Modifier.testTag(
                                TestTags.callHistoryAction(row.group.rowId, RowAction.DETAILS.name)
                            ),
                    )
                }
            },
            onClick = onClick,
            onLongClick = onLongClick,
            selected = isSelected,
        )
    }
}

// A recents row calls or deletes when swiped, so it takes a firmer, longer swipe than the default.
private const val SwipeThresholdFraction = 0.45f
private const val SwipeDragResistance = 0.75f

/** A check in place of a selected row's avatar. */
@Composable
internal fun SelectedTile(size: Dp = PvotLetterTileDefaults.Size) {
    Box(
        modifier = Modifier.size(size).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
internal fun labelAndIcon(action: RowAction, row: CallLogRowUi): Pair<String, Int> =
    when (action) {
        RowAction.CALL ->
            TextUtils.expandTemplate(stringResource(R.string.call_log_action_call), row.title)
                .toString() to R.drawable.ic_phone
        RowAction.VIDEO_CALL ->
            stringResource(R.string.call_log_action_video_call) to R.drawable.ic_video
        RowAction.SEND_MESSAGE ->
            stringResource(R.string.call_log_action_send_message) to R.drawable.ic_message
        RowAction.CALL_WITH_NOTE ->
            stringResource(R.string.call_with_a_note) to R.drawable.ic_call_note
        RowAction.REMIND -> stringResource(R.string.callback_reminder_action) to R.drawable.ic_bell
        RowAction.CREATE_CONTACT ->
            stringResource(R.string.search_shortcut_create_new_contact) to R.drawable.ic_user_plus
        RowAction.ADD_TO_CONTACT ->
            stringResource(R.string.search_shortcut_add_to_contact) to R.drawable.ic_user
        RowAction.BLOCK ->
            stringResource(R.string.call_log_action_block_number) to R.drawable.ic_ban
        RowAction.UNBLOCK ->
            stringResource(R.string.call_log_action_unblock_number) to R.drawable.ic_ban
        RowAction.DETAILS ->
            stringResource(R.string.call_log_action_details) to R.drawable.ic_info_circle
        RowAction.DELETE ->
            stringResource(R.string.voicemailMultiSelectDeleteConfirm) to R.drawable.ic_trash
    }

@Composable
private fun RowDetails(row: CallLogRowUi) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CallTypeIcons(row.group)
            Text(
                text = row.details,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (row.group.isRead) null else FontWeight.Bold,
                color =
                    if (row.group.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
        row.accountLabel?.let { label ->
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = row.accountColor?.let(::Color) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Up to three call type icons, newest first, then the call's quality badges in a fixed order:
 * Wi-Fi, HD, assisted dialing. Video and RTT calls say so in the row's text instead.
 */
@Composable
private fun CallTypeIcons(group: CallLogGroup) {
    Row(
        modifier = Modifier.padding(end = PvotSpacing.XS),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (type in group.callTypes.take(3)) {
            val (icon, description) = callTypeIcon(type)
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(description),
                tint =
                    if (type == Calls.MISSED_TYPE || type == Calls.REJECTED_TYPE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(CallTypeIconSize),
            )
        }
        for ((icon, description) in qualityBadges(group.features)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CallTypeIconSize),
            )
        }
    }
}

/**
 * The badges for a call's [features]: icon and description, Wi-Fi first, then HD, then assisted
 * dialing.
 */
internal fun qualityBadges(features: Int): List<Pair<Int, Int>> = buildList {
    if (features and Calls.FEATURES_WIFI == Calls.FEATURES_WIFI)
        add(R.drawable.ic_wifi to R.string.description_wifi_call)
    if (features and Calls.FEATURES_HD_CALL == Calls.FEATURES_HD_CALL)
        add(R.drawable.ic_badge_hd to R.string.description_hd_call)
    if (
        features and TelephonyManagerCompat.FEATURES_ASSISTED_DIALING ==
            TelephonyManagerCompat.FEATURES_ASSISTED_DIALING
    ) {
        add(R.drawable.ic_world to R.string.description_assisted_dialing)
    }
}

/** Size of the call type icons and badges on a row's second line. */
private val CallTypeIconSize = 16.dp

private fun callTypeIcon(type: Int): Pair<Int, Int> =
    when (type) {
        Calls.INCOMING_TYPE,
        Calls.ANSWERED_EXTERNALLY_TYPE -> R.drawable.ic_call_incoming to R.string.type_incoming
        Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing to R.string.type_outgoing
        Calls.VOICEMAIL_TYPE -> R.drawable.ic_call_voicemail to R.string.type_voicemail
        Calls.BLOCKED_TYPE -> R.drawable.ic_call_blocked to R.string.type_blocked
        else -> R.drawable.ic_call_missed to R.string.type_missed
    }
