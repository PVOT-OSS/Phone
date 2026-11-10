/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.Dialog
import android.os.Bundle
import android.provider.CallLog.Calls
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotAlertDialog.Action

/**
 * Asks before deleting every call from the history, then deletes them and the missed-call alerts.
 */
class ClearCallHistoryDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        PvotAlertDialog(
            requireContext(),
            getString(R.string.clearCallLogConfirmation_title),
            getString(R.string.clearCallLogConfirmation),
            listOf(
                Action(
                    getString(android.R.string.ok),
                    PvotDialogActionStyle.Destructive,
                    ::clearHistory,
                ),
                Action(getString(android.R.string.cancel), cancels = true),
            ),
        )

    private fun clearHistory() {
        val activity = requireActivity()
        val appContext = activity.applicationContext
        CallLogNotificationsService.cancelAllMissedCalls(appContext)
        // The activity outlives this dialog, so the delete finishes after it closes.
        activity.lifecycleScope.launch(Dispatchers.IO) {
            appContext.contentResolver.delete(Calls.CONTENT_URI, null, null)
            appContext.contentResolver.notifyChange(Calls.CONTENT_URI, null)
        }
    }

    companion object {
        private const val TAG = "clearCallHistory"

        @JvmStatic
        fun show(fragmentManager: FragmentManager) {
            ClearCallHistoryDialog().show(fragmentManager, TAG)
        }
    }
}
