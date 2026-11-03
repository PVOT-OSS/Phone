/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.reminder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags

/** Asks when to be reminded to call a number back, then sets the reminder; a see-through dialog. */
class CallbackReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER)
        if (number.isNullOrEmpty()) {
            finish()
            return
        }
        val name = intent.getStringExtra(EXTRA_NAME)
        setPvotContent {
            PhoneScreenTheme(fillBackground = false) {
                ReminderDelayDialog(
                    name = name ?: number,
                    onPick = { delay ->
                        val reminder = CallbackReminders.schedule(this, number, name, delay.dueAt(System.currentTimeMillis()))
                        val at = DateUtils.formatDateTime(this, reminder.atMillis, DateUtils.FORMAT_SHOW_TIME)
                        Toast.makeText(this, getString(R.string.callback_reminder_set, at), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"

        /** An intent for the dialog about [number], shown as [name] when there is one. */
        @JvmStatic
        fun newIntent(context: Context, number: String, name: String?): Intent =
            Intent(context, CallbackReminderActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** The choices of when to be reminded, one row each. */
@Composable
internal fun ReminderDelayDialog(name: String, onPick: (ReminderDelay) -> Unit, onDismiss: () -> Unit) {
    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.callback_reminder_dialog_title, name),
        icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_bell), contentDescription = null) },
        content = {
            Column(Modifier.testTag(TestTags.CALLBACK_REMINDER_DELAYS)) {
                for (delay in ReminderDelay.entries) {
                    PvotListRow(
                        title = stringResource(labelOf(delay)),
                        onClick = { onPick(delay) },
                        modifier = Modifier.testTag(TestTags.callbackReminderDelay(delay.name)),
                    )
                }
            }
        },
        actions = { PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = onDismiss) },
    )
}

private fun labelOf(delay: ReminderDelay): Int = when (delay) {
    ReminderDelay.IN_FIFTEEN_MINUTES -> R.string.callback_reminder_in_fifteen_minutes
    ReminderDelay.IN_AN_HOUR -> R.string.callback_reminder_in_an_hour
    ReminderDelay.THIS_EVENING -> R.string.callback_reminder_this_evening
    ReminderDelay.TOMORROW_MORNING -> R.string.callback_reminder_tomorrow_morning
}
