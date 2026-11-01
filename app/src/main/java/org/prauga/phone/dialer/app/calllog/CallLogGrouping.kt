/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls

/** Which calls the call log shows together as one row. */
object CallLogGrouping {

    /** A call as far as grouping cares. */
    data class Call(
        val number: String?,
        val postDialDigits: String?,
        val presentation: Int,
        val type: Int,
        /** 0 for today, 1 for yesterday, 2 for anything older. */
        val dayBucket: Int,
    )

    /**
     * Whether [call] joins the row that currently ends with [previous], the newer call just above
     * it. Rows only ever take calls next to each other.
     */
    @JvmStatic
    fun joins(previous: Call, call: Call, numbersMatch: (String, String) -> Boolean): Boolean {
        val previousNumber = previous.number
        val number = call.number
        return previous.presentation == call.presentation &&
            isMissed(previous.type) == isMissed(call.type) &&
            previous.dayBucket == call.dayBucket &&
            !previousNumber.isNullOrEmpty() && !number.isNullOrEmpty() &&
            previous.postDialDigits.orEmpty() == call.postDialDigits.orEmpty() &&
            previous.type != Calls.VOICEMAIL_TYPE && call.type != Calls.VOICEMAIL_TYPE &&
            (previous.type == Calls.BLOCKED_TYPE) == (call.type == Calls.BLOCKED_TYPE) &&
            numbersMatch(previousNumber, number)
    }

    /** The day bucket for a call [daysAgo] days before today. */
    @JvmStatic
    fun dayBucket(daysAgo: Int): Int = daysAgo.coerceIn(0, 2)

    /** Missed and rejected calls are told apart from calls that were answered or made. */
    @JvmStatic
    fun isMissed(type: Int): Boolean = type == Calls.MISSED_TYPE || type == Calls.REJECTED_TYPE
}
