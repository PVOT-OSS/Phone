/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.provider.CallLog.Calls
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One line of a call statistics section: a call type, its amount and its share of the total. */
internal data class StatLine(val type: Int, val amount: Long, val percent: Int?)

/** A call type's share of a total, for the proportional bars. */
internal data class StatShare(val type: Int, val fraction: Float)

/**
 * What the call statistics detail screen shows for one number, against all calls in the same time
 * range. A line is left out when that type has nothing while others do.
 */
internal class CallStatsSummary(private val number: CallStatsDetails, private val total: CallStatsDetails) {

    val totalDuration: Long get() = number.fullDuration
    val totalCount: Int get() = number.totalCount
    val allCallsDuration: Long get() = total.fullDuration
    val allCallsCount: Int get() = total.totalCount

    /** Incoming and outgoing talk time, in seconds; empty when there was none. */
    val durations: List<StatLine>
        get() = listOf(Calls.INCOMING_TYPE to number.inDuration, Calls.OUTGOING_TYPE to number.outDuration)
            .filter { (_, duration) -> duration != 0L }
            .map { (type, duration) -> StatLine(type, duration, percentOf(duration, totalDuration)) }

    /** Calls by type; a type with no calls is shown only if there were no calls at all. */
    val counts: List<StatLine>
        get() = listOf(
            Calls.INCOMING_TYPE to number.incomingCount,
            Calls.OUTGOING_TYPE to number.outgoingCount,
            Calls.MISSED_TYPE to number.missedCount,
            Calls.BLOCKED_TYPE to number.blockedCount,
        )
            .filter { (_, count) -> count != 0 || totalCount == 0 }
            .map { (type, count) -> StatLine(type, count.toLong(), percentOf(count.toLong(), totalCount.toLong())) }

    /** Average call length, in seconds, for incoming and outgoing calls that happened. */
    val averages: List<StatLine>
        get() = listOf(
            Triple(Calls.INCOMING_TYPE, number.inDuration, number.incomingCount),
            Triple(Calls.OUTGOING_TYPE, number.outDuration, number.outgoingCount),
        )
            .filter { (_, _, count) -> count != 0 }
            .map { (type, duration, count) -> StatLine(type, (duration.toFloat() / count).roundToLong(), null) }

    /** This number's talk time by type, as shares of [of]. */
    fun durationShares(of: Long): List<StatShare> = shares(of, Calls.INCOMING_TYPE to number.inDuration,
        Calls.OUTGOING_TYPE to number.outDuration)

    /** This number's calls by type, as shares of [of]. */
    fun countShares(of: Long): List<StatShare> = shares(of,
        Calls.INCOMING_TYPE to number.incomingCount.toLong(),
        Calls.OUTGOING_TYPE to number.outgoingCount.toLong(),
        Calls.MISSED_TYPE to number.missedCount.toLong(),
        Calls.BLOCKED_TYPE to number.blockedCount.toLong(),
    )

    private fun shares(of: Long, vararg parts: Pair<Int, Long>): List<StatShare> =
        if (of <= 0) emptyList() else parts.map { (type, value) -> StatShare(type, value.toFloat() / of) }

    private fun percentOf(value: Long, total: Long): Int? =
        if (total <= 0) null else (100f * value / total).roundToInt()
}
