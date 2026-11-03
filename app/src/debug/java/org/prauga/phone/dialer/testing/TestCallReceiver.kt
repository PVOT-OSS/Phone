/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.prauga.phone.dialer.screening.CallerCategory
import org.prauga.phone.dialer.screening.CategoryAction
import org.prauga.phone.dialer.screening.ScreeningSettings
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.state.DialerCallState

/**
 * Lets host-side tests act as the user over adb. Only the shell can send to it (DUMP permission).
 * Every action answers with the calls the app knows about, one "state number" pair per line.
 */
class TestCallReceiver : BroadcastReceiver() {

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
            ACTION_DUMP -> Unit
        }
        resultData =
            calls.allCalls.joinToString("\n") {
                "${DialerCallState.toString(it.state)} ${it.number}"
            }
    }

    private companion object {
        const val PREFIX = "org.prauga.phone.testing."
        const val ACTION_ANSWER = PREFIX + "ANSWER"
        const val ACTION_DECLINE = PREFIX + "DECLINE"
        const val ACTION_HANG_UP = PREFIX + "HANG_UP"
        const val ACTION_SET_SCREENING = PREFIX + "SET_SCREENING"
        const val ACTION_DUMP = PREFIX + "DUMP"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_ACTION = "action"
    }
}
