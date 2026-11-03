/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import org.prauga.phone.dialer.screening.CallerCategory
import org.prauga.phone.dialer.screening.CategoryAction
import org.prauga.phone.dialer.screening.ScreeningSettings
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.state.DialerCallState

/**
 * Lets host-side tests act as the user over adb. Only the shell can send to it (DUMP permission).
 * Every action answers with the calls the app knows about, one "state number" pair per line, then
 * any DTMF tones the fake carrier heard. RING and PLACE use the fake carrier, never a SIM.
 */
class TestCallReceiver : BroadcastReceiver() {

    // PLACE runs only from adb while Phone is the default phone app, which holds CALL_PHONE.
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val calls = CallList.getInstance()
        when (intent.action) {
            ACTION_ANSWER -> calls.incomingCall?.answer()
            ACTION_DECLINE -> calls.incomingCall?.reject(false, null)
            ACTION_HANG_UP ->
                (calls.activeCall ?: calls.outgoingCall ?: calls.firstCall)?.disconnect()
            ACTION_SET_SCREENING -> {
                val category =
                    CallerCategory.valueOf(
                        checkNotNull(intent.getStringExtra(EXTRA_CATEGORY)).uppercase()
                    )
                val action =
                    CategoryAction.valueOf(
                        checkNotNull(intent.getStringExtra(EXTRA_ACTION)).uppercase()
                    )
                ScreeningSettings.from(context).setAction(category, action)
            }
            ACTION_REGISTER_CARRIER -> FakeCarrier.register(context)
            ACTION_UNREGISTER_CARRIER -> FakeCarrier.unregister(context)
            ACTION_RING ->
                FakeCarrier.ring(
                    context,
                    FakeCallSpec(
                        number = intent.getStringExtra(EXTRA_NUMBER),
                        presentation =
                            intent.getIntExtra(
                                EXTRA_PRESENTATION,
                                TelecomManager.PRESENTATION_ALLOWED,
                            ),
                    ),
                )
            ACTION_PLACE ->
                context
                    .getSystemService(TelecomManager::class.java)
                    .placeCall(
                        Uri.fromParts(
                            PhoneAccount.SCHEME_TEL,
                            checkNotNull(intent.getStringExtra(EXTRA_NUMBER)),
                            null,
                        ),
                        Bundle().apply {
                            putParcelable(
                                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                                FakeCarrier.accountHandle(context),
                            )
                        },
                    )
            ACTION_FAR_END_ANSWER ->
                FakeCarrier.connections.lastOrNull { !it.isIncoming }?.answerFromFarEnd()
            ACTION_FAR_END_HANG_UP -> FakeCarrier.connections.lastOrNull()?.hangUpFromFarEnd()
            ACTION_DUMP -> Unit
        }
        val tones = FakeCarrier.connections.joinToString("") { it.playedTones.joinToString("") }
        resultData =
            calls.allCalls.joinToString("\n") {
                "${DialerCallState.toString(it.state)} ${it.number}"
            } + if (tones.isEmpty()) "" else "\ntones $tones"
    }

    private companion object {
        const val PREFIX = "org.prauga.phone.testing."
        const val ACTION_ANSWER = PREFIX + "ANSWER"
        const val ACTION_DECLINE = PREFIX + "DECLINE"
        const val ACTION_HANG_UP = PREFIX + "HANG_UP"
        const val ACTION_SET_SCREENING = PREFIX + "SET_SCREENING"
        const val ACTION_DUMP = PREFIX + "DUMP"
        const val ACTION_REGISTER_CARRIER = PREFIX + "REGISTER_CARRIER"
        const val ACTION_UNREGISTER_CARRIER = PREFIX + "UNREGISTER_CARRIER"
        const val ACTION_RING = PREFIX + "RING"
        const val ACTION_PLACE = PREFIX + "PLACE"
        const val ACTION_FAR_END_ANSWER = PREFIX + "FAR_END_ANSWER"
        const val ACTION_FAR_END_HANG_UP = PREFIX + "FAR_END_HANG_UP"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_PRESENTATION = "presentation"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_ACTION = "action"
    }
}
