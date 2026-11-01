/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
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
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.app.settings.ui.settingsIcon

/** The app's theme, the call screen's rotation, and how contacts are sorted and named. */
class DisplayOptionsSettingsFragment : SettingsComposeFragment() {

    @Composable
    override fun Content() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        DisplayOptionsList(prefs)
    }
}

/** The theme, the call screen's rotation, and how contacts are sorted and named, stored in [prefs]. */
@Composable
internal fun DisplayOptionsList(prefs: SharedPreferences) {
    val context = LocalContext.current
    SettingsList {
        item(key = "display_options") {
            SettingsGroup {
                StringChoiceRow(
                    prefs = prefs,
                    title = R.string.display_options_theme,
                    key = R.string.display_options_theme_key,
                    default = R.string.display_options_theme_system_value,
                    entries = R.array.display_options_theme_entries,
                    values = R.array.display_options_theme_values,
                    icon = R.drawable.ic_sun_moon,
                    onChanged = { DisplayOptions.applyTheme(context, it) },
                )
                val portraitKey = stringResource(R.string.display_options_force_portrait_key)
                var portrait by remember { mutableStateOf(prefs.getBoolean(portraitKey, false)) }
                PvotSwitchRow(
                    title = stringResource(R.string.display_options_force_portrait),
                    summary = stringResource(R.string.display_options_force_portrait_summary),
                    checked = portrait,
                    onCheckedChange = {
                        portrait = it
                        prefs.edit { putBoolean(portraitKey, it) }
                    },
                    modifier = Modifier.testTag(portraitKey),
                    icon = settingsIcon(R.drawable.ic_device_mobile_rotated),
                    iconStyle = PvotRowIconStyle.Plain,
                )
                StringChoiceRow(
                    prefs = prefs,
                    title = R.string.display_options_sort_list_by,
                    key = R.string.display_options_sort_list_by_key,
                    default = R.string.display_options_sort_by_given_name_value,
                    entries = R.array.contact_sort_order_entries,
                    values = R.array.contact_sort_order_values,
                    icon = R.drawable.ic_arrows_exchange,
                )
                StringChoiceRow(
                    prefs = prefs,
                    title = R.string.display_options_view_names_as,
                    key = R.string.display_options_view_names_as_key,
                    default = R.string.display_options_view_given_name_first_value,
                    entries = R.array.contact_display_order_entries,
                    values = R.array.contact_display_order_values,
                    icon = R.drawable.ic_typography,
                )
            }
        }
    }
}

/** A [ChoiceRow] stored as a string preference, its choices from paired label and value arrays. */
@Composable
internal fun StringChoiceRow(
    prefs: SharedPreferences,
    @StringRes title: Int,
    @StringRes key: Int,
    @StringRes default: Int,
    @ArrayRes entries: Int,
    @ArrayRes values: Int,
    @DrawableRes icon: Int? = null,
    onChanged: (String) -> Unit = {},
) {
    val keyName = stringResource(key)
    val defaultValue = stringResource(default)
    val choices = stringArrayResource(values).zip(stringArrayResource(entries)) { value, label -> Choice(value, label) }
    var selected by remember(keyName) { mutableStateOf(prefs.getString(keyName, defaultValue) ?: defaultValue) }
    ChoiceRow(
        title = stringResource(title),
        choices = choices,
        selected = selected,
        onSelect = {
            selected = it
            prefs.edit { putString(keyName, it) }
            onChanged(it)
        },
        testTag = keyName,
        icon = icon,
    )
}
