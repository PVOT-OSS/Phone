/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Grouping call log rows into call history rows. */
@RunWith(RobolectricTestRunner::class)
class CallLogGrouperTest {

    private val now = System.currentTimeMillis()
    private val twoDaysAgo = now - TimeUnit.DAYS.toMillis(2)

    @Test
    fun consecutiveCallsFromOneNumberShareARow() {
        val groups =
            CallLogGrouper.group(
                callLogCursor(
                    CallRow(3, "+15550100", now, Calls.INCOMING_TYPE),
                    CallRow(2, "+15550100", now - 1000, Calls.OUTGOING_TYPE),
                    CallRow(1, "+15550199", now - 2000, Calls.OUTGOING_TYPE),
                )
            )

        assertThat(groups.map { it.callIds }).containsExactly(listOf(3L, 2L), listOf(1L)).inOrder()
        assertThat(groups[0].callTypes)
            .containsExactly(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE)
            .inOrder()
        assertThat(groups[0].number).isEqualTo("+15550100")
        assertThat(groups[0].entries.entriesCount).isEqualTo(2)
    }

    @Test
    fun missedCallsGetARowApartFromAnsweredOnes() {
        val groups =
            CallLogGrouper.group(
                callLogCursor(
                    CallRow(3, "+15550100", now, Calls.MISSED_TYPE, isRead = 0),
                    CallRow(2, "+15550100", now - 1000, Calls.INCOMING_TYPE),
                )
            )

        assertThat(groups.map { it.callIds }).containsExactly(listOf(3L), listOf(2L)).inOrder()
    }

    @Test
    fun callsOnDifferentDaysGetTheirOwnRows() {
        // Each row sits under one day header, so a run of calls from one number splits at days.
        val groups =
            CallLogGrouper.group(
                callLogCursor(
                    CallRow(3, "+15550100", now),
                    CallRow(2, "+15550100", twoDaysAgo),
                    CallRow(1, "+15550199", twoDaysAgo - 1000),
                )
            )

        assertThat(groups.map { it.callIds })
            .containsExactly(listOf(3L), listOf(2L), listOf(1L))
            .inOrder()
        assertThat(groups.map { it.dayGroup })
            .containsExactly(
                CallLogGroupBuilder.DAY_GROUP_TODAY,
                CallLogGroupBuilder.DAY_GROUP_OTHER,
                CallLogGroupBuilder.DAY_GROUP_OTHER,
            )
            .inOrder()
    }

    @Test
    fun onlyMissedCallsAndVoicemailsCanBeUnread() {
        val groups =
            CallLogGrouper.group(
                callLogCursor(
                    CallRow(2, "+15550100", now, Calls.MISSED_TYPE, isRead = 0),
                    CallRow(1, "+15550199", now - 1000, Calls.INCOMING_TYPE, isRead = 0),
                )
            )

        assertThat(groups.map { it.isRead }).containsExactly(false, true).inOrder()
    }

    @Test
    fun featuresOfAGroupCombine() {
        val groups =
            CallLogGrouper.group(
                callLogCursor(
                    CallRow(2, "+15550100", now, features = 0),
                    CallRow(1, "+15550100", now - 1000, features = Calls.FEATURES_HD_CALL),
                )
            )

        assertThat(groups.single().features and Calls.FEATURES_HD_CALL)
            .isEqualTo(Calls.FEATURES_HD_CALL)
    }

    @Test
    fun cachedNameComesFromTheCallLog() {
        val group =
            CallLogGrouper.group(callLogCursor(CallRow(1, "+15550100", now, name = "Ada"))).single()
        assertThat(group.cachedInfo.name).isEqualTo("Ada")
    }

    @Test
    fun emptyCursorHasNoRows() {
        assertThat(CallLogGrouper.group(callLogCursor())).isEmpty()
    }
}
