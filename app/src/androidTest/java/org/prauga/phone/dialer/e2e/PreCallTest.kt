/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.ContentValues
import android.content.Intent
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.precall.PreCall
import org.prauga.phone.dialer.testing.FakeCarrier

/** What happens between pressing call and the call being placed. */
@RunWith(AndroidJUnit4::class)
class PreCallTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val blocked = mutableListOf<String>()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @After
    fun unblock() {
        for (number in blocked) {
            context.contentResolver.delete(
                BlockedNumbers.CONTENT_URI,
                "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                arrayOf(number),
            )
        }
    }

    @Test
    fun callingABlockedNumberAsksFirstAndCanGoAhead() {
        val number = blockedNumber()
        dial(number)

        val prefix =
            context
                .getString(R.string.calling_blocked_number_title, "\u0000")
                .substringBefore("\u0000")
        val title =
            checkNotNull(
                deviceRule.device.wait(
                    Until.findObject(By.textStartsWith(prefix)),
                    CallTests.TIMEOUT_MILLIS,
                )
            ) {
                "no warning shown"
            }
        check(title.text.filter(Char::isDigit).endsWith(number.takeLast(7))) {
            "warning names ${title.text}"
        }
        checkNotNull(
                deviceRule.device.findObject(
                    By.text(context.getString(R.string.calling_blocked_number_call))
                )
            ) {
                "no Call button"
            }
            .click()

        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        check(far.spec.number.orEmpty().endsWith(number.takeLast(7))) {
            "placed ${far.spec.number}"
        }
        far.hangUpFromFarEnd()
        InCall.waitForNoCalls()
    }

    @Test
    fun callingABlockedNumberCanBeCancelled() {
        dial(blockedNumber())

        checkNotNull(
                deviceRule.device.wait(
                    Until.findObject(By.text(context.getString(android.R.string.cancel))),
                    CallTests.TIMEOUT_MILLIS,
                )
            ) {
                "no warning shown"
            }
            .click()

        Thread.sleep(SETTLE_MILLIS)
        check(FakeCarrier.connections.isEmpty()) { "call placed after cancelling" }
    }

    @Test
    fun numberThatIsNotBlockedIsCalledStraightAway() {
        val number = CallTests.freshNumber()
        dial(number)

        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        check(far.spec.number.orEmpty().endsWith(number.takeLast(7))) {
            "placed ${far.spec.number}"
        }
        far.hangUpFromFarEnd()
        InCall.waitForNoCalls()
    }

    private fun blockedNumber(): String {
        // The block list is only there for users who can block numbers (not every user on a
        // phone with a headless system user).
        assumeTrue(
            "this user can't block numbers",
            BlockedNumberContract.canCurrentUserBlockNumbers(context),
        )
        val number = CallTests.freshNumber()
        val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        checkNotNull(context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values)) {
            "could not block $number"
        }
        blocked += number
        return number
    }

    /** Dials [number] the way the app's own screens do. */
    private fun dial(number: String) {
        val builder =
            CallIntentBuilder(number, CallInitiationType.Type.DIALPAD)
                .setPhoneAccountHandle(FakeCarrier.accountHandle(context))
        val intent =
            InCall.onMain { PreCall.getIntent(context, builder) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }
}
