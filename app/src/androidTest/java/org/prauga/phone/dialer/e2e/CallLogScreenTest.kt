/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.MainComponent
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier

@RunWith(AndroidJUnit4::class)
class CallLogScreenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun missedCallAppearsInRecents() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()
        FakeCarrier.ring(context, FakeCallSpec(number = number))
        CallTests.waitForRinging().hangUpFromFarEnd()
        InCall.waitForNoCalls()
        CallTests.waitFor { CallTests.latestCall(context, number, since) }

        context.startActivity(MainComponent.getShowCallLogIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        assertNotNull(
            "missed call is not listed in recents",
            deviceRule.device.wait(Until.findObject(By.textContains(number.takeLast(4))), CallTests.TIMEOUT_MILLIS),
        )
    }

    @Test
    fun missedFilterShowsOnlyMissedCalls() {
        val answered = CallTests.freshNumber()
        FakeCarrier.ring(context, FakeCallSpec(number = answered))
        CallTests.answerRingingCall()
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.hangUpFromFarEnd()
        InCall.waitForNoCalls()
        val missed = CallTests.freshNumber()
        FakeCarrier.ring(context, FakeCallSpec(number = missed))
        CallTests.waitForRinging().hangUpFromFarEnd()
        InCall.waitForNoCalls()

        context.startActivity(MainComponent.getShowCallLogIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val device = deviceRule.device
        assertNotNull("answered call not listed", device.wait(Until.findObject(By.textContains(answered.takeLast(4))), CallTests.TIMEOUT_MILLIS))
        checkNotNull(device.findObject(By.res(context.packageName, "call_log_filter_missed"))) { "no Missed chip" }.click()

        assertNotNull("missed call not listed", device.wait(Until.findObject(By.textContains(missed.takeLast(4))), CallTests.TIMEOUT_MILLIS))
        check(device.wait(Until.gone(By.textContains(answered.takeLast(4))), CallTests.TIMEOUT_MILLIS)) { "answered call still listed under Missed" }
    }

    @Test
    fun recentCallSaysJustNow() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()
        FakeCarrier.ring(context, FakeCallSpec(number = number))
        CallTests.waitForRinging().hangUpFromFarEnd()
        InCall.waitForNoCalls()
        CallTests.waitFor { CallTests.latestCall(context, number, since) }

        context.startActivity(MainComponent.getShowCallLogIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        assertNotNull(
            "recent call doesn't say \"Just now\"",
            deviceRule.device.wait(
                Until.findObject(By.textContains(context.getString(R.string.call_log_time_just_now))),
                CallTests.TIMEOUT_MILLIS,
            ),
        )
    }
}
