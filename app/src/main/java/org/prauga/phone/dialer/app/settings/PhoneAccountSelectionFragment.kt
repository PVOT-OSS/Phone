/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.ui.TestTags

/**
 * Lists the SIM accounts and opens [PARAM_TARGET_FRAGMENT] for the one the user picks, with its
 * [PhoneAccountHandle] added to [PARAM_ARGUMENTS] under [PARAM_PHONE_ACCOUNT_HANDLE_KEY].
 */
class PhoneAccountSelectionFragment : SettingsComposeFragment() {

    /** A SIM account as the list shows it. */
    private data class SimAccount(val handle: PhoneAccountHandle, val label: String, val description: String?)

    private var accounts by mutableStateOf(emptyList<SimAccount>())

    @Composable
    override fun Content() {
        SettingsList {
            for (account in accounts) {
                item(key = account.handle.id) {
                    PvotPreferenceRow(
                        title = account.label,
                        summary = account.description,
                        onClick = { open(account.handle) },
                        modifier = Modifier.testTag(TestTags.settingsRow("account_${account.handle.id}")),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // SIMs can be added or removed while this screen is in the background.
        accounts = loadSimAccounts()
    }

    @SuppressLint("MissingPermission")
    private fun loadSimAccounts(): List<SimAccount> {
        val telecomManager = requireContext().getSystemService(TelecomManager::class.java)
        return telecomManager.callCapablePhoneAccounts.mapNotNull { handle ->
            telecomManager.getPhoneAccount(handle)
                ?.takeIf { it.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) }
                ?.let { SimAccount(handle, it.label.toString(), it.shortDescription?.toString()) }
        }
    }

    private fun open(handle: PhoneAccountHandle) {
        val args = requireArguments()
        val target = args.getString(PARAM_TARGET_FRAGMENT) ?: return
        val targetArguments = Bundle(args.getBundle(PARAM_ARGUMENTS) ?: Bundle()).apply {
            putParcelable(args.getString(PARAM_PHONE_ACCOUNT_HANDLE_KEY), handle)
        }
        val titleRes = args.getInt(PARAM_TARGET_TITLE_RES, 0)
        (requireActivity() as DialerSettingsActivity)
            .openScreen(target, targetArguments, if (titleRes != 0) getString(titleRes) else null)
    }

    companion object {
        /** The settings screen to open once an account is picked. */
        const val PARAM_TARGET_FRAGMENT = "target_fragment"

        /** The arguments for [PARAM_TARGET_FRAGMENT]. */
        const val PARAM_ARGUMENTS = "arguments"

        /** The key under which the picked [PhoneAccountHandle] is added to [PARAM_ARGUMENTS]. */
        const val PARAM_PHONE_ACCOUNT_HANDLE_KEY = "phone_account_handle_key"

        /** The title of [PARAM_TARGET_FRAGMENT], as a string resource ID. */
        const val PARAM_TARGET_TITLE_RES = "target_title_res"
    }
}
