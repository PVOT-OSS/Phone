/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.ContentValues
import android.content.Intent
import android.provider.CallLog.Calls
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.MainComponent

/** What the user can do with call log rows and a number's call details. */
@RunWith(AndroidJUnit4::class)
class CallLogActionsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val device get() = deviceRule.device
    private val numbers = mutableListOf<String>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @After
    fun removeCalls() {
        for (number in numbers) context.contentResolver.delete(Calls.CONTENT_URI, "${Calls.NUMBER} = ?", arrayOf(number))
    }

    @Test
    fun videoCallRowSaysVideo() {
        val number = addCalls(1, features = Calls.FEATURES_VIDEO)
        openRecents()

        val row = findText(number.takeLast(4))
        checkNotNull(row.parent.parent.findObject(By.textStartsWith(context.getString(R.string.call_log_label_video)))) {
            "row doesn't say Video"
        }
    }

    @Test
    fun deletingARowAsksFirst() {
        val number = addCalls(2)
        openRecents()

        longPressMenuDelete(number)
        findDialogTitle(context.getString(R.string.call_log_delete_entry_title))
        clickDialogButton(NEGATIVE)
        check(callCount(number) == 2) { "calls deleted after cancelling" }

        longPressMenuDelete(number)
        clickDialogButton(POSITIVE)
        CallTests.waitFor { if (callCount(number) == 0) true else null }
    }

    @Test
    fun swipingACallInDetailsDeletesJustThatCall() {
        val number = addCalls(2)
        openDetails(number)

        swipeFirstCall()
        findDialogTitle(context.getString(R.string.call_details_delete_call_title))
        clickDialogButton(POSITIVE)
        CallTests.waitFor { if (callCount(number) == 1) true else null }

        swipeFirstCall()
        findDialogTitle(context.getString(R.string.call_details_delete_all_title))
        clickDialogButton(POSITIVE)
        CallTests.waitFor { if (callCount(number) == 0) true else null }
    }

    /** Adds [count] answered calls from a fresh number a few seconds ago, at the top of the log. */
    @Test
    fun callDetailsShowWhatWasSentWithTheCall() {
        val number = addCalls(1) {
            put(Calls.SUBJECT, NOTE)
            put(Calls.PRIORITY, Calls.PRIORITY_URGENT)
        }
        openDetails(number)

        findText(context.getString(R.string.call_details_composer_urgent_note, NOTE))
    }

    private fun addCalls(count: Int, features: Int = 0, more: ContentValues.() -> Unit = {}): String {
        val number = CallTests.freshNumber()
        numbers += number
        repeat(count) { i ->
            val values = ContentValues().apply {
                put(Calls.NUMBER, number)
                put(Calls.TYPE, Calls.INCOMING_TYPE)
                put(Calls.DATE, System.currentTimeMillis() - (i + 1) * SECOND)
                put(Calls.DURATION, 30)
                put(Calls.FEATURES, features)
                more()
            }
            checkNotNull(context.contentResolver.insert(Calls.CONTENT_URI, values))
        }
        return number
    }

    private fun callCount(number: String): Int =
        context.contentResolver.query(Calls.CONTENT_URI, arrayOf(Calls._ID), "${Calls.NUMBER} = ?", arrayOf(number), null)
            ?.use { it.count } ?: 0

    private fun openRecents() {
        context.startActivity(MainComponent.getShowCallLogIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun longPressMenuDelete(number: String) {
        findText(number.takeLast(4)).longClick()
        findText(context.getString(R.string.delete)).click()
    }

    private fun openDetails(number: String) {
        openRecents()
        findText(number.takeLast(4)).click()
        // The expanded row's actions can run below the bottom navigation; scroll to Call details.
        val details = By.res(context.packageName, "details_action")
        val list = checkNotNull(device.findObject(By.res(context.packageName, "recycler_view")))
        list.scrollUntil(Direction.DOWN, Until.findObject(details))
        checkNotNull(device.wait(Until.findObject(details), CallTests.TIMEOUT_MILLIS)) { "no call details action" }.click()
        device.wait(Until.findObject(By.res(context.packageName, "recycler_view")), CallTests.TIMEOUT_MILLIS)
    }

    /** Swipes the first call entry in call details (the row under the header). */
    private fun swipeFirstCall() {
        val callTime = checkNotNull(device.wait(Until.findObject(By.res(context.packageName, "call_time")), CallTests.TIMEOUT_MILLIS)) {
            "no call entries"
        }
        // Swipe the whole entry row the call time sits in.
        var entry = callTime
        while (entry.parent != null && entry.parent.resourceName != "${context.packageName}:id/recycler_view") entry = entry.parent
        entry.swipe(Direction.RIGHT, SWIPE_PERCENT)
    }

    private fun findText(text: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.textContains(text)), CallTests.TIMEOUT_MILLIS)) { "\"$text\" not shown" }

    private fun findDialogTitle(title: String) = check(
        device.wait(Until.findObject(By.res("android", "alertTitle").text(title)), CallTests.TIMEOUT_MILLIS) != null ||
            device.wait(Until.findObject(By.text(title)), CallTests.TIMEOUT_MILLIS) != null,
    ) { "dialog \"$title\" not shown" }

    private fun clickDialogButton(id: String) =
        checkNotNull(device.wait(Until.findObject(By.res("android", id)), CallTests.TIMEOUT_MILLIS)) { "no $id" }.click()

    private companion object {
        const val SECOND = 1_000L
        const val POSITIVE = "button1"
        const val NEGATIVE = "button2"
        const val SWIPE_PERCENT = 0.9f
        const val NOTE = "Running late"
    }
}
