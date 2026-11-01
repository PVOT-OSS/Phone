/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.net.Uri
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount

/** A call on the fake carrier; the user's side acts through Telecom, the far end through this class. */
class FakeConnection(val spec: FakeCallSpec, val isIncoming: Boolean) : Connection() {

    /** DTMF tones the user played, in order, including post-dial digits they chose to send. */
    val playedTones = mutableListOf<Char>()

    private val postDialDigits = spec.number?.substringAfter(';', "").orEmpty()

    init {
        val address = spec.number?.let { Uri.fromParts(PhoneAccount.SCHEME_TEL, it, null) }
        setAddress(address, spec.presentation)
        setCallerDisplayName(null, spec.presentation)
        setCallerNumberVerificationStatus(spec.verificationStatus)
        setVideoState(spec.videoState)
        connectionCapabilities = CAPABILITY_HOLD or
            CAPABILITY_SUPPORT_HOLD or
            CAPABILITY_MUTE or
            CAPABILITY_SEPARATE_FROM_CONFERENCE or
            CAPABILITY_DISCONNECT_FROM_CONFERENCE
        audioModeIsVoip = false
    }

    override fun onAnswer(videoState: Int) {
        setVideoState(videoState)
        setActive()
    }

    override fun onReject() = end(DisconnectCause(DisconnectCause.REJECTED))

    override fun onDisconnect() = end(DisconnectCause(DisconnectCause.LOCAL))

    override fun onAbort() = end(DisconnectCause(DisconnectCause.CANCELED))

    override fun onHold() = setOnHold()

    override fun onUnhold() = setActive()

    override fun onSeparate() {
        conference?.onSeparate(this)
        setActive()
    }

    override fun onPlayDtmfTone(c: Char) {
        playedTones += c
    }

    override fun onPostDialContinue(proceed: Boolean) {
        if (proceed) playedTones += postDialDigits.toList()
    }

    /** The far end picks up an outgoing call; digits after a wait (;) then wait for the user. */
    fun answerFromFarEnd() {
        setActive()
        if (postDialDigits.isNotEmpty()) setPostDialWait(postDialDigits)
    }

    /**
     * The far end or the network ends the call with [cause]. By default a call that is still
     * ringing ends as missed, as a real network reports it, and any other call as remote.
     */
    fun hangUpFromFarEnd(cause: Int = if (state == STATE_RINGING) DisconnectCause.MISSED else DisconnectCause.REMOTE) =
        end(DisconnectCause(cause))

    private fun end(cause: DisconnectCause) {
        setDisconnected(cause)
        destroy()
        FakeCarrier.forget(this)
    }
}
