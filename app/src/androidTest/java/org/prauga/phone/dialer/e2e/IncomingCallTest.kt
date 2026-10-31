/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.provider.CallLog
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
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier

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

        val call = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        call.hangUpFromFarEnd()

        assertEquals(CallLog.Calls.MISSED_TYPE, CallTests.waitFor { CallTests.latestCall(context, NUMBER, since) }.type)
    }

    private companion object {
        const val NUMBER = "+15550100001"
        const val NUMBER_TAIL = "0001"
    }
}
