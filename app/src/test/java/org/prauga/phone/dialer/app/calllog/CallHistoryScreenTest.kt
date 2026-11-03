/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A call history list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class CallHistoryScreenTest {

    @get:Rule val compose = createComposeRule()

    private val missed = callLogGroup(1, "+15550100", listOf(Calls.MISSED_TYPE, Calls.MISSED_TYPE), isRead = false)
    private val incoming = callLogGroup(10, "+15550101")
    private val older = callLogGroup(20, "+15550102", listOf(Calls.OUTGOING_TYPE), dayGroup = CallLogGroupBuilder.DAY_GROUP_OTHER)
    private val rows = mapOf(
        1L to row(missed, "Ada Lovelace", details = "(2) Mobile, 2 mins ago"),
        10L to row(incoming, actions = listOf(RowAction.SEND_MESSAGE, RowAction.BLOCK, RowAction.DETAILS)),
        20L to row(older, "Grace Hopper", details = "Work, Mar 3"),
    )

    private var state by mutableStateOf<CallHistoryUiState>(CallHistoryUiState.Loaded(listOf(missed, incoming, older), emptySet()))

    private fun hide(change: (Set<Long>) -> Set<Long>) {
        val loaded = state as CallHistoryUiState.Loaded
        state = loaded.copy(pendingDeletes = change(loaded.pendingDeletes))
    }

    private inner class RecordingActions : CallHistoryActions {
        val events = mutableListOf<String>()
        override fun primaryAction(row: CallLogRowUi) { events += "call ${row.group.rowId}" }
        override fun rowAction(row: CallLogRowUi, action: RowAction) { events += "${action.name} ${row.group.rowId}" }
        override fun opened(row: CallLogRowUi, playNow: Boolean) { events += "opened ${row.group.rowId}${if (playNow) " play" else ""}" }
        override fun closed(row: CallLogRowUi) { events += "closed ${row.group.rowId}" }
        override fun deleteSelected(groups: List<CallLogGroup>) { events += "deleteSelected ${groups.map { it.rowId }}" }
        override fun selectionModeChanged(enabled: Boolean, exit: () -> Unit) { events += "selecting $enabled" }
        override fun deleteLater(group: CallLogGroup) { events += "delete ${group.rowId}"; hide { it + group.callIds } }
        override fun undoDelete(group: CallLogGroup) { events += "undo ${group.rowId}"; hide { it - group.callIds.toSet() } }
        override fun commitDelete(group: CallLogGroup) { events += "commit ${group.rowId}" }
        override fun block(row: CallLogRowUi) { events += "block ${row.group.rowId}" }
        override fun unblock(row: CallLogRowUi) { events += "unblock ${row.group.rowId}" }
        override fun turnOnPermission() { events += "permission" }
        override fun emptyAction() { events += "dialpad" }
    }

    private val actions = RecordingActions()

    private fun show(showEmptyAction: Boolean = true) = compose.setContent {
        PhoneScreenTheme {
            CallHistoryScreen(
                state = state,
                resolve = { group, _, _ -> rows.getValue(group.rowId) },
                emptyMessage = R.string.call_log_all_empty,
                showEmptyAction = showEmptyAction,
                actions = actions,
                selectable = true,
            )
        }
    }

    @Test
    fun callsSitUnderTheirDay() {
        show()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Older").assertExists()
    }

    @Test
    fun tappingARowOpensItsActions() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryActions(10)).assertDoesNotExist()

        compose.onNodeWithTag(TestTags.callHistoryRow(10)).performClick()
        compose.onNodeWithTag(TestTags.callHistoryAction(10, "DETAILS")).performClick()

        assertThat(actions.events).containsExactly("opened 10", "DETAILS 10").inOrder()
    }

    @Test
    fun anOpenRowsLabelsRunTheirActionsToo() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).performClick()
        compose.onNode(hasText("Call details") and hasAnyAncestor(hasTestTag(TestTags.callHistoryActions(10)))).performClick()
        assertThat(actions.events).containsExactly("opened 10", "DETAILS 10").inOrder()
    }

    @Test
    fun longPressSelectsCallsAndDeletesThemTogether() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).performTouchInput { longClick() }
        compose.onNodeWithTag(TestTags.CALL_HISTORY_SELECTION).assertExists()
        compose.onNodeWithTag(TestTags.callHistoryRow(20)).performClick()
        compose.onNodeWithText("2 selected").assertExists()

        compose.onNode(hasText("Delete") and hasAnyAncestor(hasTestTag(TestTags.CALL_HISTORY_SELECTION))).performClick()
        compose.onNodeWithText("Delete these 2 calls from your history?").assertExists()
        compose.onAllNodes(hasText("Delete")).onLast().performClick()

        assertThat(actions.events).containsExactly("selecting true", "deleteSelected [10, 20]", "selecting false").inOrder()
    }

    @Test
    fun theButtonAtTheEndCallsBack() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryPrimary(20)).performClick()
        assertThat(actions.events).containsExactly("call 20")
    }

    @Test
    fun blockingAsksFirst() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).performClick()
        compose.onNodeWithTag(TestTags.callHistoryAction(10, "BLOCK")).performClick()
        compose.onNodeWithText("BLOCK").performClick()
        assertThat(actions.events).containsExactly("opened 10", "block 10").inOrder()
    }

    @Test
    fun swipingTowardTheStartDeletesWithUndo() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(actions.events).contains("delete 10")
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).assertDoesNotExist()

        compose.onNodeWithText("UNDO").performClick()
        compose.waitForIdle()

        assertThat(actions.events).contains("undo 10")
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).assertExists()
    }

    @Test
    fun swipingTowardTheEndCallsBack() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(20)).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(actions.events).contains("call 20")
    }

    @Test
    fun noCallsOffersTheDialpad() {
        state = CallHistoryUiState.Empty
        show()
        compose.onNodeWithText("Your call history is empty").assertExists()
        compose.onNodeWithText("Make a call").performClick()
        assertThat(actions.events).containsExactly("dialpad")
    }

    @Test
    fun missingPermissionOffersToTurnItOn() {
        state = CallHistoryUiState.NeedsPermission
        show()
        compose.onNodeWithText("Turn on").performClick()
        assertThat(actions.events).containsExactly("permission")
    }

    @Test
    fun screenshotDark() {
        show()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/calllog/history_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(10)).performClick()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/calllog/history_light_open.png")
    }
}
