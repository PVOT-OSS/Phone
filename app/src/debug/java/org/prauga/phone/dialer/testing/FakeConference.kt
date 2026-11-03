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
        connectionCapabilities =
            Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_MUTE or
                Connection.CAPABILITY_MANAGE_CONFERENCE
    }

    /** Numbers the user added straight to the conference, in order. */
    val addedParticipants = java.util.concurrent.CopyOnWriteArrayList<android.net.Uri>()

    /** Lets the user add people straight to the conference, as a network-hosted one does. */
    fun acceptParticipants() =
        FakeCarrier.onMain {
            connectionCapabilities = connectionCapabilities or Connection.CAPABILITY_ADD_PARTICIPANT
        }

    override fun onAddConferenceParticipants(participants: List<android.net.Uri>) {
        addedParticipants += participants
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

    /** A call in the conference ended; the conference ends with its last call. */
    fun onConnectionEnded(connection: Connection) {
        removeConnection(connection)
        if (connections.isEmpty()) {
            setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
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
