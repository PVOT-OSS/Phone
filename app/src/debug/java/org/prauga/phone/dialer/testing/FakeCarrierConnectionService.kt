/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.telecom.Conference
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/** Creates the fake carrier's calls for Telecom. */
class FakeCarrierConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val spec =
            FakeCallSpec.fromExtras(
                request.extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
            )
        return FakeConnection(spec, isIncoming = true).also {
            if (spec.rtt) it.startRtt(request.rttTextStream)
            it.setRinging()
            FakeCarrier.remember(it)
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val spec =
            FakeCallSpec(
                number = request.address?.schemeSpecificPart,
                videoState = request.videoState,
                rtt = request.isRequestingRtt,
            )
        return FakeConnection(spec, isIncoming = false).also {
            it.requestExtras = request.extras
            if (spec.rtt) it.startRtt(request.rttTextStream)
            it.setDialing()
            FakeCarrier.remember(it)
        }
    }

    override fun onConference(connection1: Connection, connection2: Connection) {
        if (FakeCarrier.failMerges) {
            connection1.sendConnectionEvent(Connection.EVENT_CALL_MERGE_FAILED, null)
            return
        }
        val conference: Conference = FakeConference(FakeCarrier.accountHandle(this))
        conference.addConnection(connection1)
        conference.addConnection(connection2)
        connection1.setActive()
        connection2.setActive()
        conference.setActive()
        addConference(conference)
    }
}
