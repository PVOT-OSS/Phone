/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.interactions

import android.app.Dialog
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prauga.pvot.designsystem.components.list.PvotListRow
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotDialogCheckboxRow

/**
 * The Pvot dialog that asks which of a contact's numbers to call or text, with a "Remember this
 * choice" checkbox under the list. Tapping a number reports it and closes the dialog; back or a tap
 * outside closes it without a choice.
 */
object PhoneDisambiguationDialog {

    /** One number in the list: its type, such as "Mobile", and the number itself. */
    class Choice(val label: CharSequence?, val number: String?)

    /** Told which number was tapped and whether to remember it as the default. */
    fun interface OnChosenListener {
        fun onChosen(position: Int, setPrimary: Boolean)
    }

    @JvmStatic
    fun create(
        context: Context,
        title: CharSequence,
        choices: List<Choice>,
        listener: OnChosenListener,
    ): Dialog {
        var setPrimary by mutableStateOf(false)
        lateinit var dialog: Dialog
        dialog = PvotAlertDialog(context, title, null, emptyList()) {
            // The list scrolls inside whatever height the dialog leaves it, above the checkbox.
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    choices.forEachIndexed { position, choice ->
                        PvotListRow(
                            title = choice.label?.toString().orEmpty(),
                            supportingText = choice.number,
                            onClick = {
                                listener.onChosen(position, setPrimary)
                                dialog.dismiss()
                            },
                        )
                    }
                }
                PvotDialogCheckboxRow(context.getString(R.string.make_primary), setPrimary) { setPrimary = it }
            }
        }
        return dialog
    }
}
