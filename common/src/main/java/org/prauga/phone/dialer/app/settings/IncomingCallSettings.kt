/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.preference.PreferenceManager

/** How incoming calls behave: answer gesture, flip to silence and announcing the caller. */
object IncomingCallSettings {

    const val KEY_ANSWER_GESTURE = "answer_method_preference_list_key"
    const val KEY_FLIP_TO_SILENCE = "flip_to_silence_key"
    const val KEY_ANNOUNCE_CALLER = "call_announcer_enable_list_key"

    const val GESTURE_SWIPE = "swipe"
    const val GESTURE_TAP = "tap"

    const val ANNOUNCE_ALWAYS = "always"
    const val ANNOUNCE_HEADSET = "headset"
    const val ANNOUNCE_NEVER = "never"

    /** Whether incoming calls are answered with buttons instead of a swipe. */
    @JvmStatic
    fun answerWithSingleTap(context: Context): Boolean =
        prefs(context)?.getString(KEY_ANSWER_GESTURE, GESTURE_SWIPE) == GESTURE_TAP

    /** Whether turning the phone face down silences the ringer. */
    @JvmStatic
    fun flipToSilence(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_FLIP_TO_SILENCE, false) == true

    /** When the caller is announced: [ANNOUNCE_ALWAYS], [ANNOUNCE_HEADSET] or [ANNOUNCE_NEVER]. */
    @JvmStatic
    fun announceCaller(context: Context): String =
        prefs(context)?.getString(KEY_ANNOUNCE_CALLER, ANNOUNCE_NEVER) ?: ANNOUNCE_NEVER

    /** The settings, or null before the first unlock, when they can't be read. */
    private fun prefs(context: Context): SharedPreferences? {
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        return if (unlocked) PreferenceManager.getDefaultSharedPreferences(context) else null
    }
}
