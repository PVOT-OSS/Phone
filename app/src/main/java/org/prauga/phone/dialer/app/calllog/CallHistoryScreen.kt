/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotLoadingContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotSnackbarHost
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotFabDefaults
import com.prauga.pvot.designsystem.components.list.PvotLetterTileDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotSwipeRow
import com.prauga.pvot.designsystem.components.list.PvotSwipeRowDefaults
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.TestTags

/** What a call history list does when the user acts. */
interface CallHistoryActions {
    fun primaryAction(row: CallLogRowUi)
    fun rowAction(row: CallLogRowUi, action: RowAction)

    /** The row was opened; [playNow] when its play button opened it. Marks it as read. */
    fun opened(row: CallLogRowUi, playNow: Boolean = false)

    /** The open row was closed. */
    fun closed(row: CallLogRowUi) = Unit

    fun deleteLater(group: CallLogGroup)
    fun undoDelete(group: CallLogGroup)
    fun commitDelete(group: CallLogGroup)

    /** Deletes the selected voicemails at once, after the user confirmed. */
    fun deleteSelected(groups: List<CallLogGroup>) = Unit

    /** Voicemails are being selected, or not any more; [exit] ends the selection. */
    fun selectionModeChanged(enabled: Boolean, exit: () -> Unit) = Unit

    fun block(row: CallLogRowUi)
    fun unblock(row: CallLogRowUi)
    fun turnOnPermission()

    /** The empty list's action: open the dialpad. */
    fun emptyAction()
}

/**
 * A call history list: calls under Today, Yesterday and Older, grouped by caller. Tapping a row
 * opens its actions; swiping it calls back or deletes it, with Undo.
 *
 * @param resolve Turns a group into its row; called while composing, so it must be quick
 * @param emptyMessage What to say when there are no calls
 * @param showEmptyAction Whether the empty list offers "Make a call"
 * @param emptyIcon The empty list's icon
 * @param header Shown above the calls, such as a voicemail status message
 * @param fullScreen Shown instead of the list while not null, such as the carrier's terms
 * @param openRowContent Shown in an open row above its actions, such as voicemail playback
 * @param selectable Whether a long press starts selecting rows to delete together
 */
@Composable
fun CallHistoryScreen(
    state: CallHistoryUiState,
    pendingDeletes: Set<Long>,
    resolve: (group: CallLogGroup, index: Int, isBlocked: Boolean) -> CallLogRowUi,
    @StringRes emptyMessage: Int,
    showEmptyAction: Boolean,
    actions: CallHistoryActions,
    modifier: Modifier = Modifier,
    @DrawableRes emptyIcon: Int = R.drawable.ic_history,
    header: (@Composable () -> Unit)? = null,
    fullScreen: (@Composable () -> Unit)? = null,
    openRowContent: (@Composable (CallLogRowUi) -> Unit)? = null,
    selectable: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            fullScreen != null -> fullScreen()
            state is CallHistoryUiState.Loading -> PvotLoadingContent()
            state is CallHistoryUiState.NeedsPermission -> PvotEmptyContent(
                message = stringResource(R.string.permission_no_calllog),
                modifier = Modifier.padding(PvotSpacing.L),
                icon = { Icon(painterResource(emptyIcon), contentDescription = null) },
                actionLabel = stringResource(R.string.permission_single_turn_on),
                onAction = actions::turnOnPermission,
            )
            else -> {
                val loaded = state as? CallHistoryUiState.Loaded
                val visible = loaded?.groups.orEmpty().filter { group -> group.callIds.none { it in pendingDeletes } }
                if (visible.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        header?.invoke()
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyHistory(emptyMessage, emptyIcon, showEmptyAction, actions)
                        }
                    }
                } else {
                    HistoryList(
                        groups = visible,
                        blockedNumbers = loaded?.blockedNumbers.orEmpty(),
                        resolve = resolve,
                        actions = actions,
                        header = header,
                        openRowContent = openRowContent,
                        selectable = selectable,
                        listState = listState,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
        PvotSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PvotFabDefaults.Size + PvotSpacing.L * 2),
        )
    }
}

@Composable
private fun EmptyHistory(@StringRes message: Int, @DrawableRes icon: Int, showAction: Boolean, actions: CallHistoryActions) {
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
    blockedNumbers: Set<String>,
    resolve: (CallLogGroup, Int, Boolean) -> CallLogRowUi,
    actions: CallHistoryActions,
    header: (@Composable () -> Unit)?,
    openRowContent: (@Composable (CallLogRowUi) -> Unit)?,
    selectable: Boolean,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
) {
    var expandedRowId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var confirmBlock by remember { mutableStateOf<CallLogRowUi?>(null) }
    var confirmUnblock by remember { mutableStateOf<CallLogRowUi?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.call_history_deleted)
    val undoLabel = stringResource(R.string.snackbar_undo)
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

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selection.size,
                allSelected = selection.size == groups.size,
                onSelectAll = { all -> select(if (all) groups.map { it.rowId }.toSet() else emptySet()) },
                onDelete = { confirmDeleteSelected = true },
            )
        }
        PvotScreen(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(TestTags.CALL_HISTORY_LIST),
            state = listState,
            // The bottom clears the dialpad button that floats over the list.
            contentPadding = PaddingValues(top = PvotSpacing.S, bottom = PvotFabDefaults.Size + PvotSpacing.L * 2),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            if (header != null) item(key = "header") { header() }
            itemsIndexed(groups, key = { _, group -> group.rowId }) { index, group ->
                val previousDay = groups.getOrNull(index - 1)?.dayGroup
                Column(Modifier.animateItem()) {
                    if (group.dayGroup != previousDay) DayHeader(group.dayGroup)
                    val row = resolve(group, index, group.number in blockedNumbers)
                    key(group.rowId) {
                        HistoryRow(
                            row = row,
                            expanded = expandedRowId == group.rowId && !selecting,
                            isSelected = group.rowId in selection,
                            onClick = {
                                if (selecting) select(selection.toggled(group.rowId)) else toggle(row)
                            },
                            onLongClick = if (selectable && group.voicemailUri != null) {
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
                                actions.deleteLater(group)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        actions.undoDelete(group)
                                    } else {
                                        actions.commitDelete(group)
                                    }
                                }
                            },
                            actions = actions,
                            openRowContent = openRowContent,
                            onBlock = { confirmBlock = row },
                            onUnblock = { confirmUnblock = row },
                        )
                    }
                }
            }
        }
    }

    confirmBlock?.let { row ->
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmBlock = null },
            title = stringResource(R.string.block_number_confirmation_title, row.displayNumber),
            message = stringResource(R.string.block_report_number_alert_details),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.block_number_ok),
                onClick = { confirmBlock = null; actions.block(row) },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = { confirmBlock = null })
        }
    }
    confirmUnblock?.let { row ->
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmUnblock = null },
            title = stringResource(R.string.unblock_report_number_alert_title, row.displayNumber),
        ) {
            PvotDialogAction(
                label = stringResource(R.string.unblock_number_ok),
                onClick = { confirmUnblock = null; actions.unblock(row) },
                style = PvotDialogActionStyle.Primary,
            )
            PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = { confirmUnblock = null })
        }
    }
    if (confirmDeleteSelected) {
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmDeleteSelected = false },
            title = pluralStringResource(R.plurals.delete_voicemails_confirmation_dialog_title, selection.size),
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

@Composable
private fun SelectionBar(count: Int, allSelected: Boolean, onSelectAll: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
private fun DayHeader(dayGroup: Int) {
    val title = when (dayGroup) {
        CallLogGroupBuilder.DAY_GROUP_TODAY -> R.string.call_log_header_today
        CallLogGroupBuilder.DAY_GROUP_YESTERDAY -> R.string.call_log_header_yesterday
        else -> R.string.call_log_header_other
    }
    PvotSectionHeader(
        title = stringResource(title),
        modifier = Modifier.fillMaxWidth().padding(start = PvotSpacing.L, end = PvotSpacing.L, top = PvotSpacing.L),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(
    row: CallLogRowUi,
    expanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    actions: CallHistoryActions,
    openRowContent: (@Composable (CallLogRowUi) -> Unit)?,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val canSwipeCall = row.primaryAction == PrimaryAction.CALL || row.primaryAction == PrimaryAction.VIDEO_CALL ||
        row.primaryAction == PrimaryAction.CALL_VOICEMAIL
    Column {
        PvotSwipeRow(
            startAction = if (canSwipeCall) {
                PvotSwipeRowDefaults.action(
                    label = stringResource(R.string.call),
                    icon = painterResource(R.drawable.ic_phone),
                    onSwipe = { actions.primaryAction(row) },
                )
            } else null,
            endAction = PvotSwipeRowDefaults.destructiveAction(
                label = stringResource(R.string.call_history_delete),
                icon = painterResource(R.drawable.ic_trash),
                onSwipe = onDelete,
            ),
        ) {
            PvotListRow(
                title = {
                    Text(
                        text = row.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (row.group.isRead) null else FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = if (row.titleIsNumber) TextDirection.Ltr else TextDirection.Content,
                        ),
                        color = if (row.group.callType == Calls.MISSED_TYPE && !row.group.isRead) {
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
                trailing = if (expanded && row.primaryAction == PrimaryAction.PLAY_VOICEMAIL) {
                    null
                } else {
                    { PrimaryButton(row, onPlay, actions) }
                },
                onClick = onClick,
                onLongClick = onLongClick,
                selected = expanded || isSelected,
            )
        }
        AnimatedVisibility(
            visible = expanded && (row.actions.isNotEmpty() || openRowContent != null),
            enter = expandVertically(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) + fadeIn(tween(PvotDurations.Medium)),
            exit = shrinkVertically(tween(PvotDurations.Short)) + fadeOut(tween(PvotDurations.Short)),
        ) {
            Column(Modifier.padding(start = PvotSpacing.L, end = PvotSpacing.L, bottom = PvotSpacing.M)) {
                openRowContent?.invoke(row)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.callHistoryActions(row.group.rowId)),
                    horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                ) {
                    for (action in row.actions) {
                        val (label, icon) = labelAndIcon(action, row)
                        PvotButton(
                            text = label,
                            onClick = {
                                when (action) {
                                    RowAction.BLOCK -> onBlock()
                                    RowAction.UNBLOCK -> onUnblock()
                                    RowAction.DELETE -> onDelete()
                                    else -> actions.rowAction(row, action)
                                }
                            },
                            modifier = Modifier.testTag(TestTags.callHistoryAction(row.group.rowId, action.name)),
                            style = PvotButtonStyle.Tonal,
                            icon = painterResource(icon),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedTile() {
    Box(
        modifier = Modifier
            .size(PvotLetterTileDefaults.Size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun labelAndIcon(action: RowAction, row: CallLogRowUi): Pair<String, Int> = when (action) {
    RowAction.CALL -> TextUtils.expandTemplate(stringResource(R.string.call_log_action_call), row.title).toString() to R.drawable.ic_phone
    RowAction.VIDEO_CALL -> stringResource(R.string.call_log_action_video_call) to R.drawable.ic_video
    RowAction.SEND_MESSAGE -> stringResource(R.string.call_log_action_send_message) to R.drawable.ic_message
    RowAction.CALL_WITH_NOTE -> stringResource(R.string.call_with_a_note) to R.drawable.ic_call_note
    RowAction.CREATE_CONTACT -> stringResource(R.string.search_shortcut_create_new_contact) to R.drawable.ic_user_plus
    RowAction.ADD_TO_CONTACT -> stringResource(R.string.search_shortcut_add_to_contact) to R.drawable.ic_user
    RowAction.BLOCK -> stringResource(R.string.call_log_action_block_number) to R.drawable.ic_ban
    RowAction.UNBLOCK -> stringResource(R.string.call_log_action_unblock_number) to R.drawable.ic_ban
    RowAction.DETAILS -> stringResource(R.string.call_log_action_details) to R.drawable.ic_info_circle
    RowAction.DELETE -> stringResource(R.string.voicemailMultiSelectDeleteConfirm) to R.drawable.ic_trash
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
                color = if (row.group.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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

/** Up to three call type icons, newest first, then a video icon for a video call. */
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
                tint = if (type == Calls.MISSED_TYPE || type == Calls.REJECTED_TYPE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
        }
        if (group.features and Calls.FEATURES_VIDEO == Calls.FEATURES_VIDEO) {
            Icon(
                painter = painterResource(R.drawable.ic_video),
                contentDescription = stringResource(R.string.description_video_call),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun callTypeIcon(type: Int): Pair<Int, Int> = when (type) {
    Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> R.drawable.ic_call_incoming to R.string.type_incoming
    Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing to R.string.type_outgoing
    Calls.VOICEMAIL_TYPE -> R.drawable.ic_call_voicemail to R.string.type_voicemail
    Calls.BLOCKED_TYPE -> R.drawable.ic_call_blocked to R.string.type_blocked
    else -> R.drawable.ic_call_missed to R.string.type_missed
}

@Composable
private fun PrimaryButton(row: CallLogRowUi, onPlay: () -> Unit, actions: CallHistoryActions) {
    @DrawableRes val icon: Int
    @StringRes val description: Int
    when (row.primaryAction) {
        PrimaryAction.NONE -> return
        PrimaryAction.VIDEO_CALL -> { icon = R.drawable.ic_video; description = R.string.description_video_call_action }
        PrimaryAction.PLAY_VOICEMAIL -> { icon = R.drawable.ic_player_play; description = R.string.description_voicemail_action }
        PrimaryAction.CALL, PrimaryAction.CALL_VOICEMAIL -> { icon = R.drawable.ic_phone; description = R.string.description_call_action }
    }
    IconButton(
        onClick = { if (row.primaryAction == PrimaryAction.PLAY_VOICEMAIL) onPlay() else actions.primaryAction(row) },
        modifier = Modifier.testTag(TestTags.callHistoryPrimary(row.group.rowId)),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = TextUtils.expandTemplate(stringResource(description), row.title).toString(),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
