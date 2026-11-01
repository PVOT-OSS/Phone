/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.Choice
import org.prauga.phone.dialer.app.settings.ui.ChoiceRow
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsList

/** How contacts are sorted and how their names are shown. */
class DisplayOptionsSettingsFragment : SettingsComposeFragment() {

    @Composable
    override fun Content() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        SettingsList {
            item {
                StringChoiceRow(
                    prefs = prefs,
                    title = R.string.display_options_sort_list_by,
                    key = R.string.display_options_sort_list_by_key,
                    default = R.string.display_options_sort_by_given_name_value,
                    entries = R.array.contact_sort_order_entries,
                    values = R.array.contact_sort_order_values,
                )
            }
            item {
                StringChoiceRow(
                    prefs = prefs,
                    title = R.string.display_options_view_names_as,
                    key = R.string.display_options_view_names_as_key,
                    default = R.string.display_options_view_given_name_first_value,
                    entries = R.array.contact_display_order_entries,
                    values = R.array.contact_display_order_values,
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
        },
        testTag = keyName,
    )
}
