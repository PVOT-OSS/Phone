/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject
import org.prauga.phone.dialer.common.LogUtil

/** A reminder to call [number] back, due at [atMillis]; [name] is who it is, when known. */
data class CallbackReminder(
    val id: Int,
    val number: String,
    val name: String?,
    val atMillis: Long,
) {

    internal fun toJson(): String =
        JSONObject()
            .put(KEY_NUMBER, number)
            .put(KEY_NAME, name ?: JSONObject.NULL)
            .put(KEY_AT, atMillis)
            .toString()

    internal companion object {
        private const val KEY_NUMBER = "number"
        private const val KEY_NAME = "name"
        private const val KEY_AT = "at"

        fun fromJson(id: Int, json: String): CallbackReminder? =
            runCatching {
                    val o = JSONObject(json)
                    CallbackReminder(
                        id,
                        o.getString(KEY_NUMBER),
                        o.optString(KEY_NAME).takeIf { !o.isNull(KEY_NAME) },
                        o.getLong(KEY_AT),
                    )
                }
                .getOrNull()
    }
}

/** How long after now a reminder is set for, as the user picks it. */
enum class ReminderDelay {
    IN_FIFTEEN_MINUTES,
    IN_AN_HOUR,
    THIS_EVENING,
    TOMORROW_MORNING;

    /**
     * When the reminder is due, from [nowMillis] in [zone]; an evening already past means
     * tomorrow's.
     */
    fun dueAt(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return when (this) {
            IN_FIFTEEN_MINUTES -> nowMillis + Duration.ofMinutes(15).toMillis()
            IN_AN_HOUR -> nowMillis + Duration.ofHours(1).toMillis()
            THIS_EVENING -> {
                val evening = now.with(EVENING)
                (if (evening.isAfter(now)) evening else evening.plusDays(1))
                    .toInstant()
                    .toEpochMilli()
            }
            TOMORROW_MORNING -> now.plusDays(1).with(MORNING).toInstant().toEpochMilli()
        }
    }

    private companion object {
        val EVENING: LocalTime = LocalTime.of(18, 0)
        val MORNING: LocalTime = LocalTime.of(9, 0)
    }
}

/**
 * When a reminder for [time] of day is due, from [nowMillis] in [zone]: today, or tomorrow once it
 * has passed.
 */
fun reminderAt(time: LocalTime, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val today = now.with(time).withSecond(0).withNano(0)
    return (if (today.isAfter(now)) today else today.plusDays(1)).toInstant().toEpochMilli()
}

/** Keeps call-back reminders and sets an alarm for each; [CallbackReminderReceiver] shows them. */
object CallbackReminders {

    private const val PREFS = "callback_reminders"
    private const val TAG = "CallbackReminders"

    /** Stores a reminder for [number] due at [atMillis] and schedules it; returns it. */
    fun schedule(
        context: Context,
        number: String,
        name: String?,
        atMillis: Long,
    ): CallbackReminder {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = (prefs.all.keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val reminder = CallbackReminder(id, number, name, atMillis)
        prefs.edit { putString(id.toString(), reminder.toJson()) }
        setAlarm(context, reminder)
        LogUtil.i(TAG, "reminder %d set for %d", id, atMillis)
        return reminder
    }

    /** Moves the reminder [id] to [atMillis]; nothing happens when it is gone already. */
    fun reschedule(context: Context, id: Int, atMillis: Long): CallbackReminder? {
        val old = get(context, id) ?: return null
        val moved = old.copy(atMillis = atMillis)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(id.toString(), moved.toJson())
        }
        setAlarm(context, moved)
        return moved
    }

    /** Forgets the reminder [id] and its alarm. */
    fun cancel(context: Context, id: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(id.toString()) }
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
    }

    /** The reminder [id], if it is still waiting. */
    fun get(context: Context, id: Int): CallbackReminder? {
        val json =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(id.toString(), null)
                ?: return null
        return CallbackReminder.fromJson(id, json)
    }

    /** Every reminder still waiting, soonest first. */
    fun pending(context: Context): List<CallbackReminder> =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .all
            .mapNotNull { (key, value) ->
                CallbackReminder.fromJson(
                    key.toIntOrNull() ?: return@mapNotNull null,
                    value as? String ?: return@mapNotNull null,
                )
            }
            .sortedBy { it.atMillis }

    /** Sets the alarms again, after a reboot cleared them. */
    fun rescheduleAll(context: Context) {
        pending(context).forEach { setAlarm(context, it) }
    }

    private fun setAlarm(context: Context, reminder: CallbackReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context, reminder.id)
        // An exact alarm when the user allows them, so "in 15 minutes" means that; a loose one
        // otherwise.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, intent)
        }
    }

    private fun alarmIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, CallbackReminderReceiver::class.java)
                .setAction(CallbackReminderReceiver.ACTION_DUE)
                .putExtra(CallbackReminderReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
