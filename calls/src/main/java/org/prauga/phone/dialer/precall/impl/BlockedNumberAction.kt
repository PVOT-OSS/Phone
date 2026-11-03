/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.precall.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.provider.BlockedNumberContract
import android.telecom.PhoneAccount
import android.telephony.PhoneNumberUtils
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import org.prauga.phone.calls.R
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.precall.PreCallAction
import org.prauga.phone.dialer.precall.PreCallCoordinator

/**
 * Asks before calling a number the user blocked, as a reminder that they won't get that number's
 * calls back.
 *
 * The block list is checked on the main thread: pre-call decides there, before any screen exists,
 * whether a step needs one, and doing it later would send every call through the pre-call screen.
 * It is one small query on the system's block list, as the call screen already does for each
 * incoming call.
 */
@SuppressLint("ThreadConstraint", "WrongThread")
class BlockedNumberAction : PreCallAction {

    private var dialog: AlertDialog? = null

    override fun requiresUi(context: Context, builder: CallIntentBuilder): Boolean =
        isBlocked(context, builder)

    override fun runWithoutUi(context: Context, builder: CallIntentBuilder) = Unit

    override fun runWithUi(coordinator: PreCallCoordinator) {
        val activity = coordinator.activity
        if (!isBlocked(activity, coordinator.builder)) return

        val number =
            BidiFormatter.getInstance()
                .unicodeWrap(
                    PhoneNumberUtils.formatNumber(numberOf(coordinator.builder), null)
                        ?: numberOf(coordinator.builder),
                    TextDirectionHeuristics.LTR,
                )
        val pending = coordinator.startPendingAction()
        var answered = false
        dialog =
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.calling_blocked_number_title, number))
                .setMessage(activity.getString(R.string.calling_blocked_number_message, number))
                .setPositiveButton(R.string.calling_blocked_number_call) { _, _ ->
                    answered = true
                    pending.finish()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    answered = true
                    coordinator.abortCall()
                    pending.finish()
                }
                .setOnCancelListener {
                    if (!answered) {
                        coordinator.abortCall()
                        pending.finish()
                    }
                }
                .show()
    }

    override fun onDiscard() {
        dialog?.dismiss()
        dialog = null
    }

    private fun isBlocked(context: Context, builder: CallIntentBuilder): Boolean {
        val number = numberOf(builder)
        if (number.isEmpty()) return false
        return try {
            BlockedNumberContract.canCurrentUserBlockNumbers(context) &&
                BlockedNumberContract.isBlocked(context, number)
        } catch (e: RuntimeException) {
            // Only the default phone app may ask; without that the call simply goes ahead.
            LogUtil.w("BlockedNumberAction.isBlocked", "could not check the block list: $e")
            false
        }
    }

    private fun numberOf(builder: CallIntentBuilder): String =
        if (builder.uri.scheme == PhoneAccount.SCHEME_TEL) builder.uri.schemeSpecificPart.orEmpty()
        else ""
}
