/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.net.Uri
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.call.state.DialerCallState

/** What the user is told when the network ends a call they placed. */
@RunWith(AndroidJUnit4::class)
class DisconnectMessageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun networkErrorShowsTheNetworksMessage() {
        dialing()
            .hangUpFromFarEnd(DisconnectCause(DisconnectCause.ERROR, null, NETWORK_MESSAGE, null))

        findText(NETWORK_MESSAGE)
        findButton(POSITIVE, context.getString(android.R.string.cancel)).click()
        InCall.waitForNoCalls()
    }

    @Test
    fun restrictedCallShowsTheNetworksMessage() {
        dialing()
            .hangUpFromFarEnd(
                DisconnectCause(DisconnectCause.RESTRICTED, null, RESTRICTED_MESSAGE, null)
            )

        findText(RESTRICTED_MESSAGE)
    }

    @Test
    fun wifiCallingOffOffersToTurnItOn() {
        val cause =
            DisconnectCause(DisconnectCause.ERROR, null, WIFI_MESSAGE, "REASON_WIFI_ON_BUT_WFC_OFF")
        dialing().hangUpFromFarEnd(cause)

        findText(WIFI_MESSAGE)
        findButton(POSITIVE, context.getString(R.string.incall_enable_wifi_calling_button))
    }

    @Test
    fun blockedVideoCallOffersAVoiceCall() {
        val number = CallTests.freshNumber()
        val extras =
            Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    FakeCarrier.accountHandle(context),
                )
                putInt(
                    TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                    VideoProfile.STATE_BIDIRECTIONAL,
                )
            }
        context
            .getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), extras)
        val videoCall = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        InCall.waitForState(DialerCallState.DIALING)

        videoCall.hangUpFromFarEnd(
            DisconnectCause(
                DisconnectCause.ERROR,
                null,
                null,
                DisconnectCause.REASON_IMS_ACCESS_BLOCKED,
            )
        )
        findText(context.getString(R.string.video_call_not_available_title))
        findButton(POSITIVE, context.getString(R.string.voice_call)).click()

        val voiceCall =
            CallTests.waitFor { FakeCarrier.connections.firstOrNull()?.takeIf { it !== videoCall } }
        check(voiceCall.spec.number == number) { "voice call went to ${voiceCall.spec.number}" }
        check(voiceCall.videoState == VideoProfile.STATE_AUDIO_ONLY) {
            "voice call has video state ${voiceCall.videoState}"
        }
    }

    @Test
    fun ordinaryHangUpShowsNoMessage() {
        dialing().hangUpFromFarEnd(DisconnectCause.REMOTE)

        InCall.waitForNoCalls()
        assertNull(
            "a message was shown",
            deviceRule.device.wait(Until.findObject(By.res("android", "message")), QUIET_MILLIS),
        )
    }

    private fun dialing(): FakeConnection {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        return CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
    }

    private fun findText(text: String): UiObject2 =
        checkNotNull(
            deviceRule.device.wait(Until.findObject(By.text(text)), CallTests.TIMEOUT_MILLIS)
        ) {
            "\"$text\" not shown"
        }

    /**
     * A dialog button; its label is compared ignoring case, as buttons may be shown in capitals.
     */
    private fun findButton(id: String, label: String): UiObject2 {
        // An AlertDialog's button by its id, or a Compose dialog's by its label in any case.
        val byLabel = By.text(Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE))
        val button =
            deviceRule.device.wait(Until.findObject(By.res("android", id)), SHORT_WAIT_MILLIS)
                ?: checkNotNull(
                    deviceRule.device.wait(Until.findObject(byLabel), CallTests.TIMEOUT_MILLIS)
                ) {
                    "button \"$label\" not shown"
                }
        check(button.text.equals(label, ignoreCase = true)) {
            "button says \"${button.text}\", expected \"$label\""
        }
        return button
    }

    private companion object {
        const val SHORT_WAIT_MILLIS = 1_000L
        const val POSITIVE = "button1"
        const val NETWORK_MESSAGE = "Not registered on network."
        const val RESTRICTED_MESSAGE = "Outgoing calls are restricted by FDN."
        const val WIFI_MESSAGE = "To place a call, turn on Wi-Fi calling."
        const val QUIET_MILLIS = 2_000L
    }
}
