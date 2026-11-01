/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.provider.BlockedNumberContract
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.BaseActivity
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.compat.telephony.TelephonyManagerCompat
import org.prauga.phone.dialer.screening.ScreeningSettingsFragment
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.PermissionsUtil
import org.prauga.phone.dialer.voicemail.settings.VoicemailSettingsFragment
import org.prauga.phone.voicemail.VoicemailClient

/**
 * Activity for dialer settings. It draws the top bar and hosts one settings screen at a time,
 * either a Compose [SettingsComposeFragment] or a [PreferenceFragmentCompat] not yet moved over.
 *
 * A `header:<fragment class>` data URI opens that screen directly.
 */
class DialerSettingsActivity : BaseActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private var screenTitle by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtil.enterBlock("DialerSettingsActivity.onCreate")
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        screenTitle = title.toString()

        findViewById<ComposeView>(R.id.settings_top_bar).setContent {
            PvotAppTheme {
                PvotTopBar(
                    title = screenTitle,
                    modifier = Modifier.testTag(TestTags.SETTINGS_TOP_BAR),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
        // Preference screens don't handle insets; Compose screens pad themselves (SettingsList).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_frame)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isCompose = supportFragmentManager.findFragmentById(R.id.content_frame) is SettingsComposeFragment
            view.updatePadding(left = bars.left, right = bars.right, bottom = if (isCompose) 0 else bars.bottom)
            insets
        }

        // If savedInstanceState is non-null, the fragment manager has already restored the screen.
        if (savedInstanceState == null) {
            val requested = intent.data?.schemeSpecificPart
            val fragment = requested?.let { instantiate(it) } ?: PrefsFragment()
            supportFragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.dialer_settings_label)
            }
            findViewById<android.view.View>(R.id.content_frame).requestApplyInsets()
        }
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        screenTitle = title?.toString().orEmpty()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        openScreen(pref.fragment ?: return false, pref.extras, pref.title)
        return true
    }

    /** Shows the settings screen [fragmentClass] with [arguments], titled [title], on the back stack. */
    fun openScreen(fragmentClass: String, arguments: Bundle?, title: CharSequence?) {
        val fragment = instantiate(fragmentClass) ?: return
        fragment.arguments = arguments
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, "")
            .addToBackStack(null)
            .commit()
        setTitle(title)
    }

    private fun instantiate(fragmentClass: String): Fragment? = try {
        supportFragmentManager.fragmentFactory.instantiate(classLoader, fragmentClass)
    } catch (e: Exception) {
        LogUtil.e("DialerSettingsActivity.instantiate", "no settings screen $fragmentClass", e)
        null
    }

    /** The top-level settings list. */
    class PrefsFragment : SettingsComposeFragment() {

        @Composable
        override fun Content() {
            val context = LocalContext.current
            val entries = remember { settingsEntries(context) }
            SettingsList {
                for (entry in entries) {
                    item(key = entry.key) {
                        val title = stringResource(entry.titleRes)
                        PvotPreferenceRow(
                            title = title,
                            onClick = { open(entry, title) },
                            modifier = Modifier.testTag(TestTags.settingsRow(entry.key)),
                            showChevron = entry.target is Target.Screen,
                        )
                    }
                }
            }
        }

        private fun open(entry: Entry, title: String) {
            when (val target = entry.target) {
                is Target.Screen -> {
                    if (entry.key == KEY_SOUNDS && !Settings.System.canWrite(requireContext())) {
                        // Without permission to write system settings, go to the system's sound settings.
                        Toast.makeText(requireContext(), R.string.toast_cannot_write_system_settings, Toast.LENGTH_SHORT)
                            .show()
                        startSafely(Intent(Settings.ACTION_SOUND_SETTINGS))
                        return
                    }
                    (requireActivity() as DialerSettingsActivity).openScreen(target.fragmentClass, target.arguments, title)
                }
                is Target.Activity -> startSafely(target.intent)
            }
        }

        private fun startSafely(intent: Intent) {
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                LogUtil.e("DialerSettingsActivity.PrefsFragment", "nothing handles $intent", e)
            }
        }
    }

    /** A row of the top-level list. */
    internal data class Entry(val key: String, val titleRes: Int, val target: Target)

    /** Where a row leads: another settings screen here, or another app's activity. */
    internal sealed interface Target {
        data class Screen(val fragmentClass: String, val arguments: Bundle? = null) : Target
        data class Activity(val intent: Intent) : Target
    }

    internal companion object {
        const val KEY_APPEARANCE = "appearance"
        const val KEY_DISPLAY_OPTIONS = "display_options"
        const val KEY_SOUNDS = "sounds_and_vibration"
        const val KEY_QUICK_RESPONSES = "quick_responses"
        const val KEY_SCREENING = "screening"
        const val KEY_CALL_SETTINGS = "call_settings"
        const val KEY_CALLING_ACCOUNTS = "calling_accounts"
        const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
        const val KEY_VOICEMAIL = "voicemail"
        const val KEY_ACCESSIBILITY = "accessibility"

        /** The rows of the top-level list on this device, in order. */
        fun settingsEntries(context: Context): List<Entry> = buildList {
            val resources = context.resources
            // For languages such as Chinese, Japanese and Korean, contacts are sorted and shown
            // family name first by default, so display options aren't useful.
            if (resources.getBoolean(R.bool.config_display_order_user_changeable) &&
                resources.getBoolean(R.bool.config_sort_order_user_changeable)
            ) {
                add(Entry(KEY_DISPLAY_OPTIONS, R.string.display_options_title,
                    Target.Screen(DisplayOptionsSettingsFragment::class.java.name)))
            }
            add(Entry(KEY_APPEARANCE, R.string.appearance_settings_label,
                Target.Screen(AppearanceSettingsFragment::class.java.name)))
            add(Entry(KEY_SOUNDS, R.string.sounds_and_vibration_title,
                Target.Screen(SoundSettingsFragment::class.java.name)))
            add(Entry(KEY_QUICK_RESPONSES, R.string.respond_via_sms_setting_title,
                Target.Activity(Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS))))
            add(Entry(KEY_SCREENING, R.string.screening_settings_title,
                Target.Screen(ScreeningSettingsFragment::class.java.name)))

            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            // "Call settings" (full settings) is shown to the primary user with one SIM; otherwise
            // "Calling accounts".
            val isPrimaryUser = context.getSystemService(UserManager::class.java).isSystemUser
            if (isPrimaryUser && TelephonyManagerCompat.getPhoneCount(telephonyManager) <= 1) {
                add(Entry(KEY_CALL_SETTINGS, R.string.call_settings_label, Target.Activity(
                    Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))))
            } else {
                add(Entry(KEY_CALLING_ACCOUNTS, R.string.phone_account_settings_label, Target.Activity(
                    Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))))
            }
            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                add(Entry(KEY_BLOCKED_NUMBERS, R.string.manage_blocked_numbers_label,
                    Target.Activity(telecomManager.createManageBlockedNumbersIntent())))
            }
            voicemailEntry(context, isPrimaryUser)?.let(::add)
            if (isPrimaryUser && (TelephonyManagerCompat.isTtyModeSupported(telecomManager) ||
                    TelephonyManagerCompat.isHearingAidCompatibilitySupported(telephonyManager))
            ) {
                add(Entry(KEY_ACCESSIBILITY, R.string.accessibility_settings_title,
                    Target.Activity(Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS))))
            }
        }

        private fun voicemailEntry(context: Context, isPrimaryUser: Boolean): Entry? {
            if (!isPrimaryUser) {
                LogUtil.i("DialerSettingsActivity.voicemailEntry", "user not primary user")
                return null
            }
            if (!PermissionsUtil.hasReadPhoneStatePermissions(context)) {
                LogUtil.i("DialerSettingsActivity.voicemailEntry", "Missing READ_PHONE_STATE")
                return null
            }
            val soleAccount = soleSimAccount(context)
            val target = if (soleAccount == null) {
                LogUtil.i("DialerSettingsActivity.voicemailEntry", "showing multi-SIM voicemail settings")
                Target.Screen(PhoneAccountSelectionFragment::class.java.name, Bundle().apply {
                    putString(PhoneAccountSelectionFragment.PARAM_TARGET_FRAGMENT, VoicemailSettingsFragment::class.java.name)
                    putString(PhoneAccountSelectionFragment.PARAM_PHONE_ACCOUNT_HANDLE_KEY,
                        VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE)
                    putBundle(PhoneAccountSelectionFragment.PARAM_ARGUMENTS, Bundle())
                    putInt(PhoneAccountSelectionFragment.PARAM_TARGET_TITLE_RES, R.string.voicemail_settings_label)
                })
            } else {
                LogUtil.i("DialerSettingsActivity.voicemailEntry", "showing single-SIM voicemail settings")
                Target.Screen(VoicemailSettingsFragment::class.java.name, Bundle().apply {
                    putParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, soleAccount)
                })
            }
            return Entry(KEY_VOICEMAIL, R.string.voicemail_settings_label, target)
        }

        /**
         * The only SIM phone account, or null if there are none or more than one. An empty SIM slot
         * still counts as an (invalid) account, and voicemail settings stay available for it.
         */
        @SuppressLint("MissingPermission")
        private fun soleSimAccount(context: Context): PhoneAccountHandle? {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            var result: PhoneAccountHandle? = null
            for (handle in telecomManager.callCapablePhoneAccounts) {
                val account = telecomManager.getPhoneAccount(handle) ?: continue
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                    LogUtil.i("DialerSettingsActivity.soleSimAccount", "$handle is a SIM account")
                    if (result != null) return null
                    result = handle
                }
            }
            return result
        }
    }
}
