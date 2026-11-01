/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.app.voicemail.VoicemailStatusCard
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessage
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The voicemail tab: playback in the open row, status messages and deleting several at once. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class VoicemailScreenTest {

    @get:Rule val compose = createComposeRule()

    private val first =
        callLogGroup(
                1,
                "+15550100",
                listOf(Calls.VOICEMAIL_TYPE),
                isRead = false,
                voicemailUri = "content://voicemail/1",
            )
            .copy(transcription = "Hi, it's Ada. Call me back about the meeting tomorrow.")
    private val second =
        callLogGroup(
            2,
            "+15550199",
            listOf(Calls.VOICEMAIL_TYPE),
            voicemailUri = "content://voicemail/2",
        )
    private val rows =
        mapOf(
            1L to
                row(
                    first,
                    "Ada Lovelace",
                    listOf(RowAction.CALL, RowAction.DELETE),
                    PrimaryAction.PLAY_VOICEMAIL,
                    "Today, 9:30 AM • 00:42",
                ),
            2L to
                row(
                    second,
                    "+15550199",
                    listOf(RowAction.CALL, RowAction.DELETE),
                    PrimaryAction.PLAY_VOICEMAIL,
                ),
        )
    private val playback = VoicemailPlaybackState().apply { setClipPosition(12_000, 42_000) }
    private var pending by mutableStateOf(emptySet<Long>())

    private inner class RecordingActions : CallHistoryActions {
        val events = mutableListOf<String>()

        override fun primaryAction(row: CallLogRowUi) {
            events += "primary ${row.group.rowId}"
        }

        override fun rowAction(row: CallLogRowUi, action: RowAction) {
            events += "${action.name} ${row.group.rowId}"
        }

        override fun opened(row: CallLogRowUi, playNow: Boolean) {
            events += "opened ${row.group.rowId}${if (playNow) " play" else ""}"
        }

        override fun closed(row: CallLogRowUi) {
            events += "closed ${row.group.rowId}"
        }

        override fun deleteLater(group: CallLogGroup) {
            events += "delete ${group.rowId}"
            pending = pending + group.callIds
        }

        override fun undoDelete(group: CallLogGroup) {
            events += "undo ${group.rowId}"
        }

        override fun commitDelete(group: CallLogGroup) {
            events += "commit ${group.rowId}"
        }

        override fun deleteSelected(groups: List<CallLogGroup>) {
            events += "deleteSelected ${groups.map { it.rowId }}"
            pending = pending + groups.flatMap { it.callIds }
        }

        override fun selectionModeChanged(enabled: Boolean, exit: () -> Unit) {
            events += "selecting $enabled"
        }

        override fun block(row: CallLogRowUi) = Unit

        override fun unblock(row: CallLogRowUi) = Unit

        override fun turnOnPermission() = Unit

        override fun emptyAction() = Unit
    }

    private val actions = RecordingActions()

    private fun show(header: VoicemailErrorMessage? = null) =
        compose.setContent {
            PhoneScreenTheme {
                CallHistoryScreen(
                    state = CallHistoryUiState.Loaded(listOf(first, second), emptySet()),
                    pendingDeletes = pending,
                    resolve = { group, _, _ -> rows.getValue(group.rowId) },
                    emptyMessage = R.string.call_log_voicemail_empty,
                    showEmptyAction = false,
                    actions = actions,
                    header = header?.let { message -> { VoicemailStatusCard(message) } },
                    voicemailPlayback = playback,
                    selectable = true,
                )
            }
        }

    @Test
    fun thePlayButtonOpensTheRowAndPlays() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryPrimary(1)).performClick()
        compose.onNodeWithTag(TestTags.VOICEMAIL_PLAYBACK, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("00:12", useUnmergedTree = true).assertExists()
        assertThat(actions.events).containsExactly("opened 1 play")
    }

    @Test
    fun aVoicemailsTranscriptIsItsTitleAndTheCallerIsUnderIt() {
        show()
        compose
            .onNodeWithText(
                "Hi, it's Ada. Call me back about the meeting tomorrow.",
                useUnmergedTree = true,
            )
            .assertExists()
        compose.onNodeWithText("Ada Lovelace", useUnmergedTree = true).assertExists()
        // Without a transcript the caller is the title, and only there.
        compose.onAllNodesWithText("+15550199", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theCallBackButtonCallsTheCaller() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryAction(1, "CALL")).performClick()
        assertThat(actions.events).containsExactly("CALL 1")
    }

    @Test
    fun openingAnotherRowClosesNothingButSwitches() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(1)).performClick()
        compose.onNodeWithTag(TestTags.callHistoryRow(1)).performClick()
        assertThat(actions.events).containsExactly("opened 1", "closed 1").inOrder()
    }

    @Test
    fun deleteInTheOpenRowAsksThenRemovesIt() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(2)).performClick()
        compose.onNodeWithTag(TestTags.callHistoryAction(2, "DELETE")).performClick()
        // The row's calls all go, so the Delete button asks first.
        assertThat(actions.events).doesNotContain("deleteSelected [2]")
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertThat(actions.events)
            .containsAtLeast("opened 2", "closed 2", "deleteSelected [2]")
            .inOrder()
        compose.onNodeWithTag(TestTags.callHistoryRow(2)).assertDoesNotExist()
    }

    @Test
    fun longPressSelectsAndDeletesSeveral() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(1)).performTouchInput { longClick() }
        compose.onNodeWithTag(TestTags.CALL_HISTORY_SELECTION).assertExists()
        compose.onNodeWithText("1 selected").assertExists()

        compose.onNodeWithTag(TestTags.callHistoryRow(2)).performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose
            .onNode(
                hasText("Delete") and hasAnyAncestor(hasTestTag(TestTags.CALL_HISTORY_SELECTION))
            )
            .performClick()
        // The confirmation's Delete is the last one on screen.
        compose.onAllNodes(hasText("Delete")).onLast().performClick()

        assertThat(actions.events)
            .containsExactly("selecting true", "deleteSelected [1, 2]", "selecting false")
            .inOrder()
    }

    @Test
    fun aStatusMessageShowsAboveTheVoicemails() {
        var clicked = false
        val message =
            VoicemailErrorMessage(
                "Voicemail inbox almost full",
                "You won't be able to receive new voicemail if your inbox is full.",
                VoicemailErrorMessage.Action("Manage storage", { clicked = true }, true),
            )
        show(header = message)
        compose.onNodeWithTag(TestTags.VOICEMAIL_STATUS).assertExists()
        compose.onNodeWithText("Manage storage").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun screenshotOpenDark() {
        show()
        compose.onNodeWithTag(TestTags.callHistoryRow(1)).performClick()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/voicemail/voicemail_open_dark.png")
    }
}
