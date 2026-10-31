/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.Context
import android.provider.CallLog

/** A row of the system call log, as tests check it. */
data class CallLogRow(val type: Int, val blockReason: Int, val missedReason: Long)

/** Waiting and call log helpers shared by the call tests. */
object CallTests {

    const val TIMEOUT_MILLIS = 10_000L
    private const val POLL_MILLIS = 200L

    /** Polls [block] until it returns a value or [timeoutMillis] passes. */
    fun <T : Any> waitFor(timeoutMillis: Long = TIMEOUT_MILLIS, block: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            block()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        return checkNotNull(block()) { "timed out" }
    }

    /** The newest call log row for [number] logged at or after [since]. */
    fun latestCall(context: Context, number: String, since: Long): CallLogRow? = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.TYPE, CallLog.Calls.BLOCK_REASON, CallLog.Calls.MISSED_REASON),
        "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.NUMBER} = ?",
        arrayOf(since.toString(), number),
        "${CallLog.Calls.DATE} DESC",
    )?.use { if (it.moveToFirst()) CallLogRow(it.getInt(0), it.getInt(1), it.getLong(2)) else null }
}
