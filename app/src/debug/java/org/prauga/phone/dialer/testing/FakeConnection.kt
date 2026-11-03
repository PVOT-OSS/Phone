/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.Connection.RttModifyStatus
import android.telecom.Connection.RttTextStream
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount

/**
 * A call on the fake carrier; the user's side acts through Telecom, the far end through this class.
 */
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

    @Volatile private var rttStream: RttTextStream? = null

    private val postDialDigits = spec.number?.substringAfter(';', "").orEmpty()

    init {
        val address = spec.number?.let { Uri.fromParts(PhoneAccount.SCHEME_TEL, it, null) }
        setAddress(address, spec.presentation)
        setCallerDisplayName(null, spec.presentation)
        setCallerNumberVerificationStatus(spec.verificationStatus)
        setVideoState(spec.videoState)
        connectionCapabilities =
            CAPABILITY_HOLD or
                CAPABILITY_SUPPORT_HOLD or
                CAPABILITY_MUTE or
                CAPABILITY_SEPARATE_FROM_CONFERENCE or
                CAPABILITY_DISCONNECT_FROM_CONFERENCE or
                CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL or
                CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL or
                CAPABILITY_TRANSFER_CONSULTATIVE or
                spec.capabilities
        videoProvider = video
        connectionProperties = spec.properties
        if (!spec.extras.isEmpty) putExtras(spec.extras)
        audioModeIsVoip = false
    }

    override fun onAnswer(videoState: Int) {
        setVideoState(videoState)
        setActive()
    }

    override fun onReject() = end(DisconnectCause(DisconnectCause.REJECTED))

    /** What the user's side sent along when placing this call, such as a note or urgency. */
    @Volatile
    var requestExtras: Bundle? = null
        internal set

    /** Why the user declined the call, when they gave a reason. */
    @Volatile
    var rejectReason: Int? = null
        private set

    override fun onReject(rejectReason: Int) {
        this.rejectReason = rejectReason
        end(DisconnectCause(DisconnectCause.REJECTED))
    }

    /** Where the user forwarded the ringing call, once they did. */
    @Volatile
    var deflectedTo: Uri? = null
        private set

    override fun onDeflect(address: Uri) {
        deflectedTo = address
        end(DisconnectCause(DisconnectCause.LOCAL))
    }

    /** The number the user transferred the call to without consulting, once they did. */
    @Volatile
    var transferredToNumber: Uri? = null
        private set

    override fun onTransfer(number: Uri, isConfirmationRequired: Boolean) {
        transferredToNumber = number
        end(DisconnectCause(DisconnectCause.LOCAL))
    }

    /** The network shows [message] under [id] on the user's call screen. */
    fun showDiagnosticMessage(id: Int, message: String) =
        FakeCarrier.onMain {
            sendConnectionEvent(
                android.telecom.Call.EVENT_DISPLAY_DIAGNOSTIC_MESSAGE,
                Bundle().apply {
                    putInt(android.telecom.Call.EXTRA_DIAGNOSTIC_MESSAGE_ID, id)
                    putCharSequence(android.telecom.Call.EXTRA_DIAGNOSTIC_MESSAGE, message)
                },
            )
        }

    /** The network takes the message under [id] off the user's call screen. */
    fun clearDiagnosticMessage(id: Int) =
        FakeCarrier.onMain {
            sendConnectionEvent(
                android.telecom.Call.EVENT_CLEAR_DIAGNOSTIC_MESSAGE,
                Bundle().apply { putInt(android.telecom.Call.EXTRA_DIAGNOSTIC_MESSAGE_ID, id) },
            )
        }

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

    /** The call this one was transferred to, once the user transferred it. */
    @Volatile
    var transferredTo: FakeConnection? = null
        private set

    override fun onTransfer(otherConnection: Connection) {
        transferredTo = otherConnection as? FakeConnection
        // The network connects the two other people and drops the user from both calls.
        (otherConnection as? FakeConnection)?.end(DisconnectCause(DisconnectCause.LOCAL))
        end(DisconnectCause(DisconnectCause.LOCAL))
    }

    override fun onPostDialContinue(proceed: Boolean) {
        if (proceed) playedTones += postDialDigits.toList()
    }

    /** The far end picks up an outgoing call; digits after a wait (;) then wait for the user. */
    fun answerFromFarEnd() =
        FakeCarrier.onMain {
            setActive()
            if (postDialDigits.isNotEmpty()) setPostDialWait(postDialDigits)
        }

    /** Starts the call with RTT on, as a network does for a call that was RTT from the start. */
    fun startRtt(stream: RttTextStream?) {
        stream?.let { rttStream = it }
        connectionProperties = connectionProperties or PROPERTY_IS_RTT
    }

    /** The far end agrees to the user's request to switch to RTT. */
    fun acceptRttRequest() =
        FakeCarrier.onMain {
            check(rttRequestedByUser) { "no RTT request" }
            rttRequestedByUser = false
            connectionProperties = connectionProperties or PROPERTY_IS_RTT
            sendRttInitiationSuccess()
        }

    /** The far end turns the user's request to switch to RTT down. */
    fun rejectRttRequest() =
        FakeCarrier.onMain {
            check(rttRequestedByUser) { "no RTT request" }
            rttRequestedByUser = false
            rttStream = null
            sendRttInitiationFailure(RttModifyStatus.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE)
        }

    /** The far end asks the user to switch the call to RTT. */
    fun requestRtt() =
        FakeCarrier.onMain {
            rttRequestAccepted = null
            sendRemoteRttRequest()
        }

    /** The far end turns RTT off; the call carries on as a voice call. */
    fun stopRttFromFarEnd() =
        FakeCarrier.onMain {
            rttStream = null
            connectionProperties = connectionProperties and PROPERTY_IS_RTT.inv()
            sendRttSessionRemotelyTerminated()
        }

    /** Whether the call still has RTT on, as the network sees it. */
    val isRtt: Boolean
        get() = connectionProperties and PROPERTY_IS_RTT != 0

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
    fun hangUpFromFarEnd(
        cause: Int = if (state == STATE_RINGING) DisconnectCause.MISSED else DisconnectCause.REMOTE
    ) = hangUpFromFarEnd(DisconnectCause(cause))

    /**
     * The network ends the call with [cause], which can carry a reason and a message for the user.
     */
    fun hangUpFromFarEnd(cause: DisconnectCause) = FakeCarrier.onMain { end(cause) }

    private fun end(cause: DisconnectCause) {
        (conference as? FakeConference)?.onConnectionEnded(this)
        setDisconnected(cause)
        destroy()
        FakeCarrier.forget(this)
    }
}
