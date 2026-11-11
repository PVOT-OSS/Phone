/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package org.prauga.phone.dialer.postcall

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.calls.R
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.util.DialerUtils
import org.prauga.phone.dialer.util.IntentUtil

/** Activity used to send post call messages after a phone call. */
class PostCallActivity : ComponentActivity() {

    private var useRcs = false

    override fun onCreate(bundle: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(bundle)

        useRcs = intent.getBooleanExtra(KEY_RCS_POST_CALL, false)
        LogUtil.i("PostCallActivity.onCreate", "useRcs: %b", useRcs)

        val postCallCharLimit =
            if (useRcs) resources.getInteger(R.integer.post_call_char_limit) else null
        val messages =
            listOf(
                getString(R.string.post_call_message_1),
                getString(R.string.post_call_message_2),
                getString(R.string.post_call_message_3),
            )
        setPvotContent {
            PhoneScreenTheme(fillBackground = false) {
                PostCallSheet(
                    messages,
                    postCallCharLimit,
                    onSend = ::sendMessage,
                    onDismiss = ::finish,
                )
            }
        }
    }

    /**
     * Sends [message] to the caller over RCS when the call offered it. Otherwise opens the
     * messaging app with the text filled in, so the user sends it there and this app needs no SMS
     * permission of its own; it then doesn't claim the message was sent.
     */
    private fun sendMessage(message: String) {
        val number = Assert.isNotNull(intent.getStringExtra(KEY_PHONE_NUMBER))
        if (useRcs) {
            LogUtil.i("PostCallActivity.onMessageFragmentSendMessage", "sending post call Rcs.")
            PostCall.onMessageSent(this, number)
        } else {
            LogUtil.i("PostCallActivity.sendMessage", "Opening the messaging app.")
            DialerUtils.startActivityWithErrorToast(
                this,
                IntentUtil.getSendSmsIntent(number).putExtra("sms_body", message),
            )
        }
        finish()
    }

    companion object {
        const val KEY_PHONE_NUMBER = "phone_number"
        const val KEY_RCS_POST_CALL = "rcs_post_call"

        @JvmStatic
        fun newIntent(context: Context, number: String, isRcsPostCall: Boolean): Intent {
            val intent = Intent(Assert.isNotNull(context), PostCallActivity::class.java)
            intent.putExtra(KEY_PHONE_NUMBER, Assert.isNotNull(number))
            intent.putExtra(KEY_RCS_POST_CALL, isRcsPostCall)
            return intent
        }
    }
}

/**
 * The sheet asking why you called: quick replies that fill the message in, a field for your own
 * message (at most [charLimit] characters when there is one) and Send, which also the keyboard's
 * send key does. Sending an empty message does nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostCallSheet(
    messages: List<String>,
    charLimit: Int?,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var message by rememberSaveable { mutableStateOf("") }
    val send = { if (message.isNotEmpty()) onSend(message) }
    PvotBottomSheet(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.post_call_message),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.S),
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
        ) {
            for (reply in messages) {
                SuggestionChip(
                    onClick = { message = if (charLimit != null) reply.take(charLimit) else reply },
                    label = { Text(reply) },
                )
            }
        }
        PvotTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.S),
            placeholder = stringResource(R.string.custom_message_hint),
            maxLength = charLimit,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        PvotButton(
            text = stringResource(R.string.post_call_send_message),
            onClick = send,
            modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.M),
            icon = painterResource(R.drawable.ic_send),
            enabled = message.isNotEmpty(),
        )
    }
}
