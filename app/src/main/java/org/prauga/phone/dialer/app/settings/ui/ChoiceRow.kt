/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags

/** One choice in a [ChoiceRow]: the value stored for it and the label shown. */
data class Choice<T>(val value: T, val label: String)

/**
 * A setting with one value out of a few, replacing a ListPreference. The row shows the current
 * choice; tapping it opens a sheet with every choice, the current one ticked.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    testTag: String = title,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == selected }
    PvotPreferenceRow(
        title = title,
        onClick = { open = true },
        modifier = modifier.testTag(TestTags.settingsRow(testTag)),
        summary = summary,
        value = current?.label,
    )
    PvotBottomSheet(visible = open, onDismissRequest = { open = false }, title = title) {
        for (choice in choices) {
            PvotSheetItem(
                title = choice.label,
                onClick = {
                    open = false
                    if (choice.value != selected) onSelect(choice.value)
                },
                modifier = Modifier.testTag(TestTags.settingsOption(choice.value.toString())),
                icon = if (choice.value == selected) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}
