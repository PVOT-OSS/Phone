/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.provider.CallLog.Calls
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The call statistics list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class CallStatsListScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun entry(
        number: String,
        name: String?,
        inSeconds: Long,
        outSeconds: Long,
        missed: Int,
    ): CallStatsEntry {
        val details =
            CallStatsDetails(number, Calls.PRESENTATION_ALLOWED, "", null, null, "US", null, 0)
        details.addTimeOrMissed(Calls.INCOMING_TYPE, inSeconds)
        details.addTimeOrMissed(Calls.OUTGOING_TYPE, outSeconds)
        repeat(missed) { details.addTimeOrMissed(Calls.MISSED_TYPE, 0) }
        details.name = name
        details.displayName = name
        details.displayNumber = number
        return CallStatsEntry(ContactInfo(), details)
    }

    private val entries =
        listOf(
            entry("+15550100", "Ada Lovelace", 1_800, 600, 1),
            entry("+15550199", null, 300, 900, 2),
        )
    private val total =
        CallStatsDetails(null, 0, null, null, null, null, null, 0).apply {
            entries.forEach { mergeWith(it.details) }
        }

    private val events = mutableListOf<String>()
    private val actions =
        object : CallStatsActions {
            override fun open(entry: CallStatsEntry, total: CallStatsDetails) {
                events += "open ${entry.details.number}"
            }

            override fun copyNumber(number: String) {
                events += "copy $number"
            }

            override fun editBeforeCall(number: String) {
                events += "edit $number"
            }

            override fun pickRange() {
                events += "range"
            }

            override fun clearRange() {
                events += "clear"
            }

            override fun sortByDuration(byDuration: Boolean) {
                events += "byDuration $byDuration"
            }

            override fun turnOnPermission() {
                events += "permission"
            }
        }

    private fun show(filter: CallStatsFilter = CallStatsFilter()) =
        compose.setContent {
            PhoneScreenTheme {
                CallStatsListScreen(
                    CallStatsUiState.Loaded(entries, total),
                    filter,
                    prepare = { it.details },
                    actions = actions,
                )
            }
        }

    @Test
    fun showsTheTotalAndEachNumber() {
        show()
        compose.onNodeWithText("Total: 7 calls, 1 hr").assertExists()
        compose.onNodeWithText("Ada Lovelace").assertExists()
        compose.onNodeWithText("66.7%").assertExists()
    }

    @Test
    fun tappingANumberOpensItsDetails() {
        show()
        compose.onNodeWithTag(TestTags.callStatsRow("+15550199")).performClick()
        assertThat(events).containsExactly("open +15550199")
    }

    @Test
    fun longPressOffersToCopyTheNumber() {
        show()
        compose.onNodeWithTag(TestTags.callStatsRow("+15550100")).performTouchInput { longClick() }
        compose.onNodeWithText("Copy number").performClick()
        assertThat(events).containsExactly("copy +15550100")
    }

    @Test
    fun sortAndRangeControlsReport() {
        show(CallStatsFilter(from = 0, to = 86_400_000))
        compose.onNodeWithText("Count").performClick()
        compose.onNodeWithTag(TestTags.CALL_STATS_RANGE).performClick()
        compose.onNodeWithText("Reset time range").performClick()
        assertThat(events).containsExactly("byDuration false", "range", "clear").inOrder()
    }

    @Test
    fun screenshotDark() {
        show()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/callstats/list_dark.png")
    }
}
