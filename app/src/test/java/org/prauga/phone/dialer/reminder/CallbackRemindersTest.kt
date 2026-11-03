/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Call-back reminders are kept, set as alarms, shown when due and moved when snoozed. */
@RunWith(RobolectricTestRunner::class)
class CallbackRemindersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun clear() {
        CallbackReminders.pending(context).forEach { CallbackReminders.cancel(context, it.id) }
    }

    @Test
    fun aReminderIsKeptAndSetAsAnAlarm() {
        val reminder = CallbackReminders.schedule(context, "+15550199", "Ada Lovelace", 1_000_000L)

        assertThat(CallbackReminders.pending(context)).containsExactly(reminder)
        assertThat(CallbackReminders.get(context, reminder.id)).isEqualTo(reminder)
        assertThat(alarms.scheduledAlarms.map { it.triggerAtMs }).containsExactly(1_000_000L)
    }

    @Test
    fun remindersGetTheirOwnIdsAndSortBySoonest() {
        val later = CallbackReminders.schedule(context, "+15550199", null, 2_000L)
        val sooner = CallbackReminders.schedule(context, "+15550123", null, 1_000L)

        assertThat(later.id).isNotEqualTo(sooner.id)
        assertThat(CallbackReminders.pending(context)).containsExactly(sooner, later).inOrder()
    }

    @Test
    fun cancellingForgetsTheReminderAndItsAlarm() {
        val reminder = CallbackReminders.schedule(context, "+15550199", null, 1_000L)
        CallbackReminders.cancel(context, reminder.id)

        assertThat(CallbackReminders.pending(context)).isEmpty()
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun aDueReminderShowsANotificationWithItsActions() {
        val reminder = CallbackReminders.schedule(context, "+15550199", "Ada Lovelace", 1_000L)
        CallbackReminderReceiver()
            .onReceive(
                context,
                Intent(CallbackReminderReceiver.ACTION_DUE)
                    .putExtra(CallbackReminderReceiver.EXTRA_ID, reminder.id),
            )

        val shown = notifications.allNotifications.single()
        assertThat(shown.extras.getCharSequence("android.title").toString())
            .isEqualTo(context.getString(R.string.callback_reminder_title, "Ada Lovelace"))
        assertThat(shown.actions.map { it.title.toString() })
            .containsExactly(
                context.getString(R.string.notification_missedCall_call_back),
                context.getString(R.string.notification_missedCall_message),
                context.getString(R.string.callback_reminder_snooze),
            )
            .inOrder()
    }

    @Test
    fun snoozingMovesTheReminderAnHourOn() {
        val reminder = CallbackReminders.schedule(context, "+15550199", null, 1_000L)
        val before = System.currentTimeMillis()
        CallbackReminderReceiver()
            .onReceive(
                context,
                Intent(CallbackReminderReceiver.ACTION_SNOOZE)
                    .putExtra(CallbackReminderReceiver.EXTRA_ID, reminder.id),
            )

        val moved = CallbackReminders.get(context, reminder.id)!!
        assertThat(moved.atMillis).isAtLeast(before + 60 * 60 * 1000L)
        assertThat(alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(moved.atMillis)
    }

    @Test
    fun anAppUpdateSetsTheAlarmsAgain() {
        val reminder = CallbackReminders.schedule(context, "+15550199", null, 5_000L)
        alarms.scheduledAlarms.clear()
        CallbackReminderReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertThat(alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(reminder.atMillis)
    }

    @Test
    fun aRebootSetsTheAlarmsAgain() {
        val reminder = CallbackReminders.schedule(context, "+15550199", null, 5_000L)
        alarms.scheduledAlarms.clear()
        CallbackReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(reminder.atMillis)
    }

    @Test
    fun delaysLandWhereTheyPromise() {
        val zone = ZoneId.of("Asia/Kolkata")
        val noon = LocalDateTime.of(2026, 11, 3, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val night = LocalDateTime.of(2026, 11, 3, 21, 0).atZone(zone).toInstant().toEpochMilli()

        assertThat(ReminderDelay.IN_FIFTEEN_MINUTES.dueAt(noon, zone))
            .isEqualTo(noon + 15 * 60 * 1000L)
        assertThat(ReminderDelay.IN_AN_HOUR.dueAt(noon, zone)).isEqualTo(noon + 60 * 60 * 1000L)
        assertThat(local(ReminderDelay.THIS_EVENING.dueAt(noon, zone), zone))
            .isEqualTo(LocalDateTime.of(2026, 11, 3, 18, 0))
        // Past six already: the next evening.
        assertThat(local(ReminderDelay.THIS_EVENING.dueAt(night, zone), zone))
            .isEqualTo(LocalDateTime.of(2026, 11, 4, 18, 0))
        assertThat(local(ReminderDelay.TOMORROW_MORNING.dueAt(night, zone), zone))
            .isEqualTo(LocalDateTime.of(2026, 11, 4, 9, 0))
    }

    private fun local(millis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
}
