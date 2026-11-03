/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.CallUtil

/** Asks which SIM or calling account to call a number with, then places the call. */
class AccountSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val handles =
            IntentCompat.getParcelableArrayListExtra(
                intent,
                EXTRA_ACCOUNT_HANDLES,
                PhoneAccountHandle::class.java,
            )
        if (number.isNullOrEmpty() || handles.isNullOrEmpty()) {
            finish()
            return
        }
        val initiationType = CallInitiationType.Type.entries[intent.getIntExtra(EXTRA_TYPE, 0)]
        val telecom = getSystemService(TelecomManager::class.java)
        val accounts =
            handles.mapNotNull { handle ->
                TelecomUtil.getPhoneAccount(this, handle)?.let { CallingAccount.of(this, it) }
            }

        setPvotContent {
            // Not filled: the window is see-through, and the sheet draws its own scrim over the app
            // below.
            PhoneScreenTheme(fillBackground = false) {
                AccountSheet(
                    accounts,
                    onSelect = { account ->
                        startActivity(
                            CallIntentBuilder(number, initiationType)
                                .setPhoneAccountHandle(account.handle)
                                .build()
                        )
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_ACCOUNT_HANDLES = "accountHandles"
        private const val EXTRA_TYPE = "type"

        /**
         * An intent to choose the account to call [number] with, or null with one account or none.
         */
        @JvmStatic
        fun createIntent(
            context: Context,
            number: String?,
            initiationType: CallInitiationType.Type,
        ): Intent? {
            if (number.isNullOrEmpty()) return null
            val accounts = CallUtil.getCallCapablePhoneAccounts(context, PhoneAccount.SCHEME_TEL)
            if (accounts == null || accounts.size <= 1) return null
            return Intent(context, AccountSelectionActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putParcelableArrayListExtra(
                    EXTRA_ACCOUNT_HANDLES,
                    ArrayList(accounts.map { it.accountHandle }),
                )
                .putExtra(EXTRA_TYPE, initiationType.ordinal)
        }
    }
}

/**
 * A calling account as the chooser shows it: its label, its number and the system's icon for it.
 */
internal data class CallingAccount(
    val handle: PhoneAccountHandle,
    val label: String,
    val number: String?,
    val icon: android.graphics.Bitmap?,
) {
    companion object {
        fun of(context: Context, account: PhoneAccount) =
            CallingAccount(
                handle = account.accountHandle,
                label = account.label.toString(),
                number =
                    (account.subscriptionAddress ?: account.address)?.schemeSpecificPart?.ifEmpty {
                        null
                    },
                icon = account.icon?.loadDrawable(context)?.toBitmap(),
            )
    }
}

/** Size of an account's own icon at the start of its row. */
private val AccountIconSize = 24.dp

/** The sheet listing [accounts]; picking one calls [onSelect], dismissing calls [onDismiss]. */
@Composable
internal fun AccountSheet(
    accounts: List<CallingAccount>,
    onSelect: (CallingAccount) -> Unit,
    onDismiss: () -> Unit,
) {
    PvotBottomSheet(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.call_via_dialog_title),
    ) {
        for (account in accounts) {
            PvotSheetItem(
                title = account.label,
                subtitle = account.number,
                onClick = { onSelect(account) },
                modifier = Modifier.testTag(TestTags.callingAccount(account.handle.id)),
                // The account's own icon, usually its SIM color, as the system shows it.
                icon =
                    account.icon?.let { bitmap ->
                        {
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = null,
                                Modifier.size(AccountIconSize),
                            )
                        }
                    },
            )
        }
    }
}
