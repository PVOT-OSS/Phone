/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.contactsfragment

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotLoadingContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.list.PvotFastScrollSection
import com.prauga.pvot.designsystem.components.list.PvotFastScroller
import com.prauga.pvot.designsystem.components.list.PvotLetterTileDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.dockClearance
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.fullWidthRow

/** What the contacts tab does when the user taps something. */
interface ContactsActions {
    fun onContactClick(contact: ContactEntry)
    fun onAddContact()
    fun onTurnOnPermission()
}

/**
 * The contacts tab: every contact under sticky index letters, with a fast scroller, after an
 * optional "Create new contact" row.
 */
@Composable
fun ContactsScreen(
    state: ContactsUiState,
    showAddContact: Boolean,
    actions: ContactsActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            ContactsUiState.Loading -> PvotLoadingContent()
            ContactsUiState.NeedsPermission -> PvotEmptyContent(
                message = stringResource(R.string.permission_no_contacts),
                modifier = Modifier.padding(PvotSpacing.L),
                icon = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
                actionLabel = stringResource(R.string.permission_single_turn_on),
                onAction = actions::onTurnOnPermission,
            )
            ContactsUiState.Empty -> PvotEmptyContent(
                message = stringResource(R.string.all_contacts_empty),
                modifier = Modifier.padding(PvotSpacing.L),
                icon = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
                actionLabel = stringResource(R.string.all_contacts_empty_add_contact_action),
                onAction = actions::onAddContact,
            )
            is ContactsUiState.Loaded -> ContactsList(state.sections, showAddContact, actions, listState)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsList(
    sections: List<ContactSection>,
    showAddContact: Boolean,
    actions: ContactsActions,
    listState: LazyListState,
) {
    val scrollSections = remember(sections, showAddContact) { scrollSectionsOf(sections, showAddContact) }
    Box(Modifier.fillMaxSize()) {
        PvotScreen(
            modifier = Modifier.fillMaxSize().testTag(TestTags.CONTACTS_LIST),
            state = listState,
            // The bottom clears the dock that floats over the list.
            contentPadding = PaddingValues(
                top = PvotSpacing.S,
                bottom = dockClearance(),
            ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            if (showAddContact) {
                item(key = "add_contact") {
                    PvotListRow(
                        title = stringResource(R.string.all_contacts_empty_add_contact_action),
                        modifier = Modifier.fullWidthRow().testTag(TestTags.CONTACTS_ADD),
                        leading = { AddContactTile() },
                        onClick = actions::onAddContact,
                    )
                }
            }
            sections.forEachIndexed { index, section ->
                stickyHeader(key = "header_$index", contentType = "header") {
                    PvotSectionHeader(
                        title = section.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.XS),
                    )
                }
                items(section.contacts, key = { it.id }, contentType = { "contact" }) { contact ->
                    PvotListRow(
                        title = contact.name,
                        modifier = Modifier.fullWidthRow().testTag(TestTags.contactRow(contact.id)),
                        leading = {
                            ContactAvatar(
                                name = contact.name,
                                photoUri = contact.photoUri,
                                colorKey = contact.lookupKey ?: contact.id,
                            )
                        },
                        onClick = { actions.onContactClick(contact) },
                    )
                }
            }
        }
        PvotFastScroller(
            state = listState,
            sections = scrollSections,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun AddContactTile() {
    Box(
        modifier = Modifier
            .size(PvotLetterTileDefaults.Size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_user_plus),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The list index of each section's header, counting the optional add contact row. */
internal fun scrollSectionsOf(sections: List<ContactSection>, showAddContact: Boolean): List<PvotFastScrollSection> {
    var index = if (showAddContact) 1 else 0
    return sections.map { section ->
        PvotFastScrollSection(section.label, index).also { index += 1 + section.contacts.size }
    }
}
