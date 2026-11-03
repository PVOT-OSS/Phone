/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.reminder

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.core.net.toUri
import java.time.Duration
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.notification.DialerNotificationManager
import org.prauga.phone.dialer.notification.NotificationChannelId

/**
 * Shows a call-back reminder when its alarm fires, with Call, Message and Snooze; sets the alarms
 * again after a reboot.
 */
class CallbackReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // A reboot or an app update drops every alarm; the stored reminders set them again.
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> CallbackReminders.rescheduleAll(context)
            ACTION_DUE -> show(context, intent.getIntExtra(EXTRA_ID, 0))
            ACTION_SNOOZE -> snooze(context, intent.getIntExtra(EXTRA_ID, 0))
            ACTION_DONE -> CallbackReminders.cancel(context, intent.getIntExtra(EXTRA_ID, 0))
        }
    }

    private fun show(context: Context, id: Int) {
        val reminder = CallbackReminders.get(context, id) ?: return
        LogUtil.i(TAG, "showing reminder %d", id)
        val notification =
            Notification.Builder(context, NotificationChannelId.DEFAULT)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(
                    context.getString(
                        R.string.callback_reminder_title,
                        reminder.name ?: reminder.number,
                    )
                )
                .setContentText(
                    if (reminder.name != null) reminder.number
                    else context.getString(R.string.callback_reminder_text)
                )
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(callIntent(context, reminder))
                .setDeleteIntent(receiverIntent(context, ACTION_DONE, id))
                .addAction(
                    action(
                        context,
                        R.drawable.ic_phone,
                        R.string.notification_missedCall_call_back,
                        callIntent(context, reminder),
                    )
                )
                .addAction(
                    action(
                        context,
                        R.drawable.ic_message,
                        R.string.notification_missedCall_message,
                        messageIntent(context, reminder),
                    )
                )
                .addAction(
                    action(
                        context,
                        R.drawable.ic_bell,
                        R.string.callback_reminder_snooze,
                        receiverIntent(context, ACTION_SNOOZE, id),
                    )
                )
                .build()
        DialerNotificationManager.notify(context, NOTIFICATION_TAG, id, notification)
    }

    private fun snooze(context: Context, id: Int) {
        DialerNotificationManager.cancel(context, NOTIFICATION_TAG, id)
        CallbackReminders.reschedule(context, id, System.currentTimeMillis() + SNOOZE.toMillis())
    }

    private fun callIntent(context: Context, reminder: CallbackReminder): PendingIntent {
        val call =
            CallIntentBuilder(reminder.number, CallInitiationType.Type.MISSED_CALL_NOTIFICATION)
                .build()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            reminder.id,
            call,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun messageIntent(context: Context, reminder: CallbackReminder): PendingIntent {
        val sms =
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", reminder.number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            reminder.id,
            sms,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun receiverIntent(context: Context, action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, CallbackReminderReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, id)
                .setData("reminder:$id/$action".toUri()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(
        context: Context,
        icon: Int,
        label: Int,
        intent: PendingIntent,
    ): Notification.Action =
        Notification.Action.Builder(
                Icon.createWithResource(context, icon),
                context.getString(label),
                intent,
            )
            .build()

    companion object {
        const val ACTION_DUE = "org.prauga.phone.dialer.reminder.DUE"
        const val ACTION_SNOOZE = "org.prauga.phone.dialer.reminder.SNOOZE"
        const val ACTION_DONE = "org.prauga.phone.dialer.reminder.DONE"
        const val EXTRA_ID = "id"
        const val NOTIFICATION_TAG = "callback_reminder"
        private const val TAG = "CallbackReminderReceiver"
        private val SNOOZE: Duration = Duration.ofHours(1)
    }
}
