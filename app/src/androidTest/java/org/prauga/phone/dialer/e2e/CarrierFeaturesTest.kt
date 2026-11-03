/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.telecom.Call
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.notification.NotificationChannelId
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConference
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.EmergencySatellite
import org.prauga.phone.incallui.InCallPresenter
import org.prauga.phone.incallui.NumberPromptDialogFragment
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.TelecomAdapter
import org.prauga.phone.incallui.call.state.DialerCallState

/** Calling features the network offers through Telecom: decline reasons, forwarding, transfer, … */
@RunWith(AndroidJUnit4::class)
class CarrierFeaturesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun decliningTellsTheNetworkTheCallWasDeclined() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        val far = CallTests.waitForRinging()

        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).reject(false, null) }

        assertEquals(Call.REJECT_REASON_DECLINED, CallTests.waitFor { far.rejectReason })
    }

    @Test
    fun blockingTellsTheNetworkTheCallWasUnwanted() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        val far = CallTests.waitForRinging()

        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).blockCall() }

        assertEquals(Call.REJECT_REASON_UNWANTED, CallTests.waitFor { far.rejectReason })
    }

    @Test
    fun ringingCallIsForwardedToTheNumberEntered() {
        FakeCarrier.ring(
            context,
            FakeCallSpec(
                CallTests.freshNumber(),
                capabilities = Connection.CAPABILITY_SUPPORT_DEFLECT,
            ),
        )
        val far = CallTests.waitForRinging()
        val call = InCall.waitForState(DialerCallState.INCOMING)
        check(InCall.onMain { call.can(Call.Details.CAPABILITY_SUPPORT_DEFLECT) }) {
            "call can't be forwarded"
        }
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }
        val activity =
            CallTests.waitFor { InCall.onMain { InCallPresenter.getInstance().activity } }

        InCall.onMain {
            NumberPromptDialogFragment.show(
                activity,
                NumberPromptDialogFragment.Action.FORWARD,
                call.id,
            )
        }
        enterNumberAndConfirm(context.getString(R.string.number_prompt_forward_confirm), TARGET)

        assertEquals(
            TARGET,
            CallTests.waitFor { far.deflectedTo }
                .schemeSpecificPart
                .filter { it.isDigit() || it == '+' },
        )
    }

    @Test
    fun transferButtonSendsTheCallToTheNumberEntered() = withTransferAllowed {
        val far =
            connectedCall(
                FakeCallSpec(CallTests.freshNumber(), capabilities = Connection.CAPABILITY_TRANSFER)
            )
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }

        showsText(context.getString(R.string.incall_label_transfer)).click()
        showsText(context.getString(R.string.number_prompt_transfer_title))
        enterNumberAndConfirm(context.getString(R.string.number_prompt_transfer_confirm), TARGET)

        assertEquals(
            TARGET,
            CallTests.waitFor { far.transferredToNumber }
                .schemeSpecificPart
                .filter { it.isDigit() || it == '+' },
        )
        InCall.waitForNoCalls()
    }

    @Test
    fun addCallAddsTheNumberToANetworkConference() {
        val first = connectedCall(FakeCallSpec(CallTests.freshNumber()))
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        InCall.waitForState(DialerCallState.INCOMING)
        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).answer() }
        val active =
            CallTests.waitFor {
                InCall.onMain {
                    CallList.getInstance().activeCall?.takeIf {
                        CallList.getInstance().backgroundCall != null
                    }
                }
            }
        InCall.onMain { TelecomAdapter.getInstance().merge(active.id) }
        val conference = CallTests.waitFor { first.conference as? FakeConference }
        conference.acceptParticipants()
        CallTests.waitFor {
            InCall.onMain {
                CallList.getInstance().activeCall?.takeIf {
                    it.isConferenceCall && it.can(Call.Details.CAPABILITY_ADD_PARTICIPANT)
                }
            }
        }
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }

        showsText(context.getString(R.string.incall_label_add_call)).click()
        showsText(context.getString(R.string.number_prompt_add_title))
        enterNumberAndConfirm(context.getString(R.string.number_prompt_add_confirm), TARGET)

        val added = CallTests.waitFor { conference.addedParticipants.firstOrNull() }
        assertEquals(TARGET, added.schemeSpecificPart.filter { it.isDigit() || it == '+' })
    }

    @Test
    fun carrierMessageShowsUntilTheCarrierClearsIt() {
        val far = connectedCall(FakeCallSpec(CallTests.freshNumber()))
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }

        far.showDiagnosticMessage(7, MESSAGE)
        showsText(MESSAGE)

        far.clearDiagnosticMessage(7)
        check(deviceRule.device.wait(Until.gone(By.text(MESSAGE)), CallTests.TIMEOUT_MILLIS)) {
            "message still shown"
        }
    }

    @Test
    fun noNetworkOffersSatelliteMessaging() {
        val opened = CountDownLatch(1)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = opened.countDown()
            }
        context.registerReceiver(
            receiver,
            IntentFilter(ACTION_SATELLITE_APP),
            Context.RECEIVER_NOT_EXPORTED,
        )
        try {
            connectedCall(FakeCallSpec(CallTests.freshNumber()))
            val call = InCall.waitForState(DialerCallState.ACTIVE)
            val app =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_SATELLITE_APP).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                )

            // Telecom passes this event on only for emergency calls, and faking one isn't safe on a
            // real phone: hand it to the app the way the call does.
            val extras =
                Bundle().apply {
                    putParcelable(
                        TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT,
                        app,
                    )
                    putInt(TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE, 1)
                }
            InCall.onMain { EmergencySatellite.onEvent(call.id, extras) }

            showsText(context.getString(R.string.emergency_satellite_title))
            button(context.getString(R.string.emergency_satellite_open_sos)).click()
            check(opened.await(CallTests.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                "satellite app not opened"
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun callMovedToAnotherDeviceSaysSo() {
        val far = connectedCall(FakeCallSpec(CallTests.freshNumber()))
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }
        showsText(context.getString(R.string.incall_label_mute))

        far.hangUpFromFarEnd(DisconnectCause.CALL_PULLED)

        showsText(context.getString(R.string.incall_call_pulled))
    }

    @Test
    fun callUsingBackupCallingIsLabelled() {
        connectedCall(
            FakeCallSpec(CallTests.freshNumber(), properties = Connection.PROPERTY_CROSS_SIM)
        )
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }

        // After the SIM's name when the call screen shows it, as it does for the fake carrier.
        val label = context.getString(R.string.incall_backup_calling)
        checkNotNull(
            deviceRule.device.wait(
                Until.findObject(By.textContains(label)),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "\"$label\" not shown"
        }
    }

    @Test
    fun callRingingSilentlyDoesNotTakeOverTheScreen() {
        val extras = Bundle().apply { putBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED, true) }
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), extras = extras))
        CallTests.waitForRinging()
        CallTests.waitFor {
            InCall.onMain { CallList.getInstance().incomingCall?.takeIf { it.isSilentRinging } }
        }

        val notifications = context.getSystemService(NotificationManager::class.java)
        val notification =
            CallTests.waitFor {
                notifications.activeNotifications
                    .map { it.notification }
                    .firstOrNull { it.channelId == NotificationChannelId.ONGOING_CALL }
            }
        assertNull("silent call opens the call screen", notification.fullScreenIntent)
        check(
            notifications.activeNotifications.none {
                it.notification.channelId == NotificationChannelId.INCOMING_CALL
            }
        ) {
            "silent call posted as a ringing call"
        }
    }

    private fun connectedCall(spec: FakeCallSpec): FakeConnection {
        FakeCarrier.ring(context, spec)
        val far = CallTests.waitForRinging()
        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).answer() }
        CallTests.waitFor { far.takeIf { it.state == Connection.STATE_ACTIVE } }
        InCall.waitForState(DialerCallState.ACTIVE)
        return far
    }

    private fun enterNumberAndConfirm(confirm: String, number: String) {
        val field =
            checkNotNull(
                deviceRule.device.wait(
                    Until.findObject(By.res(context.packageName, "number_prompt_field")),
                    CallTests.TIMEOUT_MILLIS,
                )
            ) {
                "no number field"
            }
        field.text = number
        button(confirm).click()
    }

    /** A dialog button by its label, in any case, as buttons may be shown in capitals. */
    private fun button(label: String): UiObject2 =
        checkNotNull(
            deviceRule.device.wait(
                Until.findObject(
                    By.text(Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE))
                ),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "no \"$label\" button"
        }

    private fun showsText(text: String): UiObject2 =
        checkNotNull(
            deviceRule.device.wait(Until.findObject(By.text(text)), CallTests.TIMEOUT_MILLIS)
        ) {
            val shown =
                deviceRule.device
                    .findObjects(By.pkg(context.packageName))
                    .mapNotNull { it.text }
                    .distinct()
            "\"$text\" not shown; the app shows: $shown"
        }

    private fun withTransferAllowed(block: () -> Unit) {
        Shell.run("cmd phone cc set-value $KEY_ALLOW_TRANSFER true")
        try {
            block()
        } finally {
            Shell.run("cmd phone cc clear-values")
        }
    }

    private companion object {
        const val TARGET = "+15550123456"
        const val MESSAGE = "Weak signal: your call may drop"
        const val ACTION_SATELLITE_APP = "org.prauga.phone.testing.SATELLITE_APP"
        const val KEY_ALLOW_TRANSFER = "carrier_allow_transfer_ims_call_bool"
    }
}
