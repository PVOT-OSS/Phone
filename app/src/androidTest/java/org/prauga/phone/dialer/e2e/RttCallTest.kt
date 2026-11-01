/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.call.DialerCall
import org.prauga.phone.incallui.call.state.DialerCallState

@RunWith(AndroidJUnit4::class)
class RttCallTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun rttCallShowsTheChatAndCarriesTextBothWays() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), rtt = true))
        val far = CallTests.waitForRinging()
        CallTests.answerRingingCall()
        val call = InCall.waitForState(DialerCallState.ACTIVE)
        waitForRtt(call)

        val input = findView("rtt_chat_input")
        far.typeRtt("hello from far")
        checkNotNull(deviceRule.device.wait(Until.findObject(By.textContains("hello from far")), CallTests.TIMEOUT_MILLIS)) {
            "far end's text not shown"
        }

        input.text = "hi there"
        CallTests.waitFor { far.readRtt().takeIf { it.contains("hi there") } }
    }

    @Test
    fun switchToRttAcceptedByTheFarEnd() {
        val (call, far) = connectedVoiceCall()

        InCall.onMain { call.sendRttUpgradeRequest() }
        CallTests.waitFor { if (far.rttRequestedByUser) true else null }
        far.acceptRttRequest()

        waitForRtt(call)
        far.typeRtt("ok")
        CallTests.waitFor { deviceRule.device.findObject(By.text("ok")) }
    }

    @Test
    fun switchToRttTurnedDownByTheFarEnd() {
        val (call, far) = connectedVoiceCall()

        InCall.onMain { call.sendRttUpgradeRequest() }
        CallTests.waitFor { if (far.rttRequestedByUser) true else null }
        far.rejectRttRequest()

        Thread.sleep(SETTLE_MILLIS)
        check(!InCall.onMain { call.isActiveRttCall }) { "call switched to RTT" }
        InCall.waitForState(DialerCallState.ACTIVE)
    }

    @Test
    fun farEndAsksForRttAndTheUserAccepts() {
        val (call, far) = connectedVoiceCall()

        far.requestRtt()
        findView("rtt_button_accept_request").click()

        CallTests.waitFor { far.rttRequestAccepted?.takeIf { it } }
        waitForRtt(call)
    }

    @Test
    fun farEndAsksForRttAndTheUserDeclines() {
        val (call, far) = connectedVoiceCall()

        far.requestRtt()
        findView("rtt_button_decline_request").click()

        CallTests.waitFor { far.rttRequestAccepted?.takeIf { !it } }
        check(!InCall.onMain { call.isActiveRttCall }) { "call switched to RTT" }
    }

    private fun connectedVoiceCall(): Pair<DialerCall, FakeConnection> {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        far.answerFromFarEnd()
        return InCall.waitForState(DialerCallState.ACTIVE) to far
    }

    private fun waitForRtt(call: DialerCall) = CallTests.waitFor { if (InCall.onMain { call.isActiveRttCall }) true else null }

    private fun findView(id: String): UiObject2 =
        checkNotNull(deviceRule.device.wait(Until.findObject(By.res(context.packageName, id)), CallTests.TIMEOUT_MILLIS)) {
            "$id not shown"
        }

    private companion object {
        const val SETTLE_MILLIS = 1_500L
    }
}
