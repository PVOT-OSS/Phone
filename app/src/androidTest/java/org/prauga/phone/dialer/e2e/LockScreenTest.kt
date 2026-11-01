/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.app.KeyguardManager
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
import org.prauga.phone.incallui.call.state.DialerCallState

/** Calls that ring while the phone is locked with its screen off. */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun ringingCallWakesThePhoneAndShowsOverTheLockScreen() {
        val number = CallTests.freshNumber()
        lock()

        FakeCarrier.ring(context, FakeCallSpec(number))

        assertNotNull(
            "caller not shown over the lock screen",
            deviceRule.device.wait(Until.findObject(By.textContains(number.takeLast(4))), CallTests.TIMEOUT_MILLIS),
        )
        check(deviceRule.device.isScreenOn) { "screen stayed off" }
        check(keyguard.isKeyguardLocked) { "phone was unlocked" }

        CallTests.answerRingingCall()
        InCall.waitForState(DialerCallState.ACTIVE)
        check(keyguard.isKeyguardLocked) { "answering unlocked the phone" }
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.hangUpFromFarEnd()
        InCall.waitForNoCalls()
    }

    @Test
    fun callMissedWhileLockedIsLoggedAsMissed() {
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()
        lock()

        FakeCarrier.ring(context, FakeCallSpec(number))
        CallTests.waitForRinging().hangUpFromFarEnd()

        assertEquals(CallLog.Calls.MISSED_TYPE, CallTests.waitFor { CallTests.latestCall(context, number, since) }.type)
        check(keyguard.isKeyguardLocked) { "phone was unlocked" }
    }

    private fun lock() {
        deviceRule.device.sleep()
        CallTests.waitFor { if (!deviceRule.device.isScreenOn && keyguard.isKeyguardLocked) true else null }
    }
}
