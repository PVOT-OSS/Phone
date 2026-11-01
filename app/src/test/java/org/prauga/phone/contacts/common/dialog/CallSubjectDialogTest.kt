/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.dialog

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The call note dialog: its length rules, its history and what it sends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class CallSubjectDialogTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val handle = PhoneAccountHandle(ComponentName("com.android.phone", "Telephony"), "1")

    private fun account(extras: Bundle?) = PhoneAccount.builder(handle, "SIM 1").setExtras(extras).build()

    @Test
    fun withoutAccountSettingsNotesAreSixteenCharacters() {
        val limits = SubjectLimits.of(null)
        assertThat(limits.maxLength).isEqualTo(16)
        assertThat(limits.lengthOf("héllo")).isEqualTo(5)
    }

    @Test
    fun theAccountCanSetTheLimitAndCountBytes() {
        val limits = SubjectLimits.of(account(Bundle().apply {
            putInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH, 20)
            putString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING, "UTF-8")
        }))
        assertThat(limits.maxLength).isEqualTo(20)
        assertThat(limits.lengthOf("héllo")).isEqualTo(6)
    }

    @Test
    fun anUnknownEncodingCountsCharacters() {
        val limits = SubjectLimits.of(account(Bundle().apply {
            putString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING, "not-a-charset")
        }))
        assertThat(limits.encoding).isNull()
    }

    @Test
    fun historyKeepsTheLastFiveNotes() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        CallSubjectDialog.saveSubjectHistory(prefs, listOf("1", "", "2", "3", "4", "5", "6"))
        assertThat(CallSubjectDialog.loadSubjectHistory(prefs)).containsExactly("2", "3", "4", "5", "6").inOrder()
    }

    @Test
    fun typingPastTheLimitIsRefused() {
        var sent: String? = null
        show(SubjectLimits(5, null), onSend = { sent = it })
        compose.onNode(hasSetTextAction()).performTextInput("hello world")
        compose.onNodeWithTag(TestTags.CALL_SUBJECT_SEND).performClick()
        // The whole input is longer than the limit, so the field keeps what it had.
        assertThat(sent).isEmpty()
    }

    @Test
    fun aRecentNoteCanBeReused() {
        var sent: String? = null
        show(history = listOf("Running late", "Call me back"), onSend = { sent = it })
        compose.onNodeWithTag(TestTags.CALL_SUBJECT_HISTORY).performClick()
        compose.onNodeWithText("Running late").performClick()
        compose.onNodeWithTag(TestTags.CALL_SUBJECT_SEND).performClick()
        assertThat(sent).isEqualTo("Running late")
    }

    @Test
    fun theCounterShowsLengthAgainstTheLimit() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("Hi")
        compose.onNodeWithText("2 / 16", substring = true).assertTextContains("2 / 16", substring = true)
    }

    @Test
    fun screenshotDark() {
        show(history = listOf("Running late"))
        compose.onNode(hasSetTextAction()).performTextInput("At the door")
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/dialogs/call_subject_dark.png")
    }

    private fun show(
        limits: SubjectLimits = SubjectLimits(16, null),
        history: List<String> = emptyList(),
        onSend: (String) -> Unit = {},
    ) {
        compose.setContent {
            PvotAppTheme {
                CallSubjectContent("Alex Doe", "Mobile +1 555-123-4567", limits, history, onSend, onDismiss = {}, photo = null)
            }
        }
    }
}
