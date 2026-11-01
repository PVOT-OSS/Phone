/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.app.Activity
import android.app.Dialog
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.call.CallList

/**
 * Asks for a phone number, typed or picked from contacts, and then forwards a ringing call to it,
 * transfers the call to it or adds it to a conference call.
 */
class NumberPromptDialogFragment : DialogFragment() {

    /** What happens to the call once a number is chosen. */
    enum class Action(val title: Int, val confirm: Int) {
        FORWARD(R.string.number_prompt_forward_title, R.string.number_prompt_forward_confirm),
        TRANSFER(R.string.number_prompt_transfer_title, R.string.number_prompt_transfer_confirm),
        ADD_PARTICIPANT(R.string.number_prompt_add_title, R.string.number_prompt_add_confirm),
    }

    private val number = mutableStateOf("")

    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@registerForActivityResult
        val picked = runCatching {
            requireContext().contentResolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        if (!picked.isNullOrBlank()) number.value = picked
    }

    private val action: Action
        get() = Action.valueOf(requireArguments().getString(ARG_ACTION)!!)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        savedInstanceState?.getString(STATE_NUMBER)?.let { number.value = it }
        val dialog = PvotAlertDialog(
            requireContext(),
            getString(action.title),
            null,
            listOf(
                PvotAlertDialog.Action(
                    getString(action.confirm),
                    PvotDialogActionStyle.Primary,
                    { perform(number.value) },
                    enabled = { number.value.isNotBlank() },
                ),
                PvotAlertDialog.Action(getString(android.R.string.cancel), cancels = true),
            ),
        ) { NumberField(number, ::pickFromContacts) }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_NUMBER, number.value)
    }

    private fun pickFromContacts() {
        runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) }
            .onFailure { LogUtil.e("NumberPromptDialogFragment", "no contacts picker", it) }
    }

    private fun perform(number: String) {
        if (number.isBlank()) return
        val call = CallList.getInstance().getCallById(requireArguments().getString(ARG_CALL_ID))
        if (call == null) {
            LogUtil.i("NumberPromptDialogFragment.perform", "call is gone")
            return
        }
        val uri = Uri.fromParts(PHONE_SCHEME, number, null)
        when (action) {
            Action.FORWARD -> call.deflect(uri)
            // Transfer is only offered from Android 17, where Telecom opened it to phone apps.
            Action.TRANSFER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) call.transfer(uri)
            Action.ADD_PARTICIPANT -> call.addConferenceParticipant(uri)
        }
    }

    companion object {
        const val TAG = "tag_number_prompt"
        private const val ARG_ACTION = "action"
        private const val ARG_CALL_ID = "call_id"
        private const val STATE_NUMBER = "number"
        private const val PHONE_SCHEME = "tel"

        /**
         * Shows the prompt for [action] on the call [callId], once the phone is unlocked: forwarding
         * or transferring a call, or picking a contact, shouldn't be possible from the lock screen.
         */
        @JvmStatic
        fun show(activity: FragmentActivity, action: Action, callId: String) {
            val showPrompt = {
                if (!activity.isFinishing && !activity.supportFragmentManager.isStateSaved) {
                    NumberPromptDialogFragment()
                        .apply { arguments = bundleOf(ARG_ACTION to action.name, ARG_CALL_ID to callId) }
                        .show(activity.supportFragmentManager, TAG)
                }
            }
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked != true) {
                showPrompt()
                return
            }
            keyguard.requestDismissKeyguard(
                activity,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = showPrompt()
                },
            )
        }
    }
}

/** The number to use, typed or picked from contacts, focused so the keypad opens at once. */
@Composable
private fun NumberField(number: MutableState<String>, onPickContact: () -> Unit) {
    val focus = remember { FocusRequester() }
    PvotTextField(
        value = number.value,
        onValueChange = { number.value = it },
        placeholder = stringResource(R.string.number_prompt_hint),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(TestTags.NUMBER_PROMPT_FIELD),
        trailingIcon = {
            IconButton(onClick = onPickContact) {
                Icon(painterResource(R.drawable.ic_user), stringResource(R.string.number_prompt_pick_contact))
            }
        },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
