/*
 * Copyright (C) 2018 The Android Open Source Project
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

package org.prauga.phone.incallui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.DialogFragment
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import java.lang.ref.WeakReference
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotAlertDialog.Action
import org.prauga.phone.dialer.ui.PvotDialogText
import org.prauga.phone.incallui.ContactInfoCache.ContactCacheEntry
import org.prauga.phone.incallui.ContactInfoCache.ContactInfoCacheCallback
import org.prauga.phone.incallui.call.CallList

/** Dialog that shown to user when receiving RTT request mid call. */
class RttRequestDialogFragment : DialogFragment() {

    /** "Who is asking", filled in once the contact lookup finishes. */
    private val details = mutableStateOf<String?>(null)

    override fun onCreateDialog(bundle: Bundle?): Dialog {
        super.onCreateDialog(bundle)
        LogUtil.enterBlock("RttRequestDialogFragment.onCreateDialog")

        val cache = ContactInfoCache.getInstance(requireContext())
        val dialerCall =
            CallList.getInstance().getCallById(requireArguments().getString(ARG_CALL_ID))
        cache.findInfo(dialerCall, false, ContactLookupCallback(this))

        val dialog =
            createDialog(
                requireActivity(),
                details,
                ::onPositiveButtonClick,
                ::onNegativeButtonClick,
            )
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    private fun onPositiveButtonClick() {
        LogUtil.enterBlock("RttRequestDialogFragment.onPositiveButtonClick")

        val call = CallList.getInstance().getCallById(requireArguments().getString(ARG_CALL_ID))
        call!!.respondToRttRequest(true, requireArguments().getInt(ARG_RTT_REQUEST_ID))
        dismiss()
    }

    private fun onNegativeButtonClick() {
        LogUtil.enterBlock("RttRequestDialogFragment.onNegativeButtonClick")

        val call = CallList.getInstance().getCallById(requireArguments().getString(ARG_CALL_ID))
        call!!.respondToRttRequest(false, requireArguments().getInt(ARG_RTT_REQUEST_ID))
        dismiss()
    }

    private fun setNameOrNumber(nameOrNumber: CharSequence?) {
        details.value = getString(R.string.rtt_request_dialog_details, nameOrNumber)
    }

    private class ContactLookupCallback(rttRequestDialogFragment: RttRequestDialogFragment) :
        ContactInfoCacheCallback {

        private val rttRequestDialogFragmentWeakReference = WeakReference(rttRequestDialogFragment)

        override fun onContactInfoComplete(callId: String?, entry: ContactCacheEntry) {
            val fragment = rttRequestDialogFragmentWeakReference.get()
            fragment?.setNameOrNumber(getNameOrNumber(entry, fragment.requireContext()))
        }

        private fun getNameOrNumber(entry: ContactCacheEntry, context: Context): CharSequence? {
            val preferredName =
                ContactsComponent.get(context)
                    .contactDisplayPreferences()
                    .getDisplayName(entry.namePrimary, entry.nameAlternative)
            if (TextUtils.isEmpty(preferredName)) {
                return if (TextUtils.isEmpty(entry.number)) {
                    null
                } else {
                    PhoneNumberUtils.createTtsSpannable(
                        BidiFormatter.getInstance()
                            .unicodeWrap(entry.number, TextDirectionHeuristics.LTR)
                    )
                }
            }
            return preferredName
        }

        override fun onImageLoadComplete(callId: String?, entry: ContactCacheEntry) {}
    }

    companion object {
        /**
         * The dialog itself: who asks (once [details] is known), what RTT is, and Accept or
         * Decline, each of which runs its callback and closes the dialog.
         */
        @VisibleForTesting
        internal fun createDialog(
            context: Context,
            details: State<String?>,
            onAccept: () -> Unit,
            onDecline: () -> Unit,
        ): Dialog =
            PvotAlertDialog(
                context,
                context.getString(R.string.rtt_request_dialog_title),
                null,
                listOf(
                    Action(
                        context.getString(R.string.rtt_button_accept_request),
                        PvotDialogActionStyle.Primary,
                        onAccept,
                    ),
                    Action(
                        context.getString(R.string.rtt_button_decline_request),
                        onClick = onDecline,
                    ),
                ),
            ) {
                details.value?.let { PvotDialogText(it) }
                PvotDialogText(context.getString(R.string.rtt_request_dialog_more_information))
            }

        /** Key in the arguments bundle for call id. */
        private const val ARG_CALL_ID = "call_id"

        private const val ARG_RTT_REQUEST_ID = "rtt_request_id"

        /**
         * Returns a new instance of [RttRequestDialogFragment] with the given callback.
         *
         * Prefer this method over the default constructor.
         */
        @JvmStatic
        fun newInstance(callId: String?, rttRequestId: Int): RttRequestDialogFragment =
            RttRequestDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_CALL_ID, Assert.isNotNull(callId))
                        putInt(ARG_RTT_REQUEST_ID, rttRequestId)
                    }
            }
    }
}
