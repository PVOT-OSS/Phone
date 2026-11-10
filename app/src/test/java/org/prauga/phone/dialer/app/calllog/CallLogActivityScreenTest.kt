/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.callstats.CallStatsActions
import org.prauga.phone.dialer.callstats.CallStatsDetails
import org.prauga.phone.dialer.callstats.CallStatsEntry
import org.prauga.phone.dialer.callstats.CallStatsFilter
import org.prauga.phone.dialer.callstats.CallStatsListScreen
import org.prauga.phone.dialer.callstats.CallStatsUiState
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.SolidGlassTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The call statistics' glass top bar and range dialog. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class CallLogActivityScreenTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            SolidGlassTheme {
                PhoneScaffold(topBar = { CallStatsTopBar(onBack = { events += "back" }) }) { padding
                    ->
                    CallStatsListScreen(
                        state = CallStatsUiState.Loaded(entries, total),
                        filter = CallStatsFilter(),
                        prepare = { it.details },
                        actions = NoActions,
                        modifier =
                            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                    )
                }
            }
        }
    }

    @Test
    fun backLeavesTheScreen() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(events).containsExactly("back")
    }

    @Test
    fun theRangeDialogHandsBackThePickedDays() {
        var picked: Pair<Long, Long>? = null
        var dismissed = false
        compose.setContent {
            SolidGlassTheme {
                CallStatsRangeDialog(
                    visible = true,
                    initialStartUtcDay = DAY,
                    initialEndUtcDay = DAY * 3,
                    onDismiss = { dismissed = true },
                    onPick = { start, end -> picked = start to end },
                )
            }
        }
        // The picker merges its days into one node for TalkBack, so look at the unmerged tree.
        compose
            .onNodeWithTag(TestTags.CALL_STATS_RANGE_PICKER, useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertThat(picked).isEqualTo(DAY to DAY * 3)
        assertThat(dismissed).isFalse()
    }

    @Test
    fun cancellingTheRangeDialogPicksNothing() {
        var picked = false
        var dismissed = false
        compose.setContent {
            SolidGlassTheme {
                CallStatsRangeDialog(
                    visible = true,
                    initialStartUtcDay = null,
                    initialEndUtcDay = null,
                    onDismiss = { dismissed = true },
                    onPick = { _, _ -> picked = true },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isTrue()
        assertThat(picked).isFalse()
    }

    @Test fun screenshotDark() = screenshot("stats_tab_dark")

    @Test @Config(qualifiers = "+notnight") fun screenshotLight() = screenshot("stats_tab_light")

    @Test
    fun rangeDialogScreenshotDark() {
        compose.setContent {
            SolidGlassTheme {
                CallStatsRangeDialog(
                    visible = true,
                    initialStartUtcDay = OCT_1_2026,
                    initialEndUtcDay = OCT_1_2026 + DAY * 4,
                    onDismiss = {},
                    onPick = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/calllog/range_dialog_dark.png")
    }

    private fun screenshot(name: String) {
        show()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/calllog/$name.png")
    }

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

    private object NoActions : CallStatsActions {
        override fun open(entry: CallStatsEntry, total: CallStatsDetails) = Unit

        override fun copyNumber(number: String) = Unit

        override fun editBeforeCall(number: String) = Unit

        override fun pickRange() = Unit

        override fun clearRange() = Unit

        override fun sortByDuration(byDuration: Boolean) = Unit

        override fun turnOnPermission() = Unit
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val OCT_1_2026 = 1_790_812_800_000L
    }
}
