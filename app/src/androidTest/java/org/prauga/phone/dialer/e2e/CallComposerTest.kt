/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.contacts.common.dialog.CallSubjectDialog
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.InCallPresenter
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.state.DialerCallState

/** Who is calling as the network tells it, and call composer: urgent calls and notes. */
@RunWith(AndroidJUnit4::class)
class CallComposerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun callerWithoutANameGetsTheNetworksName() {
        ringWith(Bundle().apply { putString(Call.EXTRA_ASSERTED_DISPLAY_NAME, BUSINESS) })

        showsText(BUSINESS)
    }

    @Test
    fun verifiedBusinessCallSaysSo() {
        ringWith(
            Bundle().apply {
                putString(Call.EXTRA_ASSERTED_DISPLAY_NAME, BUSINESS)
                putBoolean(Call.EXTRA_IS_BUSINESS_CALL, true)
            }
        )

        showsText(context.getString(R.string.contact_grid_incoming_verified_business_call))
    }

    @Test
    fun urgentCallGetsTheImportantBadge() {
        // Telecom passes urgency on only for accounts that support call composer.
        FakeCarrier.register(context, callComposer = true)
        ringWith(
            Bundle().apply { putInt(TelecomManager.EXTRA_PRIORITY, TelecomManager.PRIORITY_URGENT) }
        )

        checkNotNull(
            deviceRule.device.wait(
                Until.findObject(By.res(TestTags.IMPORTANT_CALL_BADGE)),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "no important call badge"
        }
    }

    @Test
    fun callWithANoteCanBeMarkedUrgent() {
        // Telecom checks the account's capabilities when the call is placed: re-register it.
        FakeCarrier.register(context, callComposer = true)
        val number = CallTests.freshNumber()
        val intent =
            Intent(context, CallSubjectDialog::class.java)
                .putExtras(
                    Bundle().apply {
                        putString(CallSubjectDialog.ARG_NUMBER, number)
                        putString(CallSubjectDialog.ARG_NAME_OR_NUMBER, number)
                        putParcelable(
                            CallSubjectDialog.ARG_PHONE_ACCOUNT_HANDLE,
                            FakeCarrier.accountHandle(context),
                        )
                    }
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        showsText(context.getString(R.string.call_composer_urgent)).click()
        // The note's editable box, inside the field the dialog tags.
        checkNotNull(
                deviceRule.device.wait(
                    Until.findObject(By.clazz(EditText::class.java)),
                    CallTests.TIMEOUT_MILLIS,
                )
            )
            .text = NOTE
        showsText(context.getString(R.string.send_and_call_button)).click()

        InCall.waitForState(DialerCallState.DIALING)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        val extras = CallTests.waitFor { far.requestExtras }
        assertEquals(NOTE, extras.getString(TelecomManager.EXTRA_CALL_SUBJECT))
        assertEquals(TelecomManager.PRIORITY_URGENT, extras.getInt(TelecomManager.EXTRA_PRIORITY))
    }

    private fun ringWith(extras: Bundle) {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), extras = extras))
        CallTests.waitForRinging()
        InCall.waitForState(DialerCallState.INCOMING)
        InCall.onMain { InCallPresenter.getInstance().showInCall(false, false) }
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
            val extras =
                InCall.onMain {
                    CallList.getInstance().incomingCall?.let { call ->
                        // Package-private in DialerCall; only for this message.
                        val telecomCall =
                            call.javaClass
                                .getDeclaredMethod("getTelecomCall")
                                .apply { isAccessible = true }
                                .invoke(call)
                        (telecomCall as Call).details.extras?.keySet()
                    }
                }
            "\"$text\" not shown; the app shows: $shown; the call's extras: $extras"
        }

    private companion object {
        const val BUSINESS = "Acme Pizza"
        const val NOTE = "Running late"
    }
}
