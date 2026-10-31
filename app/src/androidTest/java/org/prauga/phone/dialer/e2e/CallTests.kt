/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import androidx.test.platform.app.InstrumentationRegistry
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.call.CallList

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

    /**
     * Waits until a call rings in the app's call UI, past screening, and returns its connection on
     * the fake carrier. The carrier holds the call before screening finishes, so waiting for the
     * connection alone is too early.
     */
    fun waitForRinging(): FakeConnection {
        waitFor { if (hasIncomingCall()) true else null }
        return waitFor { FakeCarrier.connections.firstOrNull() }
    }

    private fun hasIncomingCall(): Boolean {
        var incoming = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { incoming = CallList.getInstance().incomingCall != null }
        return incoming
    }

    /** A number no call log row uses yet, in the reserved 555-01xx range. */
    fun freshNumber(): String = "+1555" + (1_000_000..9_999_999).random()

    /** Adds a finished incoming call from [number] to the call log, as if it happened earlier. */
    fun addPastIncomingCall(context: Context, number: String) {
        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, number)
            put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE)
            put(CallLog.Calls.DATE, System.currentTimeMillis() - PAST_CALL_AGE_MILLIS)
            put(CallLog.Calls.DURATION, 30)
        }
        checkNotNull(context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)) { "could not add call log row" }
    }

    /** The newest call log row logged at or after [since], whatever its number. */
    fun latestCall(context: Context, since: Long): CallLogRow? = query(context, "${CallLog.Calls.DATE} >= ?", arrayOf(since.toString()))

    /** The newest call log row for [number] logged at or after [since]. */
    fun latestCall(context: Context, number: String, since: Long): CallLogRow? =
        query(context, "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.NUMBER} = ?", arrayOf(since.toString(), number))

    private fun query(context: Context, selection: String, args: Array<String>): CallLogRow? = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.TYPE, CallLog.Calls.BLOCK_REASON, CallLog.Calls.MISSED_REASON),
        selection,
        args,
        "${CallLog.Calls.DATE} DESC",
    )?.use { if (it.moveToFirst()) CallLogRow(it.getInt(0), it.getInt(1), it.getLong(2)) else null }

    private const val PAST_CALL_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
}
