/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

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
        val spec = FakeCallSpec.fromExtras(request.extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS))
        return FakeConnection(spec, isIncoming = true).also {
            it.setRinging()
            FakeCarrier.remember(it)
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val spec = FakeCallSpec(number = request.address?.schemeSpecificPart, videoState = request.videoState)
        return FakeConnection(spec, isIncoming = false).also {
            it.setDialing()
            FakeCarrier.remember(it)
        }
    }
}
