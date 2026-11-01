/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.app.Dialog
import android.app.PendingIntent
import android.os.Bundle
import android.telephony.TelephonyManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.incallui.call.CallList

/**
 * When an emergency call can't find a mobile network, Android offers to reach emergency services by
 * satellite instead and hands the phone app the app to open for it. The call screen asks the user,
 * bringing itself forward if needed.
 */
object EmergencySatellite {

    /** Satellite SOS: messages go to an SOS provider, which forwards them to emergency services. */
    private const val HANDOVER_TYPE_SOS = 1

    /** Text to 911 through the carrier's satellite messaging, in the messaging app. */
    private const val HANDOVER_TYPE_T911 = 2

    /** The offer waiting for the call screen to come forward. */
    private var pending: Bundle? = null

    /**
     * Telephony's [TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE] arrived for the call [callId].
     */
    @JvmStatic
    fun onEvent(callId: String, extras: Bundle?) {
        val intent =
            extras?.let {
                runCatching {
                        it.getParcelable(
                            TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT,
                            PendingIntent::class.java,
                        )
                    }
                    .getOrNull()
            }
        if (intent == null) {
            // Nothing to open: the device has no app for it.
            LogUtil.i("EmergencySatellite.onEvent", "no app to hand over to")
            return
        }
        val type =
            extras.getInt(
                TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE,
                HANDOVER_TYPE_SOS,
            )
        pending = bundleOf(ARG_INTENT to intent, ARG_TYPE to type, ARG_CALL_ID to callId)
        val presenter = InCallPresenter.getInstance()
        val activity = presenter.activity
        if (activity != null && presenter.isShowingInCallUi) {
            showPending(activity)
        } else {
            presenter.showInCall(false, false)
        }
    }

    /**
     * Shows the offer waiting for the call screen, if any; the call screen calls this on resume.
     */
    @JvmStatic
    fun showPending(activity: FragmentActivity) {
        val args = pending ?: return
        val manager = activity.supportFragmentManager
        if (manager.isStateSaved) return
        pending = null
        // The offer is only for the call it came with.
        if (CallList.getInstance().getCallById(args.getString(ARG_CALL_ID)) == null) return
        (manager.findFragmentByTag(TAG) as? DialogFragment)?.dismiss()
        OfferDialogFragment().apply { arguments = args }.show(manager, TAG)
    }

    /** Asks whether to switch to satellite messaging. */
    class OfferDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val args = requireArguments()
            val intent = args.getParcelable(ARG_INTENT, PendingIntent::class.java)
            val textTo911 = args.getInt(ARG_TYPE) == HANDOVER_TYPE_T911
            return PvotAlertDialog(
                requireContext(),
                getString(R.string.emergency_satellite_title),
                getString(
                    if (textTo911) R.string.emergency_satellite_message_t911
                    else R.string.emergency_satellite_message_sos
                ),
                listOf(
                    PvotAlertDialog.Action(
                        getString(
                            if (textTo911) R.string.emergency_satellite_open_t911
                            else R.string.emergency_satellite_open_sos
                        ),
                        PvotDialogActionStyle.Primary,
                        {
                            runCatching { intent?.send() }
                                .onFailure {
                                    LogUtil.e(
                                        "EmergencySatellite",
                                        "can't open the satellite app",
                                        it,
                                    )
                                }
                        },
                    ),
                    PvotAlertDialog.Action(
                        getString(R.string.emergency_satellite_keep_trying),
                        cancels = true,
                    ),
                ),
            )
        }
    }

    private const val TAG = "tag_emergency_satellite"
    private const val ARG_INTENT = "intent"
    private const val ARG_TYPE = "type"
    private const val ARG_CALL_ID = "call_id"
}
