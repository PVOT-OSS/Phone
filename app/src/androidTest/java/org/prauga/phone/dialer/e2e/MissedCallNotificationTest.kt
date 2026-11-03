/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.provider.CallLog
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.MainComponent
import org.prauga.phone.dialer.app.calllog.CallLogNotificationsService
import org.prauga.phone.dialer.notification.NotificationChannelId
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier

/** Missed call notifications: how each call is shown and when they are cleared. */
@RunWith(AndroidJUnit4::class)
class MissedCallNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Before
    fun startWithNoMissedCalls() {
        CallLogNotificationsService.cancelAllMissedCalls(context)
        CallTests.waitFor { if (missedCallNotifications().isEmpty()) true else null }
    }

    @Test
    fun missedCallIsShownAsAMessageFromTheCaller() {
        val number = missCall()

        val notification = notificationFor(number).notification
        assertEquals(Notification.CATEGORY_MISSED_CALL, notification.category)
        assertEquals(
            Notification.MessagingStyle::class.java.name,
            notification.extras.getString(Notification.EXTRA_TEMPLATE),
        )
        assertEquals(
            context.getString(R.string.notification_missedCallTitle),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        val actions = notification.actions.orEmpty().map { it.title.toString() }
        check(context.getString(R.string.notification_missedCall_call_back) in actions) {
            "no call back action in $actions"
        }
        check(context.getString(R.string.notification_missedCall_message) in actions) {
            "no message action in $actions"
        }
    }

    @Test
    fun swipingOneMissedCallAwayClearsThemAll() {
        val first = missCall()
        val second = missCall()

        checkNotNull(notificationFor(first).notification.deleteIntent) { "no delete intent" }.send()

        CallTests.waitFor { if (missedCallNotifications().isEmpty()) true else null }
        CallTests.waitFor { if (isRead(first) && isRead(second)) true else null }
    }

    @Test
    fun passingThroughRecentsKeepsTheMissedCalls() {
        val number = missCall()
        openRecents()

        switchAwayFromRecents()

        Thread.sleep(SETTLE_MILLIS)
        notificationFor(number)
        check(!isRead(number)) { "missed call marked read" }
    }

    @Test
    fun leavingRecentsAfterAWhileClearsTheMissedCalls() {
        val number = missCall()
        openRecents()
        Thread.sleep(SEEN_MILLIS)

        switchAwayFromRecents()

        CallTests.waitFor { if (missedCallNotifications().isEmpty()) true else null }
        CallTests.waitFor { if (isRead(number)) true else null }
    }

    /** Rings and misses a call from a fresh number, and returns the number once it is notified. */
    private fun missCall(): String {
        val number = CallTests.freshNumber()
        FakeCarrier.ring(context, FakeCallSpec(number))
        CallTests.waitForRinging().hangUpFromFarEnd()
        InCall.waitForNoCalls()
        notificationFor(number)
        return number
    }

    private fun missedCallNotifications(): List<StatusBarNotification> =
        notifications.activeNotifications.filter {
            it.notification.channelId == NotificationChannelId.MISSED_CALL &&
                it.tag?.startsWith(TAG_PREFIX) == true
        }

    /** The missed call notification for [number]. */
    private fun notificationFor(number: String): StatusBarNotification =
        CallTests.waitFor {
            missedCallNotifications().firstOrNull {
                it.notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE)
                    ?.filter(Char::isDigit)
                    ?.endsWith(number.takeLast(7)) == true
            }
        }

    private fun isRead(number: String): Boolean =
        context.contentResolver
            .query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.IS_READ, CallLog.Calls.NEW),
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number),
                "${CallLog.Calls.DATE} DESC",
            )
            ?.use { it.moveToFirst() && it.getInt(0) == 1 && it.getInt(1) == 0 } ?: false

    private fun openRecents() {
        context.startActivity(
            MainComponent.getShowCallLogIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        checkNotNull(
            deviceRule.device.wait(
                Until.findObject(By.res("call_history_list")),
                CallTests.TIMEOUT_MILLIS,
            )
        ) {
            "main screen not shown"
        }
    }

    private fun switchAwayFromRecents() {
        val label = context.getString(R.string.tab_title_dialpad)
        val tab =
            deviceRule.device.findObject(By.text(label))
                ?: deviceRule.device.findObject(By.desc(label))
        checkNotNull(tab) { "tab \"$label\" not shown" }.click()
    }

    private companion object {
        const val TAG_PREFIX = "MissedCall_"
        const val SEEN_MILLIS = 4_000L
        const val SETTLE_MILLIS = 2_000L
    }
}
