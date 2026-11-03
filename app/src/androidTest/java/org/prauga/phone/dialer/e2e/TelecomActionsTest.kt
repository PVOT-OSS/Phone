/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.app.calllog.CallLogNotificationsService
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier

/**
 * Telecom actions the platform allows the default phone app without MODIFY_PHONE_STATE, which a
 * store app never holds.
 */
@RunWith(AndroidJUnit4::class)
class TelecomActionsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val missedCounts = LinkedBlockingQueue<Int>()
    private val missedCallReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                missedCounts += intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, -1)
            }
        }

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(DeviceRule())

    @Before
    fun listenForMissedCallCounts() {
        context.registerReceiver(
            missedCallReceiver,
            IntentFilter(TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION),
            Context.RECEIVER_EXPORTED,
        )
    }

    @After fun stopListening() = context.unregisterReceiver(missedCallReceiver)

    @Test
    fun silencingTheRingerReachesTelecom() {
        val before = silenceEvents()
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        val far = CallTests.waitForRinging()

        InCall.onMain { TelecomUtil.silenceRinger(context) }

        CallTests.waitFor { if (silenceEvents() > before) true else null }
        far.hangUpFromFarEnd()
    }

    @Test
    fun readingMissedCallsResetsTelecomsCount() {
        markMissedCallsRead()

        missCall()
        check(nextMissedCount() == 1) { "first missed call not counted as 1" }

        markMissedCallsRead()
        missCall()
        val count = nextMissedCount()
        check(count == 1) { "Telecom counted $count missed calls after the earlier one was read" }
    }

    private fun missCall() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        CallTests.waitForRinging().hangUpFromFarEnd()
        InCall.waitForNoCalls()
    }

    /** Marks every missed call read, as opening Recents does, and waits for Telecom to settle. */
    private fun markMissedCallsRead() {
        CallLogNotificationsService.cancelAllMissedCalls(context)
        Thread.sleep(SETTLE_MILLIS)
        missedCounts.clear()
    }

    private fun nextMissedCount(): Int {
        // Telecom may first send a count of 0 when the earlier calls were read.
        while (true) {
            val count =
                checkNotNull(missedCounts.poll(CallTests.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    "no missed call broadcast"
                }
            if (count != 0) return count
        }
    }

    private fun silenceEvents(): Int =
        Regex(" - SILENCE\\b").findAll(Shell.run("dumpsys telecom")).count()

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }
}
