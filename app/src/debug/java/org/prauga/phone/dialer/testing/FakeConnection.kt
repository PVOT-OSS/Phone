/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.net.Uri
import android.telecom.Connection
import android.telecom.Connection.RttModifyStatus
import android.telecom.Connection.RttTextStream
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount

/** A call on the fake carrier; the user's side acts through Telecom, the far end through this class. */
class FakeConnection(val spec: FakeCallSpec, val isIncoming: Boolean) : Connection() {

    /** DTMF tones the user played, in order, including post-dial digits they chose to send. */
    val playedTones = mutableListOf<Char>()

    /** The video side of the call, which the far end uses to accept, ask for or drop video. */
    val video = FakeVideoProvider(this)

    private val rttReceived = StringBuilder()

    /** True while the user waits for the far end to answer their request to switch to RTT. */
    @Volatile
    var rttRequestedByUser = false
        private set

    /** The user's answer to the far end's last RTT request, null until they answer. */
    @Volatile
    var rttRequestAccepted: Boolean? = null
        private set

    @Volatile
    private var rttStream: RttTextStream? = null

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
            CAPABILITY_DISCONNECT_FROM_CONFERENCE or
            CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL or
            CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL
        videoProvider = video
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

    override fun onStartRtt(rttTextStream: RttTextStream) {
        rttStream = rttTextStream
        // Telecom also hands over the streams when the call turned RTT on itself; only a call
        // that isn't RTT yet is the user asking to switch.
        if (connectionProperties and PROPERTY_IS_RTT == 0) rttRequestedByUser = true
    }

    override fun onStopRtt() {
        rttStream = null
        connectionProperties = connectionProperties and PROPERTY_IS_RTT.inv()
    }

    override fun handleRttUpgradeResponse(rttTextStream: RttTextStream?) {
        rttRequestAccepted = rttTextStream != null
        if (rttTextStream != null) startRtt(rttTextStream)
    }

    override fun onPostDialContinue(proceed: Boolean) {
        if (proceed) playedTones += postDialDigits.toList()
    }

    /** The far end picks up an outgoing call; digits after a wait (;) then wait for the user. */
    fun answerFromFarEnd() {
        setActive()
        if (postDialDigits.isNotEmpty()) setPostDialWait(postDialDigits)
    }

    /** Starts the call with RTT on, as a network does for a call that was RTT from the start. */
    fun startRtt(stream: RttTextStream?) {
        stream?.let { rttStream = it }
        connectionProperties = connectionProperties or PROPERTY_IS_RTT
    }

    /** The far end agrees to the user's request to switch to RTT. */
    fun acceptRttRequest() {
        check(rttRequestedByUser) { "no RTT request" }
        rttRequestedByUser = false
        connectionProperties = connectionProperties or PROPERTY_IS_RTT
        sendRttInitiationSuccess()
    }

    /** The far end turns the user's request to switch to RTT down. */
    fun rejectRttRequest() {
        check(rttRequestedByUser) { "no RTT request" }
        rttRequestedByUser = false
        rttStream = null
        sendRttInitiationFailure(RttModifyStatus.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE)
    }

    /** The far end asks the user to switch the call to RTT. */
    fun requestRtt() {
        rttRequestAccepted = null
        sendRemoteRttRequest()
    }

    /** The far end types [text] over RTT. */
    fun typeRtt(text: String) = checkNotNull(rttStream) { "RTT is off" }.write(text)

    /** Everything the user has typed over RTT so far. */
    fun readRtt(): String {
        rttStream?.readImmediately()?.let { rttReceived.append(it) }
        return rttReceived.toString()
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
