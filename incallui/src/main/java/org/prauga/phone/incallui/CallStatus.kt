/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

/** What the call notification says a call is doing. */
enum class CallStatus {
    SUSPECTED_SPAM,
    INCOMING_RTT,
    INCOMING_VIDEO,
    INCOMING_WIFI,
    INCOMING_WORK,
    INCOMING,
    ON_HOLD,
    CALLING,
    VIDEO_REQUEST,
    ONGOING_VIDEO_PAUSED,
    ONGOING_VIDEO,
    ONGOING_WIFI,
    ONGOING_WORK,
    ONGOING;

    /** Where a call is in its life, as far as the notification text cares. */
    enum class Phase {
        RINGING,
        DIALING,
        ON_HOLD,
        IN_CALL,
    }

    companion object {
        /** Picks the status from the call's facts; the first matching rule wins. */
        @JvmStatic
        fun of(
            phase: Phase,
            isSpam: Boolean,
            isRtt: Boolean,
            isVideo: Boolean,
            isVideoPaused: Boolean,
            hasVideoRequest: Boolean,
            isWifi: Boolean,
            isWork: Boolean,
        ): CallStatus =
            when (phase) {
                Phase.RINGING ->
                    when {
                        isSpam -> SUSPECTED_SPAM
                        isRtt -> INCOMING_RTT
                        isVideo -> INCOMING_VIDEO
                        isWifi -> INCOMING_WIFI
                        isWork -> INCOMING_WORK
                        else -> INCOMING
                    }
                Phase.ON_HOLD -> ON_HOLD
                Phase.DIALING -> CALLING
                Phase.IN_CALL ->
                    when {
                        hasVideoRequest -> VIDEO_REQUEST
                        isVideo && isVideoPaused -> ONGOING_VIDEO_PAUSED
                        isVideo -> ONGOING_VIDEO
                        isWifi -> ONGOING_WIFI
                        isWork -> ONGOING_WORK
                        else -> ONGOING
                    }
            }
    }
}
