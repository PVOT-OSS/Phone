/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.telecom.CallAudioState
import android.telecom.Connection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.audiomode.AudioModeProvider
import org.prauga.phone.incallui.call.DialerCall
import org.prauga.phone.incallui.call.TelecomAdapter
import org.prauga.phone.incallui.call.state.DialerCallState

@RunWith(AndroidJUnit4::class)
class InCallControlsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(DeviceRule())

    @Test
    fun holdAndResume() {
        val (call, far) = connectedCall()

        InCall.onMain { call.hold() }
        InCall.waitForState(DialerCallState.ONHOLD)
        CallTests.waitFor { far.takeIf { it.state == Connection.STATE_HOLDING } }

        InCall.onMain { call.unhold() }
        InCall.waitForState(DialerCallState.ACTIVE)
        CallTests.waitFor { far.takeIf { it.state == Connection.STATE_ACTIVE } }
    }

    @Test
    fun muteAndUnmute() {
        connectedCall()

        InCall.onMain { TelecomAdapter.getInstance().mute(true) }
        CallTests.waitFor { if (audioState()?.isMuted == true) true else null }

        InCall.onMain { TelecomAdapter.getInstance().mute(false) }
        CallTests.waitFor { if (audioState()?.isMuted == false) true else null }
    }

    @Test
    fun switchToSpeakerAndBack() {
        connectedCall()

        InCall.onMain { TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
        CallTests.waitFor { if (audioState()?.route == CallAudioState.ROUTE_SPEAKER) true else null }

        InCall.onMain { TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE) }
        CallTests.waitFor { if (audioState()?.route != CallAudioState.ROUTE_SPEAKER) true else null }
    }

    @Test
    fun keypadTonesReachTheFarEnd() {
        val (call, far) = connectedCall()

        for (digit in "15#") {
            InCall.onMain {
                TelecomAdapter.getInstance().playDtmfTone(call.id, digit)
                TelecomAdapter.getInstance().stopDtmfTone(call.id)
            }
        }

        CallTests.waitFor { if (far.playedTones.joinToString("") == "15#") true else null }
    }

    private fun connectedCall(): Pair<DialerCall, FakeConnection> {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        far.answerFromFarEnd()
        return InCall.waitForState(DialerCallState.ACTIVE) to far
    }

    private fun audioState(): CallAudioState? = InCall.onMain { AudioModeProvider.getInstance().audioState }
}
