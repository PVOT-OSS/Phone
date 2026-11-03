/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.prauga.phone.dialer.R

/** Settings for incoming calls: the answer gesture, flip to silence and the caller announcement. */
class IncomingCallSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            ListPreference(context).apply {
                key = IncomingCallSettings.KEY_ANSWER_GESTURE
                setTitle(R.string.answer_method_settings_title)
                setDialogTitle(R.string.answer_method_settings_title)
                entries =
                    arrayOf(
                        getString(R.string.answer_method_swipe_entry),
                        getString(R.string.answer_method_tap_entry),
                    )
                entryValues =
                    arrayOf(IncomingCallSettings.GESTURE_SWIPE, IncomingCallSettings.GESTURE_TAP)
                setDefaultValue(IncomingCallSettings.GESTURE_SWIPE)
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isIconSpaceReserved = false
            }
        )

        // Flip to silence needs an accelerometer to tell the phone is face down.
        val sensors = context.getSystemService(SensorManager::class.java)
        if (sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            screen.addPreference(
                SwitchPreferenceCompat(context).apply {
                    key = IncomingCallSettings.KEY_FLIP_TO_SILENCE
                    setTitle(R.string.flip_to_silence_title)
                    setSummary(R.string.flip_to_silence_summary)
                    setDefaultValue(false)
                    isIconSpaceReserved = false
                }
            )
        }

        screen.addPreference(
            ListPreference(context).apply {
                key = IncomingCallSettings.KEY_ANNOUNCE_CALLER
                setTitle(R.string.call_announcer_title)
                setDialogTitle(R.string.call_announcer_title)
                entries =
                    arrayOf(
                        getString(R.string.call_announcer_always),
                        getString(R.string.call_announcer_headset),
                        getString(R.string.call_announcer_never),
                    )
                entryValues =
                    arrayOf(
                        IncomingCallSettings.ANNOUNCE_ALWAYS,
                        IncomingCallSettings.ANNOUNCE_HEADSET,
                        IncomingCallSettings.ANNOUNCE_NEVER,
                    )
                setDefaultValue(IncomingCallSettings.ANNOUNCE_NEVER)
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isIconSpaceReserved = false
            }
        )
        screen.addPreference(
            androidx.preference.Preference(context).apply {
                setSummary(R.string.call_announcer_footer)
                isSelectable = false
                isIconSpaceReserved = false
            }
        )

        preferenceScreen = screen
    }
}
