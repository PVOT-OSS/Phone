/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.list.PvotListRowDefaults
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.ui.TestTags

/** One choice in a [ChoiceRow]: the value stored for it and the label shown. */
data class Choice<T>(val value: T, val label: String)

/**
 * A setting with one value out of a few, replacing a ListPreference. The row shows the current
 * choice; tapping it opens a dialog listing every choice, the current one selected. Picking one
 * stores it and closes the dialog.
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
    @DrawableRes icon: Int? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == selected }
    PvotPreferenceRow(
        title = title,
        onClick = { open = true },
        modifier = modifier.testTag(TestTags.settingsRow(testTag)),
        summary = summary,
        value = current?.label,
        icon = settingsIcon(icon),
        iconStyle = PvotRowIconStyle.Plain,
        showChevron = false,
    )
    // A window dialog for now; it moves to PvotLib's in-window glass dialog once that exists.
    PvotDialog(
        visible = open,
        onDismissRequest = { open = false },
        title = title,
        content = {
            ChoiceList(choices, selected) { value ->
                open = false
                if (value != selected) onSelect(value)
            }
        },
    ) {
        PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = { open = false })
    }
}

/** The choices as radio rows, the whole row the target. */
@Composable
private fun <T> ChoiceList(choices: List<Choice<T>>, selected: T, onPick: (T) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        for (choice in choices) {
            val isSelected = choice.value == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PvotListRowDefaults.MinHeight)
                    .clip(PvotShapes.Card)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(choice.value) })
                    .testTag(TestTags.settingsOption(choice.value.toString()))
                    .padding(horizontal = PvotSpacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
            ) {
                // Material's radio with its defaults until PvotLib has one; the row takes the click.
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
