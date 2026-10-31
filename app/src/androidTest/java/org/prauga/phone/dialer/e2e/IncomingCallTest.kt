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

        val caller = deviceRule.device.wait(Until.findObject(By.textContains(NUMBER_TAIL)), TIMEOUT_MILLIS)
        assertNotNull("incoming call screen did not show the caller", caller)

        val call = waitFor { FakeCarrier.connections.firstOrNull() }
        call.hangUpFromFarEnd()

        assertEquals(CallLog.Calls.MISSED_TYPE, waitFor { latestCallType(since) })
    }

    private fun latestCallType(since: Long): Int? = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.TYPE),
        "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.NUMBER} = ?",
        arrayOf(since.toString(), NUMBER),
        "${CallLog.Calls.DATE} DESC",
    )?.use { if (it.moveToFirst()) it.getInt(0) else null }

    private fun <T : Any> waitFor(block: () -> T?): T {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            block()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        return checkNotNull(block()) { "timed out" }
    }

    private companion object {
        const val NUMBER = "+15550100001"
        const val NUMBER_TAIL = "0001"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 200L
    }
}
