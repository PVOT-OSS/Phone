/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.phone.dialer.app.calllog.CallLogTime.Style

class CallLogTimeTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 11, 1, 18, 30)

    private fun styleOf(call: LocalDateTime): Style =
        CallLogTime.styleOf(call.atZone(zone).toInstant().toEpochMilli(), now.atZone(zone).toInstant().toEpochMilli(), zone)

    @Test
    fun underAMinuteIsJustNow() = assertEquals(Style.JUST_NOW, styleOf(now.minusSeconds(40)))

    @Test
    fun underAnHourIsMinutesAgo() = assertEquals(Style.MINUTES_AGO, styleOf(now.minusMinutes(59)))

    @Test
    fun earlierTodayIsTheTime() = assertEquals(Style.TIME, styleOf(now.withHour(0).withMinute(5)))

    @Test
    fun yesterdayLateIsTheWeekday() = assertEquals(Style.WEEKDAY_AND_TIME, styleOf(now.minusDays(1).withHour(23)))

    @Test
    fun sixDaysAgoIsTheWeekday() = assertEquals(Style.WEEKDAY_AND_TIME, styleOf(now.minusDays(6)))

    @Test
    fun aWeekAgoIsTheDate() = assertEquals(Style.DATE_AND_TIME, styleOf(now.minusDays(7)))

    @Test
    fun lastYearWithinTwelveMonthsHasNoYear() = assertEquals(Style.DATE_AND_TIME, styleOf(now.minusMonths(11)))

    @Test
    fun overAYearAgoHasTheYear() = assertEquals(Style.FULL_DATE_AND_TIME, styleOf(now.minusMonths(13)))
}
