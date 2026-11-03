/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.incallui.call.state.DialerCallState

/**
 * The call screen keeps the call and what it shows when the device rotates or the screen is
 * rebuilt.
 */
@RunWith(AndroidJUnit4::class)
class ConfigurationChangeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun callCarriesOnWhenTheDeviceRotates() {
        val number = CallTests.freshNumber()
        InCall.place(context, number)
        CallTests.waitFor { FakeCarrier.connections.firstOrNull() }.answerFromFarEnd()
        InCall.waitForState(DialerCallState.ACTIVE)
        showsText(number.takeLast(4))

        try {
            deviceRule.device.setOrientationLeft()
            InCall.waitForState(DialerCallState.ACTIVE)
            showsText(number.takeLast(4))
        } finally {
            deviceRule.device.setOrientationNatural()
            deviceRule.device.unfreezeRotation()
        }
        InCall.waitForState(DialerCallState.ACTIVE)
        showsText(number.takeLast(4))
    }

    @Test
    fun rttChatSurvivesTheScreenBeingRebuilt() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), rtt = true))
        val far = CallTests.waitForRinging()
        CallTests.answerRingingCall()
        InCall.waitForState(DialerCallState.ACTIVE)
        far.typeRtt("before")
        showsText("before")

        val nightMode = Shell.run("cmd uimode night").substringAfter(": ").trim()
        try {
            Shell.run("cmd uimode night ${if (nightMode == "yes") "no" else "yes"}")
            Thread.sleep(SETTLE_MILLIS)
            showsText("before")

            far.typeRtt(" and after")
            showsText("before and after")
            val input =
                checkNotNull(
                    deviceRule.device.wait(
                        Until.findObject(By.res(context.packageName, "rtt_chat_input")),
                        CallTests.TIMEOUT_MILLIS,
                    )
                ) {
                    "RTT input not shown"
                }
            input.text = "still here"
            CallTests.waitFor { far.readRtt().takeIf { it.contains("still here") } }
        } finally {
            Shell.run("cmd uimode night $nightMode")
        }
    }

    private fun showsText(text: String) =
        assertNotNull(
            "\"$text\" not shown",
            deviceRule.device.wait(
                Until.findObject(By.textContains(text)),
                CallTests.TIMEOUT_MILLIS,
            ),
        )

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }
}
