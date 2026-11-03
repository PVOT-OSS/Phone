/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.phone.incallui.CallStatus.Phase

class CallStatusTest {

    private fun status(
        phase: Phase,
        spam: Boolean = false,
        rtt: Boolean = false,
        video: Boolean = false,
        paused: Boolean = false,
        videoRequest: Boolean = false,
        wifi: Boolean = false,
        work: Boolean = false,
    ) = CallStatus.of(phase, spam, rtt, video, paused, videoRequest, wifi, work)

    @Test
    fun ringingSpamWinsOverEverything() =
        assertEquals(
            CallStatus.SUSPECTED_SPAM,
            status(Phase.RINGING, spam = true, rtt = true, video = true, wifi = true, work = true),
        )

    @Test
    fun ringingRttComesBeforeVideo() =
        assertEquals(CallStatus.INCOMING_RTT, status(Phase.RINGING, rtt = true, video = true))

    @Test
    fun ringingVideoComesBeforeWifi() =
        assertEquals(CallStatus.INCOMING_VIDEO, status(Phase.RINGING, video = true, wifi = true))

    @Test
    fun ringingWifiComesBeforeWork() =
        assertEquals(CallStatus.INCOMING_WIFI, status(Phase.RINGING, wifi = true, work = true))

    @Test
    fun ringingWork() = assertEquals(CallStatus.INCOMING_WORK, status(Phase.RINGING, work = true))

    @Test fun ringingPlain() = assertEquals(CallStatus.INCOMING, status(Phase.RINGING))

    @Test
    fun heldIsOnHoldWhateverElse() =
        assertEquals(CallStatus.ON_HOLD, status(Phase.ON_HOLD, video = true, wifi = true))

    @Test
    fun dialingIsCalling() = assertEquals(CallStatus.CALLING, status(Phase.DIALING, video = true))

    @Test
    fun videoRequestComesFirstInACall() =
        assertEquals(
            CallStatus.VIDEO_REQUEST,
            status(Phase.IN_CALL, videoRequest = true, video = true),
        )

    @Test
    fun pausedVideo() =
        assertEquals(
            CallStatus.ONGOING_VIDEO_PAUSED,
            status(Phase.IN_CALL, video = true, paused = true),
        )

    @Test
    fun videoComesBeforeWifi() =
        assertEquals(CallStatus.ONGOING_VIDEO, status(Phase.IN_CALL, video = true, wifi = true))

    @Test
    fun wifiComesBeforeWork() =
        assertEquals(CallStatus.ONGOING_WIFI, status(Phase.IN_CALL, wifi = true, work = true))

    @Test fun work() = assertEquals(CallStatus.ONGOING_WORK, status(Phase.IN_CALL, work = true))

    @Test fun plainCall() = assertEquals(CallStatus.ONGOING, status(Phase.IN_CALL))

    @Test
    fun spamOnlyMattersWhileRinging() =
        assertEquals(CallStatus.ONGOING, status(Phase.IN_CALL, spam = true))

    @Test
    fun pausedWithoutVideoIsAPlainCall() =
        assertEquals(CallStatus.ONGOING, status(Phase.IN_CALL, paused = true))
}
