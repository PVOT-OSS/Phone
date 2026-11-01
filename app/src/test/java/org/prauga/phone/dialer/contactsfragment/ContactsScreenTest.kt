/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.contactsfragment

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The contacts tab. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class ContactsScreenTest {

    @get:Rule val compose = createComposeRule()

    private class RecordingActions : ContactsActions {
        val events = mutableListOf<String>()
        override fun onContactClick(contact: ContactEntry) { events += "open ${contact.name}" }
        override fun onAddContact() { events += "add" }
        override fun onTurnOnPermission() { events += "permission" }
    }

    private val actions = RecordingActions()

    private val few = listOf(
        ContactSection("A", listOf(ContactEntry(1, "k1", "Ada Lovelace", null), ContactEntry(2, "k2", "Alan Turing", null))),
        ContactSection("G", listOf(ContactEntry(3, "k3", "Grace Hopper", null))),
        ContactSection("K", listOf(ContactEntry(4, "k4", "Katherine Johnson", null))),
    )

    private val many = ('A'..'Z').mapIndexed { s, letter ->
        ContactSection(letter.toString(), (1..5).map { ContactEntry(s * 10L + it, "k$s$it", "$letter-name $it", null) })
    }

    private fun show(state: ContactsUiState, listState: LazyListState = LazyListState()) {
        compose.setContent {
            PhoneScreenTheme { ContactsScreen(state, showAddContact = true, actions = actions, listState = listState) }
        }
    }

    @Test
    fun tappingAContactOpensIt() {
        show(ContactsUiState.Loaded(few))
        compose.onNodeWithTag(TestTags.contactRow(3)).performClick()
        assertThat(actions.events).containsExactly("open Grace Hopper")
    }

    @Test
    fun addContactRowAddsAContact() {
        show(ContactsUiState.Loaded(few))
        compose.onNodeWithTag(TestTags.CONTACTS_ADD).performClick()
        assertThat(actions.events).containsExactly("add")
    }

    @Test
    fun sectionsHaveHeaders() {
        show(ContactsUiState.Loaded(few))
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("G").assertExists()
    }

    @Test
    fun missingPermissionOffersToTurnItOn() {
        show(ContactsUiState.NeedsPermission)
        compose.onNodeWithText("To see your contacts, turn on the Contacts permission.").assertExists()
        compose.onNodeWithText("Turn on").performClick()
        assertThat(actions.events).containsExactly("permission")
    }

    @Test
    fun noContactsOffersToAddOne() {
        show(ContactsUiState.Empty)
        compose.onNodeWithText("You don't have any contacts yet").assertExists()
        compose.onNodeWithText("Create new contact").performClick()
        assertThat(actions.events).containsExactly("add")
    }

    @Test
    fun draggingTheEdgeJumpsDownTheList() {
        val listState = LazyListState()
        show(ContactsUiState.Loaded(many), listState)

        compose.onNodeWithTag(TestTags.CONTACTS_LIST).performTouchInput {
            swipe(Offset(right - 10f, top + 10f), Offset(right - 10f, bottom - 10f), durationMillis = 400)
        }
        compose.waitForIdle()

        assertThat(listState.firstVisibleItemIndex).isGreaterThan(100)
    }

    @Test
    fun screenshotDark() {
        show(ContactsUiState.Loaded(few))
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/contacts/contacts_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show(ContactsUiState.Loaded(few))
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/contacts/contacts_light.png")
    }

    @Test
    fun screenshotPermission() {
        show(ContactsUiState.NeedsPermission)
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/contacts/permission_dark.png")
    }
}
