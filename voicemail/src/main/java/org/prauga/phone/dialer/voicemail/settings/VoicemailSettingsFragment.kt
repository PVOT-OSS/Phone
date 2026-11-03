/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.voicemail.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.os.BundleCompat
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsGroup
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.app.settings.ui.settingsIcon
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.notification.NotificationChannelManager
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.voicemail.R
import org.prauga.phone.voicemail.VoicemailClient
import org.prauga.phone.voicemail.VoicemailComponent

/** Voicemail settings for one SIM, given as [VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE]. */
class VoicemailSettingsFragment :
    SettingsComposeFragment(), VoicemailClient.ActivationStateListener {

    private lateinit var phoneAccountHandle: PhoneAccountHandle
    private lateinit var voicemailClient: VoicemailClient
    private var state by mutableStateOf<VoicemailSettingsState?>(null)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        phoneAccountHandle =
            checkNotNull(
                BundleCompat.getParcelable(
                    requireArguments(),
                    VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE,
                    PhoneAccountHandle::class.java,
                )
            )
        voicemailClient = VoicemailComponent.get(requireContext()).voicemailClient
    }

    @Composable
    override fun Content() {
        val state = state ?: return
        var confirmDisable by rememberSaveable { mutableStateOf(false) }
        SettingsList {
            item(key = "voicemail") {
                SettingsGroup {
                    key("notifications") {
                        PvotPreferenceRow(
                            title =
                                stringResource(R.string.voicemail_notifications_preference_title),
                            onClick = { start(notificationSettingsIntent()) },
                            modifier =
                                Modifier.testTag(TestTags.settingsRow("voicemail_notifications")),
                            icon = settingsIcon(R.drawable.ic_bell),
                            iconStyle = PvotRowIconStyle.Plain,
                        )
                    }
                    if (state.carrierSupport) {
                        key("visual_voicemail") {
                            PvotSwitchRow(
                                title =
                                    stringResource(
                                        R.string.voicemail_visual_voicemail_switch_title
                                    ),
                                summary =
                                    if (state.activating)
                                        stringResource(R.string.voicemail_activating_summary_info)
                                    else null,
                                checked = state.enabled,
                                onCheckedChange = { on ->
                                    if (on) setEnabled(true) else confirmDisable = true
                                },
                                modifier =
                                    Modifier.testTag(TestTags.settingsRow("visual_voicemail")),
                                icon = settingsIcon(R.drawable.ic_call_voicemail),
                                iconStyle = PvotRowIconStyle.Plain,
                            )
                        }
                        if (state.archiveAvailable) {
                            key("auto_archive") {
                                PvotSwitchRow(
                                    title =
                                        stringResource(
                                            R.string
                                                .voicemail_visual_voicemail_auto_archive_switch_title
                                        ),
                                    checked = state.archiveEnabled,
                                    onCheckedChange = {
                                        voicemailClient.setVoicemailArchiveEnabled(
                                            requireContext(),
                                            phoneAccountHandle,
                                            it,
                                        )
                                        refresh()
                                    },
                                    modifier =
                                        Modifier.testTag(TestTags.settingsRow("auto_archive")),
                                    icon = settingsIcon(R.drawable.ic_history),
                                    iconStyle = PvotRowIconStyle.Plain,
                                )
                            }
                        }
                        key("change_pin") {
                            PvotPreferenceRow(
                                title =
                                    stringResource(
                                        if (state.pinScrambled)
                                            R.string.voicemail_set_pin_preference_title
                                        else R.string.voicemail_change_pin_preference_title
                                    ),
                                summary =
                                    when {
                                        !state.enabled ->
                                            stringResource(
                                                R.string
                                                    .voicemail_change_pin_preference_summary_disable
                                            )
                                        !state.activated ->
                                            stringResource(
                                                R.string
                                                    .voicemail_change_pin_preference_summary_not_activated
                                            )
                                        else -> null
                                    },
                                enabled = state.enabled && state.activated,
                                onClick = {
                                    start(
                                        Intent(
                                                requireContext(),
                                                VoicemailChangePinActivity::class.java,
                                            )
                                            .putExtra(
                                                VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE,
                                                phoneAccountHandle,
                                            )
                                    )
                                },
                                modifier = Modifier.testTag(TestTags.settingsRow("change_pin")),
                                icon = settingsIcon(R.drawable.ic_dialpad),
                                iconStyle = PvotRowIconStyle.Plain,
                            )
                        }
                    }
                    key("advanced") {
                        PvotPreferenceRow(
                            title = stringResource(R.string.voicemail_advanced_settings_title),
                            onClick = { start(advancedSettingsIntent()) },
                            modifier = Modifier.testTag(TestTags.settingsRow("voicemail_advanced")),
                            icon = settingsIcon(R.drawable.ic_adjustments),
                            iconStyle = PvotRowIconStyle.Plain,
                        )
                    }
                }
            }
        }

        PvotDialog(
            visible = confirmDisable,
            onDismissRequest = { confirmDisable = false },
            title = stringResource(R.string.confirm_disable_voicemail_dialog_title),
            message = stringResource(R.string.confirm_disable_voicemail_dialog_message),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(R.string.confirm_disable_voicemail_accept_dialog_label),
                style = PvotDialogActionStyle.Destructive,
                onClick = {
                    LogUtil.i(TAG, "disabling visual voicemail, confirmed")
                    confirmDisable = false
                    setEnabled(false)
                },
            )
            PvotDialogAction(
                label = stringResource(android.R.string.cancel),
                onClick = { confirmDisable = false },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        voicemailClient.addActivationStateListener(this)
        refresh()
    }

    override fun onPause() {
        voicemailClient.removeActivationStateListener(this)
        super.onPause()
    }

    override fun onActivationStateChanged(
        phoneAccountHandle: PhoneAccountHandle,
        isActivated: Boolean,
    ) {
        if (this.phoneAccountHandle == phoneAccountHandle) refresh()
    }

    private fun setEnabled(enabled: Boolean) {
        voicemailClient.setVoicemailEnabled(requireContext(), phoneAccountHandle, enabled)
        refresh()
    }

    private fun refresh() {
        state = VoicemailSettingsState.read(requireContext(), voicemailClient, phoneAccountHandle)
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(TAG, "nothing handles $intent", e)
        }
    }

    private fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(
                Settings.EXTRA_CHANNEL_ID,
                NotificationChannelManager.getVoicemailChannelId(
                    requireContext(),
                    phoneAccountHandle,
                ),
            )
            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)

    /** The carrier's voicemail settings in the system Phone app (number, and so on). */
    private fun advancedSettingsIntent(): Intent {
        val intent =
            Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)
                .putExtra(TelephonyManager.EXTRA_HIDE_PUBLIC_SETTINGS, true)
                .putExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
        // The Phone app may not read EXTRA_PHONE_ACCOUNT_HANDLE, so also pass the subscription.
        val subscription = TelecomUtil.getSubscriptionInfo(requireContext(), phoneAccountHandle)
        if (subscription.isPresent) {
            intent.putExtra(SUB_ID_EXTRA, subscription.get().subscriptionId)
            TelecomUtil.getPhoneAccount(requireContext(), phoneAccountHandle)?.let {
                intent.putExtra(SUB_LABEL_EXTRA, it.label)
            }
        }
        return intent
    }

    private companion object {
        const val TAG = "VmSettingsActivity"
        // Extras copied from com.android.phone.settings.VoicemailSettingsActivity.
        const val SUB_ID_EXTRA = "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId"
        const val SUB_LABEL_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel"
    }
}

/** What the voicemail settings show for one SIM, read from the [VoicemailClient]. */
internal data class VoicemailSettingsState(
    val carrierSupport: Boolean,
    val enabled: Boolean,
    val activated: Boolean,
    val archiveAvailable: Boolean,
    val archiveEnabled: Boolean,
    val pinScrambled: Boolean,
) {
    /** Visual voicemail is on but the carrier hasn't finished setting it up. */
    val activating: Boolean
        get() = enabled && !activated

    companion object {
        fun read(
            context: Context,
            client: VoicemailClient,
            handle: PhoneAccountHandle,
        ): VoicemailSettingsState {
            val carrierSupport = client.hasCarrierSupport(context, handle)
            val enabled = carrierSupport && client.isVoicemailEnabled(context, handle)
            val activated = enabled && client.isActivated(context, handle)
            return VoicemailSettingsState(
                carrierSupport = carrierSupport,
                enabled = enabled,
                activated = activated,
                // Auto-archive only appears once visual voicemail is on and set up.
                archiveAvailable = activated && client.isVoicemailArchiveAvailable(context),
                archiveEnabled = activated && client.isVoicemailArchiveEnabled(context, handle),
                // Same as VoicemailChangePinActivity.isPinScrambled, through the given client.
                pinScrambled =
                    carrierSupport && client.createPinChanger(context, handle).scrambledPin != null,
            )
        }
    }
}
