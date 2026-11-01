/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.os.Bundle
import android.telecom.Connection
import android.telecom.TelecomManager
import android.telecom.VideoProfile

/** Describes a call the fake carrier creates, as the network would present it. */
data class FakeCallSpec(
    val number: String?,
    val presentation: Int = TelecomManager.PRESENTATION_ALLOWED,
    val verificationStatus: Int = Connection.VERIFICATION_STATUS_NOT_VERIFIED,
    val videoState: Int = VideoProfile.STATE_AUDIO_ONLY,
    val rtt: Boolean = false,
    /** Connection properties the network reports, such as [android.telecom.Connection.PROPERTY_WIFI]. */
    val properties: Int = 0,
    /** Extras the call carries, such as Telecom's hint that answering it will end another call. */
    val extras: Bundle = Bundle(),
    /** Capabilities on top of the usual ones, such as [android.telecom.Connection.CAPABILITY_TRANSFER]. */
    val capabilities: Int = 0,
) {
    fun toExtras(): Bundle = Bundle().apply {
        putString(EXTRA_NUMBER, number)
        putInt(EXTRA_PRESENTATION, presentation)
        putInt(EXTRA_VERIFICATION, verificationStatus)
        putInt(EXTRA_VIDEO_STATE, videoState)
        putBoolean(EXTRA_RTT, rtt)
        putInt(EXTRA_PROPERTIES, properties)
        putBundle(EXTRA_CALL_EXTRAS, extras)
        putInt(EXTRA_CAPABILITIES, capabilities)
    }

    companion object {
        private const val EXTRA_NUMBER = "org.prauga.phone.testing.NUMBER"
        private const val EXTRA_PRESENTATION = "org.prauga.phone.testing.PRESENTATION"
        private const val EXTRA_VERIFICATION = "org.prauga.phone.testing.VERIFICATION"
        private const val EXTRA_VIDEO_STATE = "org.prauga.phone.testing.VIDEO_STATE"
        private const val EXTRA_RTT = "org.prauga.phone.testing.RTT"
        private const val EXTRA_PROPERTIES = "org.prauga.phone.testing.PROPERTIES"
        private const val EXTRA_CALL_EXTRAS = "org.prauga.phone.testing.CALL_EXTRAS"
        private const val EXTRA_CAPABILITIES = "org.prauga.phone.testing.CAPABILITIES"

        fun fromExtras(extras: Bundle?): FakeCallSpec = FakeCallSpec(
            number = extras?.getString(EXTRA_NUMBER),
            presentation = extras?.getInt(EXTRA_PRESENTATION, TelecomManager.PRESENTATION_ALLOWED)
                ?: TelecomManager.PRESENTATION_ALLOWED,
            verificationStatus = extras?.getInt(EXTRA_VERIFICATION, Connection.VERIFICATION_STATUS_NOT_VERIFIED)
                ?: Connection.VERIFICATION_STATUS_NOT_VERIFIED,
            videoState = extras?.getInt(EXTRA_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY) ?: VideoProfile.STATE_AUDIO_ONLY,
            rtt = extras?.getBoolean(EXTRA_RTT) ?: false,
            properties = extras?.getInt(EXTRA_PROPERTIES) ?: 0,
            extras = extras?.getBundle(EXTRA_CALL_EXTRAS) ?: Bundle(),
            capabilities = extras?.getInt(EXTRA_CAPABILITIES) ?: 0,
        )
    }
}
