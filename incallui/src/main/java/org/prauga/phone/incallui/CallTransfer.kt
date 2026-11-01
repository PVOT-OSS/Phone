/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.prauga.phone.incallui.R
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.DialerCall

/**
 * Handing a call over: to the held call (consultative transfer, so the two other people end up
 * talking to each other) or straight to a number (blind transfer); and adding people to a
 * conference the network hosts.
 */
object CallTransfer {

    /**
     * Whether [call] can be transferred: to the held call, or to a number the user enters when the
     * network supports that; either way the carrier must allow transferring calls.
     */
    @JvmStatic
    fun canTransfer(context: Context, call: DialerCall): Boolean =
        canTransferToHeldCall(context, call) || canTransferToNumber(context, call)

    /** Whether [call] can be handed over to the held call. */
    @JvmStatic
    fun canTransferToHeldCall(context: Context, call: DialerCall): Boolean =
        // Telecom opened transferring calls to phone apps in Android 17.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            call.can(Call.Details.CAPABILITY_TRANSFER_CONSULTATIVE) &&
            CallList.getInstance().backgroundCall != null &&
            carrierAllowsTransfer(context, call)

    /** Whether [call] can be handed over to a number without calling it first. */
    @JvmStatic
    fun canTransferToNumber(context: Context, call: DialerCall): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            call.can(Call.Details.CAPABILITY_TRANSFER) &&
            carrierAllowsTransfer(context, call)

    /** Whether people can be added straight to [call], a conference the network hosts. */
    @JvmStatic
    fun canAddParticipant(call: DialerCall): Boolean =
        call.isConferenceCall && call.can(Call.Details.CAPABILITY_ADD_PARTICIPANT)

    private fun carrierAllowsTransfer(context: Context, call: DialerCall): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val subId = call.accountHandle
            ?.let { context.getSystemService(TelephonyManager::class.java).createForPhoneAccountHandle(it) }
            ?.subscriptionId
            ?: SubscriptionManager.getDefaultSubscriptionId()
        val config = runCatching {
            context.getSystemService(CarrierConfigManager::class.java).getConfigForSubId(subId)
        }.getOrNull()
        return config?.getBoolean(KEY_ALLOW_TRANSFER) == true
    }

    /** Carrier config key allowing IMS call transfer; not part of the public SDK. */
    private const val KEY_ALLOW_TRANSFER = "carrier_allow_transfer_ims_call_bool"
}

/** Asks before transferring the active call to the held one. */
class CallTransferDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val calls = CallList.getInstance()
        val active = calls.activeCall
        val held = calls.backgroundCall
        val unknown = getString(R.string.unknown)
        val message = getString(
            R.string.incall_transfer_dialog_message,
            active?.let(::nameOf) ?: unknown,
            held?.let(::nameOf) ?: unknown,
        )
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.incall_transfer_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.incall_transfer_dialog_confirm) { _, _ ->
                val from = CallList.getInstance().activeCall
                val to = CallList.getInstance().backgroundCall
                if (from != null && to != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                    from.consultativeTransfer(to)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun nameOf(call: DialerCall): String? =
        ContactInfoCache.getInstance(requireContext()).getInfo(call.id)?.namePrimary?.takeIf { it.isNotBlank() }
            ?: call.number?.takeIf { it.isNotBlank() }

    companion object {
        const val TAG = "tag_call_transfer_dialog"
    }
}
