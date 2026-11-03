/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.provider.CallLog
import android.telecom.Connection
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.screening.CallerCategory
import org.prauga.phone.dialer.screening.CategoryAction
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier

@RunWith(AndroidJUnit4::class)
class ScreeningTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val screening = ScreeningSettingsRule()
    private val contacts = TestContactRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule())
            .around(FakeCarrierRule())
            .around(DeviceRule())
            .around(screening)
            .around(contacts)

    @Test
    fun hiddenCallerIsDeclined() {
        screening.set(CallerCategory.PRIVATE_OR_HIDDEN, CategoryAction.DECLINE)
        val since = System.currentTimeMillis()

        FakeCarrier.ring(
            context,
            FakeCallSpec(
                number = CallTests.freshNumber(),
                presentation = TelecomManager.PRESENTATION_RESTRICTED,
            ),
        )

        assertDeclined(CallTests.waitFor { CallTests.latestCall(context, since) })
    }

    @Test
    fun possiblyFakedCallerRingsSilently() {
        screening.set(CallerCategory.POSSIBLY_FAKED, CategoryAction.SILENCE)
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()

        FakeCarrier.ring(
            context,
            FakeCallSpec(
                number = number,
                verificationStatus = Connection.VERIFICATION_STATUS_FAILED,
            ),
        )
        CallTests.waitForRinging().hangUpFromFarEnd()

        val row = CallTests.waitFor { CallTests.latestCall(context, number, since) }
        assertEquals(CallLog.Calls.MISSED_TYPE, row.type)
        assertTrue(
            "call was not silenced by screening",
            row.missedReason and CallLog.Calls.USER_MISSED_CALL_SCREENING_SERVICE_SILENCED != 0L,
        )
    }

    @Test
    fun firstTimeCallerIsDeclined() {
        screening.set(CallerCategory.FIRST_TIME, CategoryAction.DECLINE)
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()

        FakeCarrier.ring(context, FakeCallSpec(number = number))

        assertDeclined(CallTests.waitFor { CallTests.latestCall(context, number, since) })
    }

    @Test
    fun knownCallerRingsWhenFirstTimeCallersAreDeclined() {
        screening.set(CallerCategory.FIRST_TIME, CategoryAction.DECLINE)
        val number = CallTests.freshNumber()
        CallTests.addPastIncomingCall(context, number)
        val since = System.currentTimeMillis()

        FakeCarrier.ring(context, FakeCallSpec(number = number))
        CallTests.waitForRinging().hangUpFromFarEnd()

        val row = CallTests.waitFor { CallTests.latestCall(context, number, since) }
        assertEquals(CallLog.Calls.MISSED_TYPE, row.type)
        assertEquals(
            0L,
            row.missedReason and CallLog.Calls.USER_MISSED_CALL_SCREENING_SERVICE_SILENCED,
        )
    }

    @Test
    fun contactRingsWhenFirstTimeCallersAreDeclined() {
        screening.set(CallerCategory.FIRST_TIME, CategoryAction.DECLINE)
        val number = CallTests.freshNumber()
        contacts.add("Screening Test", number)

        assertRings(FakeCallSpec(number = number))
    }

    @Test
    fun recentlyCalledNumberRingsWhenFakedNumbersAreDeclined() {
        screening.set(CallerCategory.POSSIBLY_FAKED, CategoryAction.DECLINE)
        val number = CallTests.freshNumber()
        CallTests.addRecentOutgoingCall(context, number)

        assertRings(
            FakeCallSpec(
                number = number,
                verificationStatus = Connection.VERIFICATION_STATUS_FAILED,
            )
        )
    }

    @Test
    fun waitingCallRingsWhenHiddenCallersAreDeclined() {
        screening.set(CallerCategory.PRIVATE_OR_HIDDEN, CategoryAction.DECLINE)
        FakeCarrier.ring(context, FakeCallSpec(number = CallTests.freshNumber()))
        CallTests.answerRingingCall()
        val since = System.currentTimeMillis()

        FakeCarrier.ring(
            context,
            FakeCallSpec(
                number = CallTests.freshNumber(),
                presentation = TelecomManager.PRESENTATION_RESTRICTED,
            ),
        )
        CallTests.waitFor { if (FakeCarrier.connections.size == 2) true else null }
        CallTests.waitFor {
                FakeCarrier.connections.lastOrNull()?.takeIf {
                    it.state == Connection.STATE_RINGING
                }
            }
            .hangUpFromFarEnd()

        assertEquals(
            CallLog.Calls.MISSED_TYPE,
            CallTests.waitFor { CallTests.latestCall(context, since) }.type,
        )
    }

    private fun assertRings(spec: FakeCallSpec) {
        val since = System.currentTimeMillis()
        FakeCarrier.ring(context, spec)
        CallTests.waitForRinging().hangUpFromFarEnd()
        val row =
            CallTests.waitFor { CallTests.latestCall(context, checkNotNull(spec.number), since) }
        assertEquals(CallLog.Calls.MISSED_TYPE, row.type)
        assertEquals(
            0L,
            row.missedReason and CallLog.Calls.USER_MISSED_CALL_SCREENING_SERVICE_SILENCED,
        )
    }

    private fun assertDeclined(row: CallLogRow) {
        assertEquals(CallLog.Calls.BLOCKED_TYPE, row.type)
        assertEquals(CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, row.blockReason)
        assertNotEquals(
            "declined call is still on the carrier",
            true,
            FakeCarrier.connections.isNotEmpty(),
        )
    }
}
