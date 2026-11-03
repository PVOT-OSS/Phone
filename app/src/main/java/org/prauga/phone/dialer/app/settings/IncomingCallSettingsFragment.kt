/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.Choice
import org.prauga.phone.dialer.app.settings.ui.ChoiceRow
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsGroup
import org.prauga.phone.dialer.app.settings.ui.SettingsGroupDefaults
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.app.settings.ui.settingsIcon
import org.prauga.phone.dialer.ui.TestTags

/** Settings for incoming calls: the answer gesture, flip to silence and the caller announcement. */
class IncomingCallSettingsFragment : SettingsComposeFragment() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Flip to silence needs an accelerometer to tell the phone is face down.
        val hasAccelerometer = remember {
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        }
        IncomingCallSettingsList(prefs, hasAccelerometer)
    }
}

/** The incoming call settings, stored in [prefs]; flip to silence only with [hasAccelerometer]. */
@Composable
internal fun IncomingCallSettingsList(prefs: SharedPreferences, hasAccelerometer: Boolean) {
    SettingsList {
        item(key = "incoming_calls") {
            SettingsGroup {
                if (hasAccelerometer) {
                    var flip by remember { mutableStateOf(prefs.getBoolean(IncomingCallSettings.KEY_FLIP_TO_SILENCE, false)) }
                    PvotSwitchRow(
                        title = stringResource(R.string.flip_to_silence_title),
                        summary = stringResource(R.string.flip_to_silence_summary),
                        checked = flip,
                        onCheckedChange = {
                            flip = it
                            prefs.edit { putBoolean(IncomingCallSettings.KEY_FLIP_TO_SILENCE, it) }
                        },
                        modifier = Modifier.testTag(TestTags.settingsRow(IncomingCallSettings.KEY_FLIP_TO_SILENCE)),
                        icon = settingsIcon(R.drawable.ic_device_mobile_off),
                        iconStyle = PvotRowIconStyle.Plain,
                    )
                }
                var reminders by remember { mutableStateOf(prefs.getBoolean(IncomingCallSettings.KEY_CALLBACK_REMINDERS, true)) }
                PvotSwitchRow(
                    title = stringResource(R.string.callback_reminders_title),
                    summary = stringResource(R.string.callback_reminders_summary),
                    checked = reminders,
                    onCheckedChange = {
                        reminders = it
                        prefs.edit { putBoolean(IncomingCallSettings.KEY_CALLBACK_REMINDERS, it) }
                    },
                    modifier = Modifier.testTag(TestTags.settingsRow(IncomingCallSettings.KEY_CALLBACK_REMINDERS)),
                    icon = settingsIcon(R.drawable.ic_bell),
                    iconStyle = PvotRowIconStyle.Plain,
                )
                StoredChoiceRow(
                    prefs = prefs,
                    key = IncomingCallSettings.KEY_ANNOUNCE_CALLER,
                    default = IncomingCallSettings.ANNOUNCE_NEVER,
                    title = stringResource(R.string.call_announcer_title),
                    choices = listOf(
                        Choice(IncomingCallSettings.ANNOUNCE_ALWAYS, stringResource(R.string.call_announcer_always)),
                        Choice(IncomingCallSettings.ANNOUNCE_HEADSET, stringResource(R.string.call_announcer_headset)),
                        Choice(IncomingCallSettings.ANNOUNCE_NEVER, stringResource(R.string.call_announcer_never)),
                    ),
                    icon = R.drawable.ic_speakerphone,
                )
            }
        }
        item(key = "announcer_footer") {
            Text(
                text = stringResource(R.string.call_announcer_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Lined up with the text of the rows in the card above.
                modifier = Modifier.padding(horizontal = SettingsGroupDefaults.CardPadding + SettingsGroupDefaults.RowPadding),
            )
        }
    }
}

/** A [ChoiceRow] stored as the string preference [key]. */
@Composable
private fun StoredChoiceRow(
    prefs: SharedPreferences,
    key: String,
    default: String,
    title: String,
    choices: List<Choice<String>>,
    icon: Int,
) {
    var selected by remember(key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    ChoiceRow(
        title = title,
        choices = choices,
        selected = selected,
        onSelect = {
            selected = it
            prefs.edit { putString(key, it) }
        },
        testTag = key,
        icon = icon,
    )
}
