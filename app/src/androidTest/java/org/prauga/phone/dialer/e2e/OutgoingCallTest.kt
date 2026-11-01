/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.app.NotificationManager
import android.provider.CallLog
import android.telecom.DisconnectCause
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.notification.NotificationChannelId
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.incallui.call.state.DialerCallState

@RunWith(AndroidJUnit4::class)
class OutgoingCallTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun answeredCallIsLoggedAsOutgoing() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()

        InCall.place(context, number)
        InCall.waitForState(DialerCallState.DIALING)
        assertNotNull(
            "call screen did not show the number",
            deviceRule.device.wait(Until.findObject(By.textContains(number.takeLast(4))), CallTests.TIMEOUT_MILLIS),
        )
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.answerFromFarEnd()
        val call = InCall.waitForState(DialerCallState.ACTIVE)
        InCall.onMain { call.disconnect() }

        InCall.waitForNoCalls()
        assertEquals(CallLog.Calls.OUTGOING_TYPE, CallTests.waitFor { CallTests.latestCall(context, number, since) }.type)
    }

    @Test
    fun connectedCallShowsOngoingNotification() {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.answerFromFarEnd()
        InCall.waitForState(DialerCallState.ACTIVE)

        val notifications = context.getSystemService(NotificationManager::class.java)
        CallTests.waitFor {
            notifications.activeNotifications.firstOrNull { it.notification.channelId == NotificationChannelId.ONGOING_CALL }
        }
    }

    @Test
    fun busyCalleeEndsTheCall() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()

        InCall.place(context, number)
        InCall.waitForState(DialerCallState.DIALING)
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.hangUpFromFarEnd(DisconnectCause.BUSY)

        InCall.waitForNoCalls()
        assertEquals(CallLog.Calls.OUTGOING_TYPE, CallTests.waitFor { CallTests.latestCall(context, number, since) }.type)
    }
}
