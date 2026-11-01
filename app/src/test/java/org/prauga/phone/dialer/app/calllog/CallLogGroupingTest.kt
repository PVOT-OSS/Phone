/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.phone.dialer.app.calllog.CallLogGrouping.Call

class CallLogGroupingTest {

    private val sameDigits: (String, String) -> Boolean = { a, b -> a.filter(Char::isDigit) == b.filter(Char::isDigit) }

    private fun call(
        number: String? = "+16505550100",
        type: Int = Calls.INCOMING_TYPE,
        day: Int = 0,
        presentation: Int = Calls.PRESENTATION_ALLOWED,
        postDial: String? = null,
    ) = Call(number, postDial, presentation, type, day)

    private fun joins(previous: Call, next: Call) = CallLogGrouping.joins(previous, next, sameDigits)

    @Test
    fun incomingAndOutgoingFromTheSameNumberShareARow() =
        assertTrue(joins(call(type = Calls.INCOMING_TYPE), call(type = Calls.OUTGOING_TYPE)))

    @Test
    fun missedCallsGetTheirOwnRow() =
        assertFalse(joins(call(type = Calls.INCOMING_TYPE), call(type = Calls.MISSED_TYPE)))

    @Test
    fun rejectedCallsCountAsMissed() =
        assertTrue(joins(call(type = Calls.MISSED_TYPE), call(type = Calls.REJECTED_TYPE)))

    @Test
    fun todayAndYesterdayAreSeparate() = assertFalse(joins(call(day = 0), call(day = 1)))

    @Test
    fun olderCallsShareARowAcrossDays() =
        assertTrue(joins(call(day = CallLogGrouping.dayBucket(3)), call(day = CallLogGrouping.dayBucket(9))))

    @Test
    fun differentNumbersAreSeparate() = assertFalse(joins(call(number = "+16505550100"), call(number = "+16505550199")))

    @Test
    fun privateCallersNeverShareARow() {
        val hidden = call(number = "", presentation = Calls.PRESENTATION_RESTRICTED)
        assertFalse(joins(hidden, hidden))
        assertFalse(joins(call(number = null), call(number = null)))
    }

    @Test
    fun differentPresentationsAreSeparate() =
        assertFalse(joins(call(presentation = Calls.PRESENTATION_ALLOWED), call(presentation = Calls.PRESENTATION_PAYPHONE)))

    @Test
    fun differentPostDialDigitsAreSeparate() = assertFalse(joins(call(postDial = ",1"), call(postDial = ",2")))

    @Test
    fun voicemailsAreNeverGrouped() =
        assertFalse(joins(call(type = Calls.VOICEMAIL_TYPE), call(type = Calls.VOICEMAIL_TYPE)))

    @Test
    fun blockedCallsOnlyGroupWithBlockedCalls() {
        assertFalse(joins(call(type = Calls.BLOCKED_TYPE), call(type = Calls.INCOMING_TYPE)))
        assertTrue(joins(call(type = Calls.BLOCKED_TYPE), call(type = Calls.BLOCKED_TYPE)))
    }

    @Test
    fun dayBuckets() {
        assertTrue(CallLogGrouping.dayBucket(0) == 0)
        assertTrue(CallLogGrouping.dayBucket(1) == 1)
        assertTrue(CallLogGrouping.dayBucket(2) == 2)
        assertTrue(CallLogGrouping.dayBucket(40) == 2)
    }
}
