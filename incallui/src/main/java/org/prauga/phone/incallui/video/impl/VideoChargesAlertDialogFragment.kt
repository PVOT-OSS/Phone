/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.video.impl

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.telecom.Call.Details
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.os.UserManagerCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotAlertDialog.Action
import org.prauga.phone.dialer.ui.PvotDialogCheckboxRow
import org.prauga.phone.incallui.R
import org.prauga.phone.incallui.call.CallList

/** Alert dialog for video charges. */
class VideoChargesAlertDialogFragment : DialogFragment() {

    override fun onCreateDialog(bundle: Bundle?): Dialog {
        super.onCreateDialog(bundle)

        if (!shouldShow(requireActivity(), requireArguments().getString(ARG_CALL_ID))) {
            throw IllegalStateException(
                "shouldShow indicated VideoChargesAlertDialogFragment should not have showed"
            )
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val alertDialog =
            createDialog(requireActivity()) { onPositiveButtonClicked(preferences, it) }
        isCancelable = false
        return alertDialog
    }

    private fun onPositiveButtonClicked(preferences: SharedPreferences, isChecked: Boolean) {
        LogUtil.i(
            "VideoChargesAlertDialogFragment.onPositiveButtonClicked",
            "isChecked: %b",
            isChecked,
        )
        preferences.edit { putBoolean(KEY_DO_NOT_SHOW_VIDEO_CHARGES_ALERT, isChecked) }

        val dialerCall =
            CallList.getInstance().getCallById(requireArguments().getString(ARG_CALL_ID))
        dialerCall?.setDidDismissVideoChargesAlertDialog(true)
    }

    companion object {
        /**
         * The dialog itself: the warning, "Do not show again" and OK, which reports the checkbox.
         */
        @VisibleForTesting
        internal fun createDialog(
            context: Context,
            onOk: (doNotShowAgain: Boolean) -> Unit,
        ): Dialog {
            var doNotShow by mutableStateOf(false)
            return PvotAlertDialog(
                context,
                null,
                context.getString(R.string.videocall_charges_alert_dialog_description),
                listOf(
                    Action(
                        context.getString(android.R.string.ok),
                        PvotDialogActionStyle.Primary,
                        { onOk(doNotShow) },
                    )
                ),
            ) {
                PvotDialogCheckboxRow(context.getString(R.string.do_not_show_again), doNotShow) {
                    doNotShow = it
                }
            }
        }

        /** Preference key for whether to show the alert dialog for video charges next time. */
        private const val KEY_DO_NOT_SHOW_VIDEO_CHARGES_ALERT =
            "key_do_not_show_video_charges_alert"

        /** Key in the arguments bundle for call id. */
        private const val ARG_CALL_ID = "call_id"

        /**
         * Returns `true` if an [VideoChargesAlertDialogFragment] should be shown.
         *
         * Attempting to show an VideoChargesAlertDialogFragment when this method returns `false`
         * will result in an [IllegalStateException].
         */
        @JvmStatic
        fun shouldShow(context: Context, callId: String?): Boolean {
            val call = CallList.getInstance().getCallById(callId)
            if (call == null) {
                LogUtil.i("VideoChargesAlertDialogFragment.shouldShow", "null call")
                return false
            }

            if (call.hasProperty(Details.PROPERTY_WIFI)) {
                return false
            }

            if (call.didDismissVideoChargesAlertDialog()) {
                LogUtil.i(
                    "VideoChargesAlertDialogFragment.shouldShow",
                    "The dialog has been dismissed by user",
                )
                return false
            }

            if (!call.showVideoChargesAlertDialog()) {
                return false
            }

            if (!UserManagerCompat.isUserUnlocked(context)) {
                LogUtil.i(
                    "VideoChargesAlertDialogFragment.shouldShow",
                    "user locked, returning false",
                )
                return false
            }

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (preferences.getBoolean(KEY_DO_NOT_SHOW_VIDEO_CHARGES_ALERT, false)) {
                LogUtil.i(
                    "VideoChargesAlertDialogFragment.shouldShow",
                    "Video charges alert has been disabled by user, returning false",
                )
                return false
            }

            return true
        }

        /**
         * Returns a new instance of [VideoChargesAlertDialogFragment]
         *
         * Prefer this method over the default constructor.
         */
        @JvmStatic
        fun newInstance(callId: String): VideoChargesAlertDialogFragment =
            VideoChargesAlertDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_CALL_ID, Assert.isNotNull(callId)) }
            }
    }
}
