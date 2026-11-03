/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.app.Dialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.function.Consumer
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotAlertDialog.Action

/** The dialogs the in-call screens show, drawn as Pvot dialogs in platform dialog windows. */
object InCallDialogs {

    /** A message after a call failed, with one button to close it. */
    @JvmStatic
    fun message(context: Context, message: CharSequence): Dialog =
        PvotAlertDialog(
            context,
            null,
            message,
            listOf(Action(context.getString(android.R.string.cancel))),
        )

    /** Wi-Fi is on but Wi-Fi calling is off: offers to open its settings. */
    @JvmStatic
    fun enableWifiCalling(context: Context, message: CharSequence, onEnable: Runnable): Dialog =
        PvotAlertDialog(
            context,
            null,
            message,
            listOf(
                Action(
                    context.getString(R.string.incall_enable_wifi_calling_button),
                    PvotDialogActionStyle.Primary,
                    onEnable,
                ),
                Action(context.getString(android.R.string.cancel)),
            ),
        )

    /** A video call could not be placed: offers a voice call instead. */
    @JvmStatic
    fun videoCallNotAvailable(context: Context, onVoiceCall: Runnable): Dialog =
        PvotAlertDialog(
            context,
            context.getString(R.string.video_call_not_available_title),
            context.getString(R.string.video_call_not_available_message),
            listOf(
                Action(
                    context.getString(R.string.voice_call),
                    PvotDialogActionStyle.Primary,
                    onVoiceCall,
                ),
                Action(context.getString(android.R.string.cancel)),
            ),
        )

    /** The number has a wait: asks before sending the tones after it. "No" cancels the dialog. */
    @JvmStatic
    fun postDial(context: Context, postDial: String, onSend: Runnable): Dialog =
        PvotAlertDialog(
            context,
            null,
            context.getString(R.string.wait_prompt_str) + postDial,
            listOf(
                Action(
                    context.getString(R.string.pause_prompt_yes),
                    PvotDialogActionStyle.Primary,
                    onSend,
                ),
                Action(context.getString(R.string.pause_prompt_no), cancels = true),
            ),
        )

    /**
     * A video call could not move from LTE to Wi-Fi. OK cancels the dialog and tells [onOk] whether
     * to stop showing this.
     */
    @JvmStatic
    fun wifiHandoverFailed(context: Context, onOk: Consumer<Boolean>): Dialog {
        var doNotShow by mutableStateOf(false)
        return PvotAlertDialog(
            context,
            context.getString(R.string.video_call_lte_to_wifi_failed_title),
            context.getString(R.string.video_call_lte_to_wifi_failed_message),
            listOf(
                Action(
                    context.getString(android.R.string.ok),
                    PvotDialogActionStyle.Primary,
                    { onOk.accept(doNotShow) },
                    cancels = true,
                )
            ),
        ) {
            CheckboxRow(
                context.getString(R.string.video_call_lte_to_wifi_failed_do_not_show),
                doNotShow,
            ) {
                doNotShow = it
            }
        }
    }

    /**
     * Calling an international number over Wi-Fi may cost money. Either button tells its callback
     * whether to warn every time; the dialog only closes through them.
     */
    @JvmStatic
    fun internationalCallOnWifi(
        context: Context,
        alwaysWarn: Boolean,
        onContinue: Consumer<Boolean>,
        onCancel: Consumer<Boolean>,
    ): Dialog {
        var warn by mutableStateOf(alwaysWarn)
        return PvotAlertDialog(
                context,
                null,
                null,
                listOf(
                    Action(
                        context.getString(android.R.string.ok),
                        PvotDialogActionStyle.Primary,
                        { onContinue.accept(warn) },
                    ),
                    Action(
                        context.getString(android.R.string.cancel),
                        onClick = { onCancel.accept(warn) },
                    ),
                ),
            ) {
                Text(
                    text = context.getString(R.string.details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                CheckboxRow(context.getString(R.string.always_warn), warn) { warn = it }
            }
            .apply {
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
    }
}

/** A checkbox with its label; the whole row toggles it. */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
