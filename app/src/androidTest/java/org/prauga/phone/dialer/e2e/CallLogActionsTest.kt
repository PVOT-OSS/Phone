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
import java.util.regex.Pattern
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
    private val device
        get() = deviceRule.device

    private val numbers = mutableListOf<String>()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @After
    fun removeCalls() {
        for (number in numbers) context.contentResolver.delete(
            Calls.CONTENT_URI,
            "${Calls.NUMBER} = ?",
            arrayOf(number),
        )
    }

    @Test
    fun videoCallRowSaysVideo() {
        val number = addCalls(1, features = Calls.FEATURES_VIDEO)
        openRecents()

        val row = findText(number.takeLast(4))
        checkNotNull(
            row.parent.parent.findObject(
                By.textStartsWith(context.getString(R.string.call_log_label_video))
            )
        ) {
            "row doesn't say Video"
        }
    }

    @Test
    fun swipingARowAwayCanBeUndone() {
        val number = addCalls(2)
        openRecents()

        row(number).swipe(Direction.LEFT, SWIPE_PERCENT)
        findText(context.getString(R.string.snackbar_undo)).click()
        row(number)
        check(callCount(number) == 2) { "calls deleted after Undo" }

        row(number).swipe(Direction.LEFT, SWIPE_PERCENT)
        // The delete commits once its snackbar is gone.
        CallTests.waitFor(DELETE_COMMIT_MILLIS) { if (callCount(number) == 0) true else null }
    }

    @Test
    fun swipingACallInDetailsDeletesJustThatCall() {
        val number = addCalls(2)
        openDetails(number)

        swipeFirstCall()
        val callTitle = context.getString(R.string.call_details_delete_call_title)
        findDialogTitle(callTitle)
        clickDialogButton(callTitle, POSITIVE)
        CallTests.waitFor { if (callCount(number) == 1) true else null }
        // Swipe the remaining call only once the deleted one has left the screen.
        CallTests.waitFor {
            if (device.findObjects(By.res(Pattern.compile(ENTRY_TAG))).size == 1) true else null
        }
        device.waitForIdle()

        swipeFirstCall()
        val allTitle = context.getString(R.string.call_details_delete_all_title)
        findDialogTitle(allTitle)
        clickDialogButton(allTitle, POSITIVE)
        CallTests.waitFor { if (callCount(number) == 0) true else null }
    }

    /** Adds [count] answered calls from a fresh number a few seconds ago, at the top of the log. */
    @Test
    fun callDetailsShowWhatWasSentWithTheCall() {
        val number =
            addCalls(1) {
                put(Calls.SUBJECT, NOTE)
                put(Calls.PRIORITY, Calls.PRIORITY_URGENT)
            }
        openDetails(number)

        findText(context.getString(R.string.call_details_composer_urgent_note, NOTE))
    }

    private fun addCalls(
        count: Int,
        features: Int = 0,
        more: ContentValues.() -> Unit = {},
    ): String {
        val number = CallTests.freshNumber()
        numbers += number
        repeat(count) { i ->
            val values =
                ContentValues().apply {
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
        context.contentResolver
            .query(
                Calls.CONTENT_URI,
                arrayOf(Calls._ID),
                "${Calls.NUMBER} = ?",
                arrayOf(number),
                null,
            )
            ?.use { it.count } ?: 0

    private fun openRecents() {
        context.startActivity(
            MainComponent.getShowCallLogIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Opens the row's actions and taps its Delete button, which asks first. */
    /** The Recent row for [number], once it is on screen. */
    private fun row(number: String): UiObject2 =
        checkNotNull(
            device.wait(
                Until.findObject(
                    By.res(Pattern.compile(ROW_TAG))
                        .hasDescendant(By.textContains(number.takeLast(4)))
                ),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "no row for $number"
        }

    private fun openDetails(number: String) {
        openRecents()
        // A tap on the row calls back; its info button opens the details.
        checkNotNull(
                row(number).findObject(By.desc(context.getString(R.string.call_log_action_details)))
            ) {
                "no details button"
            }
            .click()
        checkNotNull(
            device.wait(
                Until.findObject(By.res(Pattern.compile(ENTRY_TAG))),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "call details not shown"
        }
    }

    /** Swipes the first call in call details toward the start, where Delete is. */
    private fun swipeFirstCall() {
        val entry =
            checkNotNull(
                device.wait(
                    Until.findObject(By.res(Pattern.compile(ENTRY_TAG))),
                    CallTests.TIMEOUT_MILLIS,
                )
            ) {
                "no call entries"
            }
        entry.swipe(Direction.LEFT, SWIPE_PERCENT)
    }

    private fun findText(text: String): UiObject2 =
        checkNotNull(
            device.wait(Until.findObject(By.textContains(text)), CallTests.TIMEOUT_MILLIS)
        ) {
            "\"$text\" not shown"
        }

    private fun findDialogTitle(title: String) =
        check(
            device.wait(
                Until.findObject(By.res("android", "alertTitle").text(title)),
                CallTests.TIMEOUT_MILLIS,
            ) != null ||
                device.wait(Until.findObject(By.text(title)), CallTests.TIMEOUT_MILLIS) != null
        ) {
            "dialog \"$title\" not shown"
        }

    /**
     * Taps [label] in the dialog titled [title]. The dialog is a window of its own, so the button
     * is looked for under the title's root, not among the screen's own buttons of the same name.
     */
    private fun clickDialogButton(title: String, label: String) {
        var root =
            checkNotNull(device.wait(Until.findObject(By.text(title)), CallTests.TIMEOUT_MILLIS)) {
                "no dialog \"$title\""
            }
        while (root.parent != null) root = root.parent
        checkNotNull(root.wait(Until.findObject(By.text(label)), CallTests.TIMEOUT_MILLIS)) {
                "no \"$label\" in the dialog"
            }
            .click()
    }

    private companion object {
        const val SECOND = 1_000L
        val POSITIVE: String
            get() =
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .getString(R.string.delete)

        const val ROW_TAG = "call_history_row_\\d+"
        const val DELETE_COMMIT_MILLIS = 15_000L
        const val ENTRY_TAG = "call_details_entry_.*"
        const val SWIPE_PERCENT = 0.9f
        const val NOTE = "Running late"
    }
}
