/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotRowChevron
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsChrome
import org.prauga.phone.dialer.app.settings.ui.SettingsChromeHost
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsGroup
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.app.settings.ui.SettingsScaffold
import org.prauga.phone.dialer.app.settings.ui.SettingsTheme
import org.prauga.phone.dialer.app.settings.ui.settingsIcon
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.compat.telephony.TelephonyManagerCompat
import org.prauga.phone.dialer.lookup.LookupSettingsFragment
import org.prauga.phone.dialer.screening.ScreeningSettingsFragment
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.PermissionsUtil
import org.prauga.phone.dialer.voicemail.settings.VoicemailSettingsFragment
import org.prauga.phone.voicemail.VoicemailClient

/**
 * Activity for dialer settings. It draws the glass scaffold and its top bar, and hosts one settings
 * screen at a time under the bar: a Compose [SettingsComposeFragment], or a
 * [PreferenceFragmentCompat] opened through [onPreferenceStartFragment]. Opened screens go on the
 * fragment back stack, which back pops before the activity finishes.
 *
 * It is a [FragmentActivity] rather than a plain ComponentActivity because the screens are still
 * fragments; nothing in it needs AppCompat any more, so it sits on Theme.Pvot.
 *
 * A `header:<fragment class>` data URI opens that screen directly.
 */
class DialerSettingsActivity :
    FragmentActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SettingsChromeHost {

    private var screenTitle by mutableStateOf("")

    /** Whether the screen shown draws itself in Compose; preference screens get padded instead. */
    private var showingCompose by mutableStateOf(true)

    override val settingsChrome = SettingsChrome()

    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtil.enterBlock("DialerSettingsActivity.onCreate")
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        screenTitle = title.toString()

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.dialer_settings_label)
            }
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (f.id == R.id.settings_content) showingCompose = f is SettingsComposeFragment
                }
            },
            false,
        )

        setPvotContent {
            SettingsTheme {
                SettingsScaffold(
                    title = screenTitle,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    chrome = settingsChrome,
                ) {
                    ScreenContainer()
                }
            }
        }
    }

    /** The view the settings screens go in, filling the scaffold, under the top bar. */
    @Composable
    private fun ScreenContainer() {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val padding = settingsChrome.contentPadding
        val padContainer = !showingCompose
        AndroidView(
            factory = { context ->
                FragmentContainerView(context).apply { id = R.id.settings_content }
            },
            modifier = Modifier.fillMaxSize(),
            update = { container ->
                // Compose screens scroll under the bars and pad their own lists; preference screens
                // don't handle insets, so their container clears the bars.
                if (padContainer) {
                    with(density) {
                        container.setPaddingRelative(
                            padding.calculateStartPadding(direction).roundToPx(),
                            padding.calculateTopPadding().roundToPx(),
                            padding.calculateEndPadding(direction).roundToPx(),
                            padding.calculateBottomPadding().roundToPx(),
                        )
                    }
                } else {
                    container.setPadding(0, 0, 0, 0)
                }
            },
        )
        // The container exists only once this is composed, which is after the fragment manager
        // creates the screens' views in onStart, so the first screen is put in from here.
        DisposableEffect(Unit) {
            showScreen()
            onDispose {}
        }
    }

    /**
     * Puts a screen in the container: the requested one (or the list) the first time, or the
     * restored one again after the activity is recreated. A restored screen made its view before
     * its container existed, so it is detached and attached to make its view again in the
     * container.
     */
    private fun showScreen() {
        val manager = supportFragmentManager
        val current = manager.findFragmentById(R.id.settings_content)
        when {
            current == null -> {
                val requested = intent.data?.schemeSpecificPart
                val fragment = requested?.let { instantiate(it) } ?: PrefsFragment()
                manager.beginTransaction().replace(R.id.settings_content, fragment).commitNow()
            }
            current.view?.parent == null -> {
                // Two transactions: in one, the fragment manager cancels the detach against the
                // attach.
                manager.beginTransaction().detach(current).commitNow()
                manager.beginTransaction().attach(current).commitNow()
            }
        }
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        screenTitle = title?.toString().orEmpty()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        openScreen(pref.fragment ?: return false, pref.extras, pref.title)
        return true
    }

    /**
     * Shows the settings screen [fragmentClass] with [arguments], titled [title], on the back
     * stack.
     */
    fun openScreen(fragmentClass: String, arguments: Bundle?, title: CharSequence?) {
        val fragment = instantiate(fragmentClass) ?: return
        fragment.arguments = arguments
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_content, fragment, "")
            .addToBackStack(null)
            .commit()
        setTitle(title)
    }

    private fun instantiate(fragmentClass: String): Fragment? =
        try {
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
            SettingsRootList(entries, ::open)
        }

        private fun open(entry: Entry, title: String) {
            when (val target = entry.target) {
                is Target.Screen -> {
                    if (entry.key == KEY_SOUNDS && !Settings.System.canWrite(requireContext())) {
                        // Without permission to write system settings, go to the system's sound
                        // settings.
                        Toast.makeText(
                                requireContext(),
                                R.string.toast_cannot_write_system_settings,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                        startSafely(Intent(Settings.ACTION_SOUND_SETTINGS))
                        return
                    }
                    (requireActivity() as DialerSettingsActivity).openScreen(
                        target.fragmentClass,
                        target.arguments,
                        title,
                    )
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
    internal data class Entry(
        val key: String,
        val titleRes: Int,
        val target: Target,
        @DrawableRes val icon: Int = R.drawable.ic_settings,
    )

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
        const val KEY_LOOKUP = "lookup"
        const val KEY_SCREENING = "screening"
        const val KEY_INCOMING_CALLS = "incoming_calls"
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
            if (
                resources.getBoolean(R.bool.config_display_order_user_changeable) &&
                    resources.getBoolean(R.bool.config_sort_order_user_changeable)
            ) {
                add(
                    Entry(
                        KEY_DISPLAY_OPTIONS,
                        R.string.display_options_title,
                        Target.Screen(DisplayOptionsSettingsFragment::class.java.name),
                        R.drawable.ic_list,
                    )
                )
            }
            add(
                Entry(
                    KEY_APPEARANCE,
                    R.string.appearance_settings_label,
                    Target.Screen(AppearanceSettingsFragment::class.java.name),
                    R.drawable.ic_palette,
                )
            )
            add(
                Entry(
                    KEY_SOUNDS,
                    R.string.sounds_and_vibration_title,
                    Target.Screen(SoundSettingsFragment::class.java.name),
                    R.drawable.ic_volume,
                )
            )
            add(
                Entry(
                    KEY_QUICK_RESPONSES,
                    R.string.respond_via_sms_setting_title,
                    Target.Activity(Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS)),
                    R.drawable.ic_message_circle,
                )
            )
            add(
                Entry(
                    KEY_LOOKUP,
                    R.string.lookup_settings_label,
                    Target.Screen(LookupSettingsFragment::class.java.name),
                    R.drawable.ic_world,
                )
            )
            add(
                Entry(
                    KEY_SCREENING,
                    R.string.screening_settings_title,
                    Target.Screen(ScreeningSettingsFragment::class.java.name),
                    R.drawable.ic_user,
                )
            )
            add(
                Entry(
                    KEY_INCOMING_CALLS,
                    R.string.incoming_call_settings_title,
                    Target.Screen(IncomingCallSettingsFragment::class.java.name),
                    R.drawable.ic_phone_incoming,
                )
            )

            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            // "Call settings" (full settings) is shown to the primary user with one SIM; otherwise
            // "Calling accounts".
            val isPrimaryUser = context.getSystemService(UserManager::class.java).isSystemUser
            if (isPrimaryUser && TelephonyManagerCompat.getPhoneCount(telephonyManager) <= 1) {
                add(
                    Entry(
                        KEY_CALL_SETTINGS,
                        R.string.call_settings_label,
                        Target.Activity(
                            Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        ),
                        R.drawable.ic_phone,
                    )
                )
            } else {
                add(
                    Entry(
                        KEY_CALLING_ACCOUNTS,
                        R.string.phone_account_settings_label,
                        Target.Activity(
                            Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        ),
                        R.drawable.ic_device_sim,
                    )
                )
            }
            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                add(
                    Entry(
                        KEY_BLOCKED_NUMBERS,
                        R.string.manage_blocked_numbers_label,
                        Target.Activity(telecomManager.createManageBlockedNumbersIntent()),
                        R.drawable.ic_ban,
                    )
                )
            }
            voicemailEntry(context, isPrimaryUser)?.let(::add)
            if (
                isPrimaryUser &&
                    (TelephonyManagerCompat.isTtyModeSupported(telecomManager) ||
                        TelephonyManagerCompat.isHearingAidCompatibilitySupported(telephonyManager))
            ) {
                add(
                    Entry(
                        KEY_ACCESSIBILITY,
                        R.string.accessibility_settings_title,
                        Target.Activity(
                            Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS)
                        ),
                        R.drawable.ic_eye,
                    )
                )
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
            val target =
                if (soleAccount == null) {
                    LogUtil.i(
                        "DialerSettingsActivity.voicemailEntry",
                        "showing multi-SIM voicemail settings",
                    )
                    Target.Screen(
                        PhoneAccountSelectionFragment::class.java.name,
                        Bundle().apply {
                            putString(
                                PhoneAccountSelectionFragment.PARAM_TARGET_FRAGMENT,
                                VoicemailSettingsFragment::class.java.name,
                            )
                            putString(
                                PhoneAccountSelectionFragment.PARAM_PHONE_ACCOUNT_HANDLE_KEY,
                                VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE,
                            )
                            putBundle(PhoneAccountSelectionFragment.PARAM_ARGUMENTS, Bundle())
                            putInt(
                                PhoneAccountSelectionFragment.PARAM_TARGET_TITLE_RES,
                                R.string.voicemail_settings_label,
                            )
                        },
                    )
                } else {
                    LogUtil.i(
                        "DialerSettingsActivity.voicemailEntry",
                        "showing single-SIM voicemail settings",
                    )
                    Target.Screen(
                        VoicemailSettingsFragment::class.java.name,
                        Bundle().apply {
                            putParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, soleAccount)
                        },
                    )
                }
            return Entry(
                KEY_VOICEMAIL,
                R.string.voicemail_settings_label,
                target,
                R.drawable.ic_call_voicemail,
            )
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
                val account = TelecomUtil.getPhoneAccount(context, handle) ?: continue
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

/** The settings that change how this app looks and sounds; the rest are about calls. */
private val APP_KEYS =
    setOf(
        DialerSettingsActivity.KEY_DISPLAY_OPTIONS,
        DialerSettingsActivity.KEY_APPEARANCE,
        DialerSettingsActivity.KEY_SOUNDS,
        DialerSettingsActivity.KEY_QUICK_RESPONSES,
    )

/**
 * The top-level settings [entries] in two groups, this app's look and sound and then calls, in
 * their order; [onOpen] gets the tapped entry and its title.
 */
@Composable
internal fun SettingsRootList(
    entries: List<DialerSettingsActivity.Entry>,
    onOpen: (DialerSettingsActivity.Entry, String) -> Unit,
) {
    val (app, calls) = entries.partition { it.key in APP_KEYS }
    SettingsList {
        for ((key, title, group) in
            listOf(
                Triple("app", R.string.settings_section_app, app),
                Triple("calls", R.string.settings_section_calls, calls),
            )) {
            if (group.isEmpty()) continue
            item(key = key) {
                SettingsGroup(title = stringResource(title)) {
                    for (entry in group) {
                        val entryTitle = stringResource(entry.titleRes)
                        PvotPreferenceRow(
                            title = entryTitle,
                            onClick = { onOpen(entry, entryTitle) },
                            modifier = Modifier.testTag(TestTags.settingsRow(entry.key)),
                            icon = settingsIcon(entry.icon),
                            iconStyle = PvotRowIconStyle.Plain,
                            chevron = PvotRowChevron.Arrow,
                        )
                    }
                }
            }
        }
    }
}
