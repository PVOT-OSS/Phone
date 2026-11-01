/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.calls.R
import org.prauga.phone.dialer.app.settings.ui.Choice
import org.prauga.phone.dialer.app.settings.ui.ChoiceRow
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsGroup
import org.prauga.phone.dialer.app.settings.ui.SettingsGroupDefaults
import org.prauga.phone.dialer.app.settings.ui.SettingsList

/**
 * Lets the user choose whether each kind of unknown caller rings, rings silently or is declined.
 */
class ScreeningSettingsFragment : SettingsComposeFragment() {

    @Composable
    override fun Content() {
        val settings = ScreeningSettings.from(LocalContext.current)
        ScreeningSettingsList(settings)
    }
}

/** One row per [CallerCategory] and a closing note, reading and writing [settings]. */
@Composable
fun ScreeningSettingsList(settings: ScreeningSettings) {
    val actions = CategoryAction.entries.map { Choice(it, stringResource(labelOf(it))) }
    SettingsList {
        item(key = "categories") {
            SettingsGroup {
                for (category in CallerCategory.entries) {
                    key(category.name) {
                        var action by remember { mutableStateOf(settings.actionFor(category)) }
                        ChoiceRow(
                            title = stringResource(titleOf(category)),
                            summary = stringResource(descriptionOf(category)),
                            choices = actions,
                            selected = action,
                            onSelect = {
                                action = it
                                settings.setAction(category, it)
                            },
                            testTag = ScreeningSettings.keyFor(category),
                            icon = R.drawable.ic_user,
                        )
                    }
                }
            }
        }
        item(key = "footer") {
            Text(
                text = stringResource(R.string.screening_settings_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        horizontal =
                            SettingsGroupDefaults.CardPadding + SettingsGroupDefaults.RowPadding,
                        vertical = PvotSpacing.S,
                    ),
            )
        }
    }
}

private fun titleOf(category: CallerCategory) =
    when (category) {
        CallerCategory.PRIVATE_OR_HIDDEN -> R.string.screening_category_private_or_hidden
        CallerCategory.POSSIBLY_FAKED -> R.string.screening_category_possibly_faked
        CallerCategory.FIRST_TIME -> R.string.screening_category_first_time
    }

private fun descriptionOf(category: CallerCategory) =
    when (category) {
        CallerCategory.PRIVATE_OR_HIDDEN ->
            R.string.screening_category_private_or_hidden_description
        CallerCategory.POSSIBLY_FAKED -> R.string.screening_category_possibly_faked_description
        CallerCategory.FIRST_TIME -> R.string.screening_category_first_time_description
    }

private fun labelOf(action: CategoryAction) =
    when (action) {
        CategoryAction.RING -> R.string.screening_action_ring
        CategoryAction.SILENCE -> R.string.screening_action_silence
        CategoryAction.DECLINE -> R.string.screening_action_decline
    }
