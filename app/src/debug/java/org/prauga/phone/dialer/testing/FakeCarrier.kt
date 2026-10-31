/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import java.util.concurrent.CopyOnWriteArrayList

/** A pretend network for tests: a calling account whose calls go through the real Telecom stack. */
object FakeCarrier {

    private const val ACCOUNT_ID = "fake_carrier"
    private val live = CopyOnWriteArrayList<FakeConnection>()

    /** Calls currently on the fake carrier, oldest first. */
    val connections: List<FakeConnection> get() = live.toList()

    fun accountHandle(context: Context) =
        PhoneAccountHandle(ComponentName(context, FakeCarrierConnectionService::class.java), ACCOUNT_ID)

    /** Registers the account; it still has to be enabled, for example with the telecom shell command. */
    fun register(context: Context) {
        val account = PhoneAccount.builder(accountHandle(context), "Fake carrier")
            .setCapabilities(
                PhoneAccount.CAPABILITY_CALL_PROVIDER or
                    PhoneAccount.CAPABILITY_VIDEO_CALLING or
                    PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                    PhoneAccount.CAPABILITY_RTT,
            )
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .build()
        telecom(context).registerPhoneAccount(account)
    }

    fun unregister(context: Context) {
        live.forEach { it.hangUpFromFarEnd() }
        telecom(context).unregisterPhoneAccount(accountHandle(context))
    }

    /** Rings the device with a call described by [spec]. */
    fun ring(context: Context, spec: FakeCallSpec) {
        val extras = Bundle().apply { putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, spec.toExtras()) }
        telecom(context).addNewIncomingCall(accountHandle(context), extras)
    }

    internal fun remember(connection: FakeConnection) {
        live += connection
    }

    internal fun forget(connection: FakeConnection) {
        live -= connection
    }

    private fun telecom(context: Context) = context.getSystemService(TelecomManager::class.java)
}
