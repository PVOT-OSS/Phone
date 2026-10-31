/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.incallui.call.CallList

/**
 * Screens incoming calls before they ring, following the user's choice for each
 * [CallerCategory]. Calls only ring differently when the user picked another action; any failure
 * lets the call ring.
 */
class PhoneCallScreeningService : CallScreeningService() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        val settings = ScreeningSettings.from(this)
        if (!isDefaultDialer() || CallerCategory.entries.all { settings.actionFor(it) == CategoryAction.RING }) {
            respondToCall(callDetails, responseFor(CategoryAction.RING))
            return
        }
        val provider = IncomingCallFactsProvider(applicationContext)
        val hasActiveCall = CallList.getInstance().activeCall != null
        executor.execute {
            val decision = runCatching {
                ScreeningPolicy.decide(provider.factsFor(callDetails, hasActiveCall), settings::actionFor)
            }
                .getOrElse {
                    LogUtil.e(TAG, "screening failed, letting the call ring", it)
                    ScreeningDecision(CategoryAction.RING, ScreeningReason.None)
                }
            LogUtil.i(TAG, "action: %s, reason: %s", decision.action, decision.reason)
            respondToCall(callDetails, responseFor(decision.action))
        }
    }

    private fun isDefaultDialer(): Boolean =
        getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun responseFor(action: CategoryAction): CallResponse = when (action) {
        CategoryAction.RING -> CallResponse.Builder().build()
        CategoryAction.SILENCE -> CallResponse.Builder().setSilenceCall(true).build()
        CategoryAction.DECLINE -> CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .build()
    }

    private companion object {
        const val TAG = "PhoneCallScreeningService"
    }
}
