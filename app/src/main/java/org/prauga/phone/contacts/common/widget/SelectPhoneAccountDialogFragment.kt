/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.widget

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.DialogFragment
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotListRowDefaults
import org.prauga.phone.contacts.common.compat.PhoneAccountCompat
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.location.GeoUtil
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.protos.ProtoParsers
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.ui.PvotAlertDialog
import org.prauga.phone.dialer.ui.PvotDialogCheckboxRow

/**
 * Dialog that allows the user to select a phone accounts for a given action. Optionally provides
 * the choice to set the phone account as default.
 */
class SelectPhoneAccountDialogFragment : DialogFragment() {

    private var options: SelectPhoneAccountDialogOptions = SelectPhoneAccountDialogOptions.getDefaultInstance()
    private var listener: SelectPhoneAccountListener? = null

    private val isDefaultChecked = mutableStateOf(false)
    private var isSelected = false

    fun setListener(listener: SelectPhoneAccountListener?) {
        this.listener = listener
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_IS_DEFAULT_CHECKED, isDefaultChecked.value)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        options = ProtoParsers.getTrusted(
            requireArguments(),
            ARG_OPTIONS,
            SelectPhoneAccountDialogOptions.getDefaultInstance(),
        )
        if (savedInstanceState != null) {
            isDefaultChecked.value = savedInstanceState.getBoolean(ARG_IS_DEFAULT_CHECKED)
        }
        isSelected = false

        val context = requireActivity()
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        val rows = options.entriesList.map { entry ->
            val handle = SelectPhoneAccountDialogOptionsUtil.getPhoneAccountHandle(entry)
            val account = TelecomUtil.getPhoneAccount(context, handle)
            val address = account?.address?.schemeSpecificPart
            AccountRow(
                label = account?.label?.toString().orEmpty(),
                number = if (account == null || TextUtils.isEmpty(address)) {
                    null
                } else {
                    PhoneNumberHelper.formatNumberForDisplay(context, address, getCountryIso(context, handle))?.toString()
                },
                hint = entry.hint.takeUnless { TextUtils.isEmpty(it) },
                icon = PhoneAccountCompat.createIconDrawable(account, context),
                enabled = entry.enabled,
            )
        }
        val setDefaultLabel = if (options.getCanSetDefault()) {
            getString(if (options.hasSetDefaultLabel()) options.setDefaultLabel else R.string.set_default_account)
        } else {
            null
        }
        val title = getString(if (options.hasTitle()) options.title else R.string.select_account_dialog_title)

        return createDialog(context, title, rows, setDefaultLabel, isDefaultChecked) { position ->
            isSelected = true
            val selectedAccountHandle =
                SelectPhoneAccountDialogOptionsUtil.getPhoneAccountHandle(options.entriesList[position])
            val result = Bundle()
            result.putParcelable(SelectPhoneAccountListener.EXTRA_SELECTED_ACCOUNT_HANDLE, selectedAccountHandle)
            result.putBoolean(SelectPhoneAccountListener.EXTRA_SET_DEFAULT, isDefaultChecked.value)
            result.putString(SelectPhoneAccountListener.EXTRA_CALL_ID, getCallId())
            listener?.deliver(SelectPhoneAccountListener.RESULT_SELECTED, result)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        if (!isSelected && listener != null) {
            val result = Bundle()
            result.putString(SelectPhoneAccountListener.EXTRA_CALL_ID, getCallId())
            listener!!.deliver(SelectPhoneAccountListener.RESULT_DISMISSED, result)
        }
        super.onCancel(dialog)
    }

    private fun getCallId(): String? = options.callId

    open class SelectPhoneAccountListener protected constructor() :
        ResultReceiver(Handler(Looper.getMainLooper())) {

        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            if (resultCode == RESULT_SELECTED) {
                onPhoneAccountSelected(
                    resultData!!.getParcelable(EXTRA_SELECTED_ACCOUNT_HANDLE, PhoneAccountHandle::class.java),
                    resultData.getBoolean(EXTRA_SET_DEFAULT),
                    resultData.getString(EXTRA_CALL_ID),
                )
            } else if (resultCode == RESULT_DISMISSED) {
                onDialogDismissed(resultData!!.getString(EXTRA_CALL_ID))
            }
        }

        /** Hands a result to [onReceiveResult] directly, as the dialog always has. */
        internal fun deliver(resultCode: Int, resultData: Bundle) = onReceiveResult(resultCode, resultData)

        open fun onPhoneAccountSelected(
            selectedAccountHandle: PhoneAccountHandle?,
            setDefault: Boolean,
            callId: String?,
        ) {}

        open fun onDialogDismissed(callId: String?) {}

        companion object {
            internal const val RESULT_SELECTED = 1
            internal const val RESULT_DISMISSED = 2

            internal const val EXTRA_SELECTED_ACCOUNT_HANDLE = "extra_selected_account_handle"
            internal const val EXTRA_SET_DEFAULT = "extra_set_default"
            internal const val EXTRA_CALL_ID = "extra_call_id"
        }
    }

    /** What one row of the dialog shows for an account. */
    @VisibleForTesting
    internal class AccountRow(
        val label: String,
        val number: String?,
        val hint: String?,
        val icon: Drawable?,
        val enabled: Boolean,
    )

    companion object {
        private const val ARG_OPTIONS = "options"

        private const val ARG_IS_DEFAULT_CHECKED = "is_default_checked"

        /** Create new fragment instance. */
        @JvmStatic
        fun newInstance(
            options: SelectPhoneAccountDialogOptions,
            listener: SelectPhoneAccountListener?,
        ): SelectPhoneAccountDialogFragment {
            val fragment = SelectPhoneAccountDialogFragment()
            fragment.setListener(listener)
            val arguments = Bundle()
            ProtoParsers.put(arguments, ARG_OPTIONS, options)
            fragment.arguments = arguments
            return fragment
        }

        /**
         * The dialog: a row per account (its icon, label, number and hint; disabled accounts can't
         * be picked) and, when [setDefaultLabel] is given, a checkbox to keep the choice. Picking an
         * account reports its position and closes the dialog; back or a tap outside cancels it.
         */
        @VisibleForTesting
        internal fun createDialog(
            context: Context,
            title: String,
            rows: List<AccountRow>,
            setDefaultLabel: String?,
            isDefaultChecked: MutableState<Boolean>,
            onSelected: (position: Int) -> Unit,
        ): Dialog {
            lateinit var dialog: Dialog
            dialog = PvotAlertDialog(context, title, null, emptyList()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        rows.forEachIndexed { position, row ->
                            PvotListRow(
                                title = row.label,
                                supportingText = listOfNotNull(row.number, row.hint).joinToString("\n").ifEmpty { null },
                                leading = row.icon?.let { icon -> { AccountIcon(icon, row.enabled) } },
                                enabled = row.enabled,
                                onClick = {
                                    onSelected(position)
                                    dialog.dismiss()
                                },
                            )
                        }
                    }
                    if (setDefaultLabel != null) {
                        PvotDialogCheckboxRow(setDefaultLabel, isDefaultChecked.value) { isDefaultChecked.value = it }
                    }
                }
            }
            return dialog
        }

        private fun getCountryIso(context: Context, phoneAccountHandle: PhoneAccountHandle): String {
            val info = TelecomUtil.getSubscriptionInfo(context, phoneAccountHandle)
            if (!info.isPresent) {
                return GeoUtil.getCurrentCountryIso(context)
            }
            return info.get().countryIso.uppercase()
        }
    }
}

/** An account's icon at the row's leading size, faded when the account can't be picked. */
@Composable
private fun AccountIcon(icon: Drawable, enabled: Boolean) {
    val size = with(LocalDensity.current) { PvotListRowDefaults.LeadingSize.roundToPx() }
    val bitmap = remember(icon, size) { icon.toBitmap(size, size).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(PvotListRowDefaults.LeadingSize),
        alpha = if (enabled) 1f else DisabledIconAlpha,
    )
}

/** The 38% the old list faded a disabled account's icon to. */
private const val DisabledIconAlpha = 0.38f
