/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.content.Context
import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.prauga.phone.dialer.R

/** How a call log row says when the call was. */
object CallLogTime {

    /** The shape of the time shown, from most recent to oldest. */
    enum class Style {
        /** Under a minute ago: "Just now". */
        JUST_NOW,

        /** Under an hour ago: "5 min ago". */
        MINUTES_AGO,

        /** Earlier today: the clock time. */
        TIME,

        /** In the last week: the weekday and time. */
        WEEKDAY_AND_TIME,

        /** In the last twelve months: the date without the year, and the time. */
        DATE_AND_TIME,

        /** Longer ago: the date with the year, and the time. */
        FULL_DATE_AND_TIME,
    }

    @JvmStatic
    fun styleOf(callMillis: Long, nowMillis: Long, zone: ZoneId): Style {
        val ago = nowMillis - callMillis
        val call = Instant.ofEpochMilli(callMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val days = ChronoUnit.DAYS.between(call.toLocalDate(), now.toLocalDate())
        return when {
            ago < MINUTE_MILLIS -> Style.JUST_NOW
            ago < HOUR_MILLIS -> Style.MINUTES_AGO
            days == 0L -> Style.TIME
            days < WEEK_DAYS -> Style.WEEKDAY_AND_TIME
            call.isAfter(now.minusMonths(YEAR_MONTHS)) -> Style.DATE_AND_TIME
            else -> Style.FULL_DATE_AND_TIME
        }
    }

    /** The text for a call made at [callMillis], as seen at [nowMillis]. */
    @JvmStatic
    fun format(context: Context, callMillis: Long, nowMillis: Long): CharSequence {
        val time = DateUtils.FORMAT_SHOW_TIME
        return when (styleOf(callMillis, nowMillis, ZoneId.systemDefault())) {
            Style.JUST_NOW -> context.getString(R.string.call_log_time_just_now)
            Style.MINUTES_AGO ->
                DateUtils.getRelativeTimeSpanString(
                        callMillis,
                        nowMillis,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    )
                    .toString()
                    .replace(".", "")
            Style.TIME -> DateUtils.formatDateTime(context, callMillis, time)
            Style.WEEKDAY_AND_TIME ->
                DateUtils.formatDateTime(
                    context,
                    callMillis,
                    time or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY,
                )
            Style.DATE_AND_TIME ->
                DateUtils.formatDateTime(
                    context,
                    callMillis,
                    time or
                        DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_NO_YEAR or
                        DateUtils.FORMAT_ABBREV_MONTH,
                )
            Style.FULL_DATE_AND_TIME ->
                DateUtils.formatDateTime(
                    context,
                    callMillis,
                    time or
                        DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_ABBREV_MONTH,
                )
        }
    }

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val WEEK_DAYS = 7L
    private const val YEAR_MONTHS = 12L
}
