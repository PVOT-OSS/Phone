/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.provider.CallLog.Calls
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotLoadingContent
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotAvatarDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotListRowDefaults
import com.prauga.pvot.designsystem.components.progress.PvotProportionBar
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Locale
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callstats.CallStatsListViewModel.Companion.ALL
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.ui.QuickContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.fullWidthRow

/** What the statistics do when the user acts. */
interface CallStatsActions {
    fun open(entry: CallStatsEntry, total: CallStatsDetails)

    fun copyNumber(number: String)

    fun editBeforeCall(number: String)

    fun pickRange()

    fun clearRange()

    fun sortByDuration(byDuration: Boolean)

    fun turnOnPermission()
}

/**
 * Call statistics: each number with its share of the calls, sorted by duration or count, for an
 * optional time range.
 *
 * @param prepare Fills an entry's contact and display text; called while composing
 */
@Composable
fun CallStatsListScreen(
    state: CallStatsUiState,
    filter: CallStatsFilter,
    prepare: (CallStatsEntry) -> CallStatsDetails,
    actions: CallStatsActions,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    var menuFor by remember { mutableStateOf<CallStatsDetails?>(null) }
    // With calls, the controls are the list's first item and scroll under a bar over the screen;
    // otherwise they and the message start below it.
    Column(
        modifier
            .fillMaxSize()
            .padding(top = if (state is CallStatsUiState.Loaded) 0.dp else topPadding)
    ) {
        if (state !is CallStatsUiState.NeedsPermission && state !is CallStatsUiState.Loaded)
            StatsControls(filter, actions)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (state) {
                CallStatsUiState.Loading -> PvotLoadingContent()
                CallStatsUiState.NeedsPermission ->
                    PvotEmptyContent(
                        message = stringResource(R.string.permission_no_calllog),
                        modifier = Modifier.padding(PvotSpacing.L),
                        icon = {
                            Icon(painterResource(R.drawable.ic_history), contentDescription = null)
                        },
                        actionLabel = stringResource(R.string.permission_single_turn_on),
                        onAction = actions::turnOnPermission,
                    )
                is CallStatsUiState.Empty ->
                    PvotEmptyContent(
                        message =
                            stringResource(
                                if (state.inRange) R.string.recent_calls_no_items_in_range
                                else R.string.call_log_all_empty
                            ),
                        modifier = Modifier.padding(PvotSpacing.L),
                        icon = {
                            Icon(painterResource(R.drawable.ic_history), contentDescription = null)
                        },
                    )
                is CallStatsUiState.Loaded ->
                    StatsList(state, filter, prepare, actions, topPadding) { menuFor = it }
            }
        }
    }
    menuFor?.let { details ->
        PvotBottomSheet(
            visible = true,
            onDismissRequest = { menuFor = null },
            title = details.displayNumber,
        ) {
            PvotSheetItem(
                title = stringResource(R.string.action_copy_number_text),
                icon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
                onClick = {
                    menuFor = null
                    actions.copyNumber(details.number)
                },
            )
            if (
                PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation) &&
                    !PhoneNumberHelper.isSipNumber(details.number)
            ) {
                PvotSheetItem(
                    title = stringResource(R.string.action_edit_number_before_call),
                    icon = {
                        Icon(painterResource(R.drawable.ic_dialpad), contentDescription = null)
                    },
                    onClick = {
                        menuFor = null
                        actions.editBeforeCall(details.number)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatsControls(filter: CallStatsFilter, actions: CallStatsActions) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
    ) {
        PvotSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.call_stats_by_duration),
                    stringResource(R.string.call_stats_by_count),
                ),
            selectedIndex = if (filter.sortByDuration) 0 else 1,
            onSelect = { actions.sortByDuration(it == 0) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.CALL_STATS_SORT),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    if (filter.hasRange) {
                        DateUtils.formatDateRange(context, filter.from, filter.to, 0)
                    } else {
                        stringResource(R.string.call_stats_all_time)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (filter.hasRange) {
                PvotButton(
                    text = stringResource(R.string.call_stats_reset_filter),
                    onClick = actions::clearRange,
                    style = PvotButtonStyle.Text,
                )
            }
            PvotButton(
                text = stringResource(R.string.call_stats_date_filter),
                onClick = actions::pickRange,
                modifier = Modifier.testTag(TestTags.CALL_STATS_RANGE),
                style = PvotButtonStyle.Text,
                icon = painterResource(R.drawable.ic_calendar),
            )
        }
    }
}

@Composable
private fun StatsList(
    state: CallStatsUiState.Loaded,
    filter: CallStatsFilter,
    prepare: (CallStatsEntry) -> CallStatsDetails,
    actions: CallStatsActions,
    topPadding: Dp,
    onLongPress: (CallStatsDetails) -> Unit,
) {
    val context = LocalContext.current
    val byDuration = filter.sortByDuration
    val first = state.entries.first().details
    PvotScreen(
        modifier = Modifier.fillMaxSize().testTag(TestTags.CALL_STATS_LIST),
        contentPadding = PaddingValues(top = topPadding, bottom = PvotSpacing.L),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        item(key = "controls") { StatsControls(filter, actions) }
        item(key = "total") {
            val count =
                CallStatsFormat.callCount(context, state.total.getRequestedCount(ALL).toLong())
            val duration =
                CallStatsFormat.duration(context, state.total.getRequestedDuration(ALL), false)
            Text(
                text = stringResource(R.string.call_stats_header_total, count, duration),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S),
            )
        }
        items(state.entries, key = { it.details.number.orEmpty() }) { entry ->
            val details = prepare(entry)
            val hasName = !details.displayName.isNullOrEmpty()
            Column {
                PvotListRow(
                    title = (if (hasName) details.displayName else details.displayNumber).orEmpty(),
                    modifier =
                        Modifier.fullWidthRow()
                            .testTag(TestTags.callStatsRow(details.number.orEmpty())),
                    supportingText =
                        if (hasName) details.displayNumber
                        else details.geocode?.takeIf { !details.isVoicemailNumber },
                    leading = {
                        // The row opens the statistics, so the picture does not open Quick Contact.
                        QuickContactAvatar(
                            name = details.displayName ?: details.displayNumber.orEmpty(),
                            contactUri = null,
                            photoId = details.photoId,
                            photoUri = details.photoUri,
                            size = PvotAvatarDefaults.Small,
                            colorKey = details.number.orEmpty(),
                        )
                    },
                    trailing = {
                        Text(
                            text =
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f%%",
                                    value(details, byDuration) * 100f /
                                        value(state.total, byDuration),
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { actions.open(entry, state.total) },
                    onLongClick =
                        if (details.number.isNullOrEmpty()) null else ({ onLongPress(details) }),
                )
                ShareBar(details, first, byDuration)
            }
        }
    }
}

/** The number's calls, as a bar as long as its share of the top number, split by call type. */
@Composable
private fun ShareBar(details: CallStatsDetails, first: CallStatsDetails, byDuration: Boolean) {
    val ratio = value(details, byDuration) / value(first, byDuration)
    val full = value(details, byDuration)
    val parts =
        buildList {
                add(
                    Calls.INCOMING_TYPE to
                        value(details, byDuration, Calls.INCOMING_TYPE) * ratio / full
                )
                add(
                    Calls.OUTGOING_TYPE to
                        value(details, byDuration, Calls.OUTGOING_TYPE) * ratio / full
                )
                if (!byDuration) {
                    add(
                        Calls.MISSED_TYPE to value(details, false, Calls.MISSED_TYPE) * ratio / full
                    )
                    add(
                        Calls.BLOCKED_TYPE to
                            value(details, false, Calls.BLOCKED_TYPE) * ratio / full
                    )
                }
            }
            .toMap()
    PvotProportionBar(
        segments = segmentsOf(parts),
        // Lines up with the row's text, after its avatar.
        modifier =
            Modifier.padding(
                start = PvotSpacing.L * 2 + PvotListRowDefaults.LeadingSize,
                end = PvotSpacing.L,
                bottom = PvotSpacing.S,
            ),
        colors = statColors(),
    )
}

private fun value(details: CallStatsDetails, byDuration: Boolean, type: Int = ALL): Float =
    if (byDuration) details.getRequestedDuration(type).toFloat()
    else details.getRequestedCount(type).toFloat()
