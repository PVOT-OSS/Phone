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
import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import org.prauga.phone.incallui.R
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.common.DpUtil
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

    private lateinit var numberField: EditText

    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@registerForActivityResult
        val number = runCatching {
            requireContext().contentResolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        if (!number.isNullOrBlank()) numberField.setText(number)
    }

    private val action: Action
        get() = Action.valueOf(requireArguments().getString(ARG_ACTION)!!)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val padding = DpUtil.dpToPx(context, 20f).toInt()
        numberField = EditText(context).apply {
            id = R.id.number_prompt_field
            inputType = InputType.TYPE_CLASS_PHONE
            setHint(R.string.number_prompt_hint)
            addTextChangedListener(PhoneNumberFormattingTextWatcher())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pick = ImageButton(context).apply {
            id = R.id.number_prompt_pick_contact
            setImageResource(R.drawable.quantum_ic_person_vd_theme_24)
            contentDescription = getString(R.string.number_prompt_pick_contact)
            val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
            background = attrs.getDrawable(0)
            attrs.recycle()
            setOnClickListener {
                runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) }
                    .onFailure { LogUtil.e("NumberPromptDialogFragment", "no contacts picker", it) }
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding / 2, padding / 2, 0)
            addView(numberField)
            addView(pick)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(action.title)
            .setView(content)
            .setPositiveButton(action.confirm) { _, _ -> perform(numberField.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
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
