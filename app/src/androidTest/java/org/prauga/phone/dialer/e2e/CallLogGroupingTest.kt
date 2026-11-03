/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.Intent
import android.database.MatrixCursor
import android.provider.CallLog
import android.provider.CallLog.Calls
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.app.calllog.CallLogActivity
import org.prauga.phone.dialer.app.calllog.CallLogGroupBuilder
import org.prauga.phone.dialer.phonenumbercache.CallLogQuery
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.incallui.call.CallList

/** How the call log puts calls into rows, and which calls the Missed view lists. */
@RunWith(AndroidJUnit4::class)
class CallLogGroupingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun rowsFollowTheGroupingRule() {
        val now = System.currentTimeMillis()
        val rows =
            rowsOf(
                // Today: two answered calls share a row, the missed one before them doesn't.
                Row(NUMBER, Calls.OUTGOING_TYPE, now - MINUTE),
                Row("(650) 555-0100", Calls.INCOMING_TYPE, now - 2 * MINUTE, account = "other SIM"),
                Row(NUMBER, Calls.MISSED_TYPE, now - 3 * MINUTE),
                Row(NUMBER, Calls.REJECTED_TYPE, now - 4 * MINUTE),
                // Yesterday starts a new row.
                Row(NUMBER, Calls.MISSED_TYPE, now - DAY),
                // Private callers each get a row.
                Row(
                    "",
                    Calls.INCOMING_TYPE,
                    now - 3 * DAY,
                    presentation = Calls.PRESENTATION_RESTRICTED,
                ),
                Row(
                    "",
                    Calls.INCOMING_TYPE,
                    now - 4 * DAY,
                    presentation = Calls.PRESENTATION_RESTRICTED,
                ),
            )

        assertEquals(listOf(2, 2, 1, 1, 1), rows)
    }

    @Test
    fun declinedCallIsListedUnderMissed() {
        val number = CallTests.freshNumber()
        FakeCarrier.ring(context, FakeCallSpec(number))
        CallTests.waitForRinging()
        InCall.onMain { checkNotNull(CallList.getInstance().incomingCall).reject(false, null) }
        CallTests.waitFor {
            CallTests.latestCall(context, number, 0)?.takeIf {
                it.type == CallLog.Calls.REJECTED_TYPE
            }
        }

        context.startActivity(
            Intent(context, CallLogActivity::class.java)
                .putExtra(CallLog.Calls.EXTRA_CALL_TYPE_FILTER, CallLog.Calls.MISSED_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        assertNotNull(
            "declined call not listed under Missed",
            deviceRule.device.wait(
                Until.findObject(By.textContains(number.takeLast(4))),
                CallTests.TIMEOUT_MILLIS,
            ),
        )
    }

    private data class Row(
        val number: String,
        val type: Int,
        val date: Long,
        val presentation: Int = Calls.PRESENTATION_ALLOWED,
        val account: String = "sim",
    )

    /** Runs the grouping over [rows], newest first, and returns the size of each call log row. */
    private fun rowsOf(vararg rows: Row): List<Int> {
        val cursor = MatrixCursor(CallLogQuery.getProjection())
        rows.forEachIndexed { id, row ->
            val values = arrayOfNulls<Any>(CallLogQuery.getProjection().size)
            values[CallLogQuery.ID] = id.toLong()
            values[CallLogQuery.NUMBER] = row.number
            values[CallLogQuery.DATE] = row.date
            values[CallLogQuery.CALL_TYPE] = row.type
            values[CallLogQuery.NUMBER_PRESENTATION] = row.presentation
            values[CallLogQuery.ACCOUNT_COMPONENT_NAME] = row.account
            values[CallLogQuery.ACCOUNT_ID] = row.account
            values[CallLogQuery.FEATURES] = 0
            values[CallLogQuery.POST_DIAL_DIGITS] = ""
            values[CallLogQuery.VIA_NUMBER] = ""
            cursor.addRow(values)
        }
        val sizes = mutableListOf<Int>()
        CallLogGroupBuilder(
                object : CallLogGroupBuilder.GroupCreator {
                    override fun addGroup(cursorPosition: Int, size: Int) {
                        sizes += size
                    }

                    override fun setCallbackAction(rowId: Long, callbackAction: Int) = Unit

                    override fun setDayGroup(rowId: Long, dayGroup: Int) = Unit

                    override fun clearDayGroups() = Unit
                }
            )
            .addGroups(cursor)
        return sizes
    }

    private companion object {
        const val NUMBER = "+16505550100"
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }
}
