/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.provider.CallLog.Calls
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The call statistics detail summary and screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class CallStatsDetailTest {

    @get:Rule val compose = createComposeRule()

    private fun details(vararg calls: Pair<Int, Long>) =
        CallStatsDetails("+15551234567", Calls.PRESENTATION_ALLOWED, "", null, null, "us", "", 0).apply {
            for ((type, seconds) in calls) addTimeOrMissed(type, seconds)
        }

    private val number = details(Calls.INCOMING_TYPE to 120, Calls.INCOMING_TYPE to 60, Calls.OUTGOING_TYPE to 300,
        Calls.MISSED_TYPE to 0)
    private val total = details(Calls.INCOMING_TYPE to 600, Calls.OUTGOING_TYPE to 1200, Calls.MISSED_TYPE to 0,
        Calls.MISSED_TYPE to 0, Calls.BLOCKED_TYPE to 0).apply { mergeWith(this@CallStatsDetailTest.number) }

    @Test
    fun durationsShowOnlyTypesWithTalkTime() {
        val lines = CallStatsSummary(number, total).durations
        assertThat(lines.map { it.type }).containsExactly(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE).inOrder()
        assertThat(lines.map { it.amount }).containsExactly(180L, 300L).inOrder()
        assertThat(lines.map { it.percent }).containsExactly(38, 63).inOrder()
    }

    @Test
    fun countsLeaveOutTypesWithNoCalls() {
        val lines = CallStatsSummary(number, total).counts
        assertThat(lines.map { it.type }).containsExactly(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE, Calls.MISSED_TYPE).inOrder()
        assertThat(lines.map { it.percent }).containsExactly(50, 25, 25).inOrder()
    }

    @Test
    fun withNoCallsEveryTypeShowsAtZero() {
        val lines = CallStatsSummary(details(), details()).counts
        assertThat(lines).hasSize(4)
        assertThat(lines.all { it.amount == 0L && it.percent == null }).isTrue()
    }

    @Test
    fun averagesRoundToTheNearestSecond() {
        val averages = CallStatsSummary(details(Calls.INCOMING_TYPE to 10, Calls.INCOMING_TYPE to 11), total).averages
        assertThat(averages.single().amount).isEqualTo(11L)
    }

    @Test
    fun sharesOfTheTotalAddUpToThisNumbersPart() {
        val shares = CallStatsSummary(number, total).durationShares(total.fullDuration)
        assertThat(shares.sumOf { it.fraction.toDouble() }).isWithin(0.001).of(480.0 / 2280.0)
    }

    @Test
    fun theCallButtonPlacesTheCall() {
        var called = false
        show(header(canCall = true), onCall = { called = true })
        compose.onNodeWithTag(TestTags.CALL_STATS_CALL).performClick()
        assertThat(called).isTrue()
    }

    @Test
    fun aNumberThatCantBeCalledHasNoActions() {
        show(header(canCall = false))
        compose.onNodeWithText("Alex Doe").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CALL_STATS_CALL).assertDoesNotExist()
    }

    @Test
    fun screenshotDark() {
        show(header(canCall = true), dateRange = "Oct 1 – 31, 2026")
        compose.onRoot().captureRoboImage("src/test/screenshots/callstats/detail_dark.png")
    }

    private fun header(canCall: Boolean) = CallStatsHeader(
        title = "Alex Doe", subtitle = "Mobile +1 555-123-4567", nameForPhoto = "Alex Doe",
        contactType = LetterTileDrawable.TYPE_DEFAULT, canCall = canCall, canEditBeforeCall = canCall,
    )

    private fun show(header: CallStatsHeader, dateRange: String? = null, onCall: () -> Unit = {}) {
        compose.setContent {
            PvotAppTheme {
                CallStatsDetailScreen(
                    header = header, summary = CallStatsSummary(number, total), dateRange = dateRange,
                    onBack = {}, onCall = onCall, callWithIntent = null, onEditNumber = {}, onCopy = {},
                    photo = { Box(it) },
                )
            }
        }
    }
}
