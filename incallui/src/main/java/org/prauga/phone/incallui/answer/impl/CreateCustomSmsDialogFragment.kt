/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prauga.phone.incallui.answer.impl

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.R
import org.prauga.phone.incallui.incalluilock.InCallUiLock

/** Lets the user write their own reply when declining a call with a message. */
class CreateCustomSmsDialogFragment : AppCompatDialogFragment() {

    private var text by mutableStateOf("")
    private var inCallUiLock: InCallUiLock? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        text = savedInstanceState?.getCharSequence(ARG_ENTERED_TEXT)?.toString().orEmpty()
        inCallUiLock = holder().acquireInCallUiLock("CreateCustomSmsDialogFragment")
        val context = requireContext()
        return PvotAlertDialog(
                context,
                context.getString(R.string.call_incoming_respond_via_sms_custom_message),
                null,
                listOf(
                    PvotAlertDialog.Action(
                        context.getString(R.string.call_incoming_custom_message_send),
                        PvotDialogActionStyle.Primary,
                        { holder().customSmsCreated(text.trim()) },
                        enabled = { text.isNotBlank() },
                    ),
                    PvotAlertDialog.Action(
                        context.getString(R.string.call_incoming_custom_message_cancel)
                    ),
                ),
            ) {
                PvotTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.CUSTOM_SMS_INPUT),
                )
            }
            .apply {
                setCancelable(true)
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(ARG_ENTERED_TEXT, text)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        inCallUiLock?.release()
        holder().customSmsDismissed()
    }

    private fun holder() = FragmentUtils.getParentUnsafe(this, CreateCustomSmsHolder::class.java)

    /** Call back for [CreateCustomSmsDialogFragment] */
    interface CreateCustomSmsHolder {
        fun acquireInCallUiLock(tag: String): InCallUiLock

        fun customSmsCreated(text: CharSequence)

        fun customSmsDismissed()
    }

    companion object {
        private const val ARG_ENTERED_TEXT = "enteredText"

        @JvmStatic fun newInstance() = CreateCustomSmsDialogFragment()
    }
}
