/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.telecom.Connection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.TelecomAdapter
import org.prauga.phone.incallui.call.state.DialerCallState

@RunWith(AndroidJUnit4::class)
class TwoCallsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(DeviceRule())

    @Test
    fun answeringAWaitingCallHoldsTheFirst() {
        twoCalls()
    }

    @Test
    fun switchingToTheHeldCallHoldsTheOther() {
        val (first, second) = twoCalls()

        InCall.onMain { checkNotNull(CallList.getInstance().backgroundCall).unhold() }

        waitForState(first, Connection.STATE_ACTIVE)
        waitForState(second, Connection.STATE_HOLDING)
    }

    @Test
    fun mergeIntoAConferenceAndSeparate() {
        twoCalls()
        val active = InCall.waitForState(DialerCallState.ACTIVE)

        InCall.onMain { TelecomAdapter.getInstance().merge(active.id) }
        val conference = CallTests.waitFor {
            InCall.onMain { CallList.getInstance().allCalls.firstOrNull { it.isConferenceCall && it.childCallIds.size == 2 } }
        }

        val participant = InCall.onMain { checkNotNull(CallList.getInstance().getCallById(conference.childCallIds.first())) }
        InCall.onMain { participant.splitFromConference() }
        CallTests.waitFor { if (InCall.onMain { CallList.getInstance().allCalls.none { it.isConferenceCall } }) true else null }
    }

    private fun twoCalls(): Pair<FakeConnection, FakeConnection> {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        val first = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        first.answerFromFarEnd()
        InCall.waitForState(DialerCallState.ACTIVE)

        FakeCarrier.ring(context, FakeCallSpec(number = CallTests.freshNumber()))
        InCall.waitForState(DialerCallState.INCOMING)
        val second = CallTests.waitFor { FakeCarrier.connections.getOrNull(1) }
        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).answer() }

        waitForState(first, Connection.STATE_HOLDING)
        waitForState(second, Connection.STATE_ACTIVE)
        return first to second
    }

    private fun waitForState(connection: FakeConnection, state: Int) =
        CallTests.waitFor { connection.takeIf { it.state == state } }
}
