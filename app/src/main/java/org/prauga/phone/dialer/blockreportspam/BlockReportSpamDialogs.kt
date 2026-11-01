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

package org.prauga.phone.dialer.blockreportspam

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.DialogFragment
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotAlertDialog.Action
import org.prauga.phone.dialer.ui.PvotDialogCheckboxRow
import org.prauga.phone.dialer.ui.PvotDialogText

/** Creates dialog fragments to block a number and/or report it as spam/not spam. */
object BlockReportSpamDialogs {

    const val BLOCK_REPORT_SPAM_DIALOG_TAG = "BlockReportSpamDialog"
    const val BLOCK_DIALOG_TAG = "BlockDialog"
    const val UNBLOCK_DIALOG_TAG = "UnblockDialog"
    const val NOT_SPAM_DIALOG_TAG = "NotSpamDialog"

    /**
     * A Pvot dialog with a positive button and a Cancel button that dismisses the fragment. The
     * positive button dismisses the fragment and then runs [onPositive].
     */
    private fun DialogFragment.createDialog(
        title: String?,
        message: String?,
        positiveLabel: Int,
        onPositive: () -> Unit,
        content: (@Composable () -> Unit)? = null,
    ): Dialog =
        PvotAlertDialog(
                requireActivity(),
                title,
                message,
                listOf(
                    Action(
                        getString(positiveLabel),
                        PvotDialogActionStyle.Primary,
                        {
                            dismiss()
                            onPositive()
                        },
                    ),
                    Action(getString(android.R.string.cancel), onClick = { dismiss() }),
                ),
                content,
            )
            .apply {
                setCancelable(true)
                setCanceledOnTouchOutside(true)
            }

    /**
     * Positive listener for the "Block/Report spam" dialog
     * [DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam].
     */
    fun interface OnSpamDialogClickListener {

        /**
         * Called when the user clicks on the positive button of the "Block/Report spam" dialog.
         *
         * @param isSpamChecked Whether the spam checkbox is checked.
         */
        fun onClick(isSpamChecked: Boolean)
    }

    /** Positive listener for dialogs other than the "Block/Report spam" dialog. */
    fun interface OnConfirmListener {

        /** Called when the user clicks on the positive button of the dialog. */
        fun onClick()
    }

    /** Contains common attributes shared among all dialog fragments. */
    abstract class CommonDialogsFragment : DialogFragment() {

        /** The number to display in the dialog title. */
        internal var displayNumber: String? = null

        /** Listener for the positive button. */
        internal var positiveListener: OnConfirmListener? = null

        /** Listener for when the dialog is dismissed. */
        internal var dismissListener: DialogInterface.OnDismissListener? = null

        override fun onDismiss(dialog: DialogInterface) {
            dismissListener?.onDismiss(dialog)
            super.onDismiss(dialog)
        }

        override fun onPause() {
            // The dialog is dismissed onPause, i.e. rotation.
            dismiss()
            dismissListener = null
            positiveListener = null
            displayNumber = null
            super.onPause()
        }

        /** The positive listener as it is now, called later even if this fragment pauses first. */
        protected fun confirm(): () -> Unit {
            val listener = positiveListener
            return { listener!!.onClick() }
        }
    }

    /**
     * Dialog for blocking a number and optionally reporting it as spam.
     *
     * This dialog is for a number that is neither blocked nor marked as spam. It has a checkbox
     * that allows the user to report a number as spam when they block it.
     */
    class DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam : CommonDialogsFragment() {

        /** Called when dialog positive button is pressed. */
        private var onSpamDialogClickListener: OnSpamDialogClickListener? = null

        /** Whether the mark as spam checkbox is checked before displaying the dialog. */
        private var spamChecked = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreateDialog(savedInstanceState)
            // Listen for changes on the checkbox and update if orientation changes
            var checked by mutableStateOf(spamChecked)
            val listener = onSpamDialogClickListener
            return createDialog(
                title = getString(R.string.block_report_number_alert_title, displayNumber),
                message = null,
                positiveLabel = R.string.block_number_ok,
                onPositive = { listener!!.onClick(checked) },
            ) {
                PvotDialogText(getString(R.string.block_number_confirmation_message_new_filtering))
                PvotDialogCheckboxRow(getString(R.string.checkbox_report_as_spam_action), checked) {
                    checked = it
                    spamChecked = it
                }
            }
        }

        companion object {
            @JvmStatic
            fun newInstance(
                displayNumber: String?,
                spamChecked: Boolean,
                onSpamDialogClickListener: OnSpamDialogClickListener?,
                dismissListener: DialogInterface.OnDismissListener?,
            ): DialogFragment =
                DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam().apply {
                    this.spamChecked = spamChecked
                    this.displayNumber = displayNumber
                    this.onSpamDialogClickListener = onSpamDialogClickListener
                    this.dismissListener = dismissListener
                }
        }
    }

    /**
     * Dialog for blocking a number and reporting it as spam.
     *
     * This dialog is for the migration of blocked numbers. Its positive action should block a
     * number, and also marks it as spam if the spam feature is enabled.
     */
    class DialogFragmentForBlockingNumberAndReportingAsSpam : CommonDialogsFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreateDialog(savedInstanceState)
            return createDialog(
                title = getString(R.string.block_number_confirmation_title, displayNumber),
                message = getString(R.string.block_report_number_alert_details),
                positiveLabel = R.string.block_number_ok,
                onPositive = confirm(),
            )
        }

        companion object {
            @JvmStatic
            fun newInstance(
                displayNumber: String?,
                positiveListener: OnConfirmListener?,
                dismissListener: DialogInterface.OnDismissListener?,
            ): DialogFragment =
                DialogFragmentForBlockingNumberAndReportingAsSpam().apply {
                    this.displayNumber = displayNumber
                    this.positiveListener = positiveListener
                    this.dismissListener = dismissListener
                }
        }
    }

    /**
     * Dialog for blocking a number.
     *
     * This dialog is for a spam number that hasn't been blocked. For example, if the user receives
     * a spam call, this dialog will be shown if they would like to block the number.
     */
    class DialogFragmentForBlockingNumber : CommonDialogsFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreateDialog(savedInstanceState)
            return createDialog(
                title = getString(R.string.block_number_confirmation_title, displayNumber),
                message = getString(R.string.block_report_number_alert_details),
                positiveLabel = R.string.block_number_ok,
                onPositive = confirm(),
            )
        }

        companion object {
            // Shows the same dialog as DialogFragmentForBlockingNumberAndReportingAsSpam, as
            // before.
            @JvmStatic
            fun newInstance(
                displayNumber: String?,
                positiveListener: OnConfirmListener?,
                dismissListener: DialogInterface.OnDismissListener?,
            ): DialogFragment =
                DialogFragmentForBlockingNumberAndReportingAsSpam.newInstance(
                    displayNumber,
                    positiveListener,
                    dismissListener,
                )
        }
    }

    /**
     * Dialog for unblocking a number and marking it as not spam.
     *
     * This dialog is used in the old call log, where unblocking a number will also mark it as not
     * spam.
     */
    class DialogFragmentForUnblockingNumberAndReportingAsNotSpam : CommonDialogsFragment() {

        /** Whether or not the number is spam. */
        private var isSpam = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreateDialog(savedInstanceState)
            val title = getString(R.string.unblock_report_number_alert_title, displayNumber)
            return if (isSpam) {
                createDialog(
                    title,
                    getString(R.string.unblock_number_alert_details),
                    R.string.unblock_number_ok,
                    confirm(),
                )
            } else {
                createDialog(null, title, R.string.unblock_number_ok, confirm())
            }
        }

        companion object {
            @JvmStatic
            fun newInstance(
                displayNumber: String?,
                isSpam: Boolean,
                positiveListener: OnConfirmListener?,
                dismissListener: DialogInterface.OnDismissListener?,
            ): DialogFragment =
                DialogFragmentForUnblockingNumberAndReportingAsNotSpam().apply {
                    this.displayNumber = displayNumber
                    this.isSpam = isSpam
                    this.positiveListener = positiveListener
                    this.dismissListener = dismissListener
                }
        }
    }

    /**
     * Dialog for unblocking a number.
     *
     * This dialog is used in the new call log, where unblocking a number will *not* mark it as not
     * spam.
     */
    class DialogFragmentForUnblockingNumber : CommonDialogsFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreateDialog(savedInstanceState)
            return createDialog(
                title = null,
                message = getString(R.string.unblock_report_number_alert_title, displayNumber),
                positiveLabel = R.string.unblock_number_ok,
                onPositive = confirm(),
            )
        }

        companion object {
            // Shows the not-spam unblock dialog for a number that is not spam, as before.
            @JvmStatic
            fun newInstance(
                displayNumber: String?,
                positiveListener: OnConfirmListener?,
                dismissListener: DialogInterface.OnDismissListener?,
            ): DialogFragment =
                DialogFragmentForUnblockingNumberAndReportingAsNotSpam.newInstance(
                    displayNumber,
                    false,
                    positiveListener,
                    dismissListener,
                )
        }
    }

    /** Dialog for reporting a number as not spam. */
    class DialogFragmentForReportingNotSpam : CommonDialogsFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            super.onCreateDialog(savedInstanceState)
            return createDialog(
                title = getString(R.string.report_not_spam_alert_title),
                message = getString(R.string.report_not_spam_alert_details, displayNumber),
                positiveLabel = R.string.report_not_spam_alert_button,
                onPositive = confirm(),
            )
        }

        companion object {
            @JvmStatic
            fun newInstance(
                displayNumber: String?,
                positiveListener: OnConfirmListener?,
                dismissListener: DialogInterface.OnDismissListener?,
            ): DialogFragment =
                DialogFragmentForReportingNotSpam().apply {
                    this.displayNumber = displayNumber
                    this.positiveListener = positiveListener
                    this.dismissListener = dismissListener
                }
        }
    }
}
