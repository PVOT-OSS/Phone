/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.app.NotificationManager
import android.provider.CallLog
import android.telecom.Connection
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
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.incallui.call.CallList

@RunWith(AndroidJUnit4::class)
class IncomingCallTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun ringingCallShowsCallerAndBecomesMissed() {
        val since = System.currentTimeMillis()
        FakeCarrier.ring(context, FakeCallSpec(number = NUMBER))

        val caller = deviceRule.device.wait(Until.findObject(By.textContains(NUMBER_TAIL)), CallTests.TIMEOUT_MILLIS)
        assertNotNull("incoming call screen did not show the caller", caller)

        CallTests.waitForRinging().hangUpFromFarEnd()

        assertEquals(CallLog.Calls.MISSED_TYPE, CallTests.waitFor { CallTests.latestCall(context, NUMBER, since) }.type)
    }

    @Test
    fun answeredCallIsLoggedAsIncoming() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()
        FakeCarrier.ring(context, FakeCallSpec(number = number))

        CallTests.answerRingingCall()
        val call = CallTests.waitFor { FakeCarrier.connections.firstOrNull()?.takeIf { it.state == Connection.STATE_ACTIVE } }
        call.hangUpFromFarEnd()

        InCall.waitForNoCalls()
        assertEquals(CallLog.Calls.INCOMING_TYPE, CallTests.waitFor { CallTests.latestCall(context, number, since) }.type)
    }

    @Test
    fun declinedCallIsLoggedAsRejected() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()
        FakeCarrier.ring(context, FakeCallSpec(number = number))

        CallTests.waitForRinging()
        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).reject(false, null) }

        InCall.waitForNoCalls()
        assertEquals(CallLog.Calls.REJECTED_TYPE, CallTests.waitFor { CallTests.latestCall(context, number, since) }.type)
    }

    @Test
    fun missedCallPostsNotification() {
        FakeCarrier.ring(context, FakeCallSpec(number = CallTests.freshNumber()))
        CallTests.waitForRinging().hangUpFromFarEnd()

        val notifications = context.getSystemService(NotificationManager::class.java)
        CallTests.waitFor {
            notifications.activeNotifications.firstOrNull { it.notification.channelId == NotificationChannelId.MISSED_CALL }
        }
    }

    private companion object {
        const val NUMBER = "+15550100001"
        const val NUMBER_TAIL = "0001"
    }
}
