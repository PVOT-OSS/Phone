/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.os.Bundle
import android.telecom.Connection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.InCallPresenter
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.TelecomAdapter
import org.prauga.phone.incallui.call.state.DialerCallState

@RunWith(AndroidJUnit4::class)
class TwoCallsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val toasts = ToastWatcher()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule())
            .around(FakeCarrierRule())
            .around(deviceRule)
            .around(toasts)

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
        val conference =
            CallTests.waitFor {
                InCall.onMain {
                    CallList.getInstance().allCalls.firstOrNull {
                        it.isConferenceCall && it.childCallIds.size == 2
                    }
                }
            }

        val participant =
            InCall.onMain {
                checkNotNull(CallList.getInstance().getCallById(conference.childCallIds.first()))
            }
        InCall.onMain { participant.splitFromConference() }
        CallTests.waitFor {
            if (InCall.onMain { CallList.getInstance().allCalls.none { it.isConferenceCall } }) true
            else null
        }
    }

    @Test
    fun heldCallEndedByTheOtherSideIsAnnounced() {
        val (held, active) = twoCalls()

        held.hangUpFromFarEnd()

        toasts.waitFor(context.getString(R.string.incall_held_call_disconnected))
        waitForState(active, Connection.STATE_ACTIVE)
    }

    @Test
    fun failedMergeIsAnnounced() {
        twoCalls()
        FakeCarrier.failMerges = true
        val active = InCall.waitForState(DialerCallState.ACTIVE)

        InCall.onMain { TelecomAdapter.getInstance().merge(active.id) }

        toasts.waitFor(context.getString(R.string.incall_merge_failed))
        check(InCall.onMain { CallList.getInstance().allCalls.none { it.isConferenceCall } }) {
            "calls were merged"
        }
    }

    @Test
    fun ringingCallSaysAnsweringEndsTheOtherAppsCall() {
        connectedCall()
        val extras =
            Bundle().apply {
                putBoolean(EXTRA_ANSWERING_DROPS_FG_CALL, true)
                putString(EXTRA_ANSWERING_DROPS_FG_CALL_APP_NAME, "Meet")
            }

        FakeCarrier.ring(context, FakeCallSpec(number = CallTests.freshNumber(), extras = extras))

        showsText(context.getString(R.string.call_incoming_will_disconnect_app, "Meet"))
    }

    @Test
    fun ringingCallSaysAnsweringEndsTheOtherCall() {
        connectedCall()
        val extras = Bundle().apply { putBoolean(EXTRA_ANSWERING_DROPS_FG_CALL, true) }

        FakeCarrier.ring(context, FakeCallSpec(number = CallTests.freshNumber(), extras = extras))

        showsText(context.getString(R.string.call_incoming_will_disconnect))
    }

    @Test
    fun transferPutsTheActiveCallThroughToTheHeldOne() {
        val original = Shell.run("cmd phone cc get-value $KEY_ALLOW_TRANSFER")
        Shell.run("cmd phone cc set-value $KEY_ALLOW_TRANSFER true")
        try {
            val (held, active) = twoCalls()
            InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }

            control(context.getString(R.string.incall_label_transfer)).click()
            showsText(context.getString(R.string.incall_transfer_dialog_title))
            showsText(context.getString(R.string.incall_transfer_dialog_confirm)).click()

            CallTests.waitFor { active.transferredTo?.takeIf { it === held } }
            InCall.waitForNoCalls()
        } finally {
            if (original.contains("true"))
                Shell.run("cmd phone cc set-value $KEY_ALLOW_TRANSFER true")
            else Shell.run("cmd phone cc clear-values")
        }
    }

    @Test
    fun noTransferWhenTheCarrierDoesNotAllowIt() {
        Shell.run("cmd phone cc set-value $KEY_ALLOW_TRANSFER false")
        try {
            twoCalls()
            InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }
            val transfer = context.getString(R.string.incall_label_transfer)
            waitForCallScreen()
            check(!isShown(transfer)) {
                "transfer on the panel although the carrier doesn't allow it"
            }
            openMore()
            // Which controls sit under More depends on the screen; wait for any of them.
            val sheet =
                listOf(
                        R.string.incall_label_hold,
                        R.string.incall_label_merge,
                        R.string.incall_label_swap,
                        R.string.incall_label_videocall,
                    )
                    .map(context::getString)
            CallTests.waitFor { sheet.firstOrNull(::isShown) }
            check(!isShown(transfer)) {
                "transfer under More although the carrier doesn't allow it"
            }
        } finally {
            Shell.run("cmd phone cc clear-values")
        }
    }

    private fun connectedCall(): FakeConnection {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        far.answerFromFarEnd()
        InCall.waitForState(DialerCallState.ACTIVE)
        return far
    }

    /** Opens the More sheet, where the call's actions beyond audio, mute and the keypad are. */
    private fun openMore() {
        checkNotNull(
                deviceRule.device.wait(
                    Until.findObject(By.res(TestTags.INCALL_MORE)),
                    CallTests.TIMEOUT_MILLIS,
                )
            ) {
                "no More button"
            }
            .click()
    }

    private fun waitForCallScreen() {
        checkNotNull(
            deviceRule.device.wait(
                Until.findObject(By.res(TestTags.INCALL_END_CALL)),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "no call screen"
        }
    }

    private fun isShown(label: String) =
        deviceRule.device.findObject(By.text(label)) != null ||
            deviceRule.device.findObject(By.desc(label)) != null

    /** The call screen's control named [label]: on the panel, or else under More. */
    private fun control(label: String): UiObject2 {
        waitForCallScreen()
        deviceRule.device.findObject(By.text(label))?.let {
            return it
        }
        deviceRule.device.findObject(By.desc(label))?.let {
            return it
        }
        openMore()
        return showsText(label)
    }

    private fun showsText(text: String): UiObject2 =
        checkNotNull(CallTests.findLabel(deviceRule.device, text)) { "\"$text\" not shown" }

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

    private companion object {
        const val EXTRA_ANSWERING_DROPS_FG_CALL = "android.telecom.extra.ANSWERING_DROPS_FG_CALL"
        const val EXTRA_ANSWERING_DROPS_FG_CALL_APP_NAME =
            "android.telecom.extra.ANSWERING_DROPS_FG_CALL_APP_NAME"
        const val KEY_ALLOW_TRANSFER = "carrier_allow_transfer_ims_call_bool"
    }
}
