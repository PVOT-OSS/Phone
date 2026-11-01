/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.searchfragment.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotLetterTileDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.fullWidthRow

/** What the search results do when the user acts. */
interface SearchActions {
    fun call(number: String, position: Int)
    fun videoCall(number: String, position: Int)
    fun addToContact(number: String)
    fun createContact(number: String)
    fun sendSms(number: String)
    fun allowLocation()
    fun dismissLocation()
    fun turnOnPermission()

    /** A finger went down on the list, which hides the dialpad or the keyboard. */
    fun listTouched()
}

/**
 * Search results: contacts, directory contacts and nearby places matching the query, then
 * shortcuts for the typed number.
 *
 * @param needsPermission Whether contacts cannot be read, which asks for the permission instead
 */
@Composable
fun SearchScreen(rows: List<SearchRow>, needsPermission: Boolean, actions: SearchActions, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(actions) {
                // Watches the first touch without taking it from the rows.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    actions.listTouched()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (needsPermission) {
            PvotEmptyContent(
                message = stringResource(R.string.new_permission_no_search),
                modifier = Modifier.padding(PvotSpacing.L),
                icon = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
                actionLabel = stringResource(R.string.permission_single_turn_on),
                onAction = actions::turnOnPermission,
            )
            return@Box
        }
        PvotScreen(
            modifier = Modifier.fillMaxSize().testTag(TestTags.SEARCH_LIST),
            contentPadding = PaddingValues(vertical = PvotSpacing.S),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is SearchRow.Header -> PvotSectionHeader(
                        title = row.text,
                        modifier = Modifier.fillMaxWidth().padding(start = PvotSpacing.L, end = PvotSpacing.L, top = PvotSpacing.M),
                    )
                    is SearchRow.Number -> NumberRow(row, actions)
                    is SearchRow.Action -> ActionRow(row, actions)
                    SearchRow.LocationRequest -> LocationRequest(actions)
                }
            }
        }
    }
}

@Composable
private fun NumberRow(row: SearchRow.Number, actions: SearchActions) {
    PvotListRow(
        title = {
            Text(
                text = row.name ?: row.details,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (row.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = Modifier.fullWidthRow().testTag(TestTags.searchRow(row.key)),
        supportingText = if (row.name != null && row.details.isNotEmpty()) {
            { Text(text = row.details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        leading = {
            if (row.name != null) {
                Box {
                    ContactAvatar(name = row.name.text, photoUri = row.photoUri, colorKey = row.colorKey)
                    if (row.isWork) {
                        Icon(
                            painter = painterResource(R.drawable.ic_briefcase),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                        )
                    }
                }
            } else {
                // A contact's second number lines up under the first.
                Spacer(Modifier.size(PvotLetterTileDefaults.Size))
            }
        },
        trailing = if (row.canVideoCall) {
            {
                IconButton(onClick = { actions.videoCall(row.number, row.position) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video),
                        contentDescription = stringResource(R.string.description_search_video_call),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else null,
        onClick = { actions.call(row.number, row.position) },
    )
}

@Composable
private fun ActionRow(row: SearchRow.Action, actions: SearchActions) {
    val (label, icon) = when (row.action) {
        SearchAction.ADD_TO_CONTACT -> stringResource(R.string.search_shortcut_add_to_contact) to R.drawable.ic_user
        SearchAction.CREATE_NEW_CONTACT -> stringResource(R.string.search_shortcut_create_new_contact) to R.drawable.ic_user_plus
        SearchAction.MAKE_VILTE_CALL -> stringResource(R.string.search_shortcut_make_video_call) to R.drawable.ic_video
        SearchAction.SEND_SMS -> stringResource(R.string.search_shortcut_send_sms_message) to R.drawable.ic_message
        else -> stringResource(R.string.search_shortcut_make_voice_call, row.number) to R.drawable.ic_phone
    }
    PvotListRow(
        title = label,
        modifier = Modifier.fullWidthRow().testTag(TestTags.searchAction(row.action)),
        leading = {
            Box(
                modifier = Modifier
                    .size(PvotLetterTileDefaults.Size)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        onClick = {
            when (row.action) {
                SearchAction.ADD_TO_CONTACT -> actions.addToContact(row.number)
                SearchAction.CREATE_NEW_CONTACT -> actions.createContact(row.number)
                SearchAction.MAKE_VILTE_CALL -> actions.videoCall(row.number, row.position)
                SearchAction.SEND_SMS -> actions.sendSms(row.number)
                else -> actions.call(row.number, row.position)
            }
        },
    )
}

@Composable
private fun LocationRequest(actions: SearchActions) {
    PvotCard(modifier = Modifier.fillMaxWidth().padding(PvotSpacing.L)) {
        Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
            Text(text = stringResource(R.string.search_location_permission), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
                PvotButton(text = stringResource(R.string.nearby_places_dismiss), onClick = actions::dismissLocation, style = PvotButtonStyle.Text)
                PvotButton(text = stringResource(R.string.nearby_places_allow), onClick = actions::allowLocation, style = PvotButtonStyle.Filled)
            }
        }
    }
}

/** Puts the search results into the search fragment's view. */
object SearchScreenHost {
    @JvmStatic
    fun attach(view: ComposeView, results: SearchResults, actions: SearchActions) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            PhoneScreenTheme { SearchScreen(results.rows, results.needsPermission, actions) }
        }
    }
}
