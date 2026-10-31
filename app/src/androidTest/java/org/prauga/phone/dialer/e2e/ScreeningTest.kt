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

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule())
        .around(FakeCarrierRule())
        .around(DeviceRule())
        .around(screening)

    @Test
    fun hiddenCallerIsDeclined() {
        screening.set(CallerCategory.PRIVATE_OR_HIDDEN, CategoryAction.DECLINE)
        val since = System.currentTimeMillis()

        FakeCarrier.ring(context, FakeCallSpec(number = CallTests.freshNumber(), presentation = TelecomManager.PRESENTATION_RESTRICTED))

        assertDeclined(CallTests.waitFor { CallTests.latestCall(context, since) })
    }

    @Test
    fun possiblyFakedCallerRingsSilently() {
        screening.set(CallerCategory.POSSIBLY_FAKED, CategoryAction.SILENCE)
        val number = CallTests.freshNumber()
        val since = System.currentTimeMillis()

        FakeCarrier.ring(context, FakeCallSpec(number = number, verificationStatus = Connection.VERIFICATION_STATUS_FAILED))
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
        assertEquals(0L, row.missedReason and CallLog.Calls.USER_MISSED_CALL_SCREENING_SERVICE_SILENCED)
    }

    private fun assertDeclined(row: CallLogRow) {
        assertEquals(CallLog.Calls.BLOCKED_TYPE, row.type)
        assertEquals(CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, row.blockReason)
        assertNotEquals("declined call is still on the carrier", true, FakeCarrier.connections.isNotEmpty())
    }
}
