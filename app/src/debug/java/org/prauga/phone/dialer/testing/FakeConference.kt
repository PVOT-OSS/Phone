/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.telecom.Conference
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle

/** A conference the fake carrier hosts when the user merges its calls. */
class FakeConference(account: PhoneAccountHandle) : Conference(account) {

    init {
        connectionCapabilities = Connection.CAPABILITY_HOLD or
            Connection.CAPABILITY_SUPPORT_HOLD or
            Connection.CAPABILITY_MUTE or
            Connection.CAPABILITY_MANAGE_CONFERENCE
    }

    override fun onDisconnect() {
        connections.toList().forEach { it.onDisconnect() }
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onSeparate(connection: Connection) {
        removeConnection(connection)
        if (connections.size < 2) {
            connections.toList().forEach { removeConnection(it) }
            setDisconnected(DisconnectCause(DisconnectCause.OTHER))
            destroy()
        }
    }

    override fun onHold() {
        connections.forEach { it.setOnHold() }
        setOnHold()
    }

    override fun onUnhold() {
        connections.forEach { it.setActive() }
        setActive()
    }
}
