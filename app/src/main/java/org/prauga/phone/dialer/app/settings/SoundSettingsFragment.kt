/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.app.Activity
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.Choice
import org.prauga.phone.dialer.app.settings.ui.ChoiceRow
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.app.settings.ui.SettingsGroup
import org.prauga.phone.dialer.app.settings.ui.settingsIcon
import org.prauga.phone.dialer.callrecord.impl.CallRecorderService
import org.prauga.phone.dialer.ui.TestTags

/**
 * Ringtone, vibration, dialpad tones, in-call alerts and the call recording format. Opened only
 * when Phone may write system settings; the settings list sends the user to the system's sound
 * settings otherwise.
 */
class SoundSettingsFragment : SettingsComposeFragment() {

    private var model: SoundSettingsModel? = null

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val model = remember { SoundSettingsModel(context).also { this.model = it } }
        SoundSettingsList(model)
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.System.canWrite(requireContext())) {
            // The user took away the permission while this screen was open; nothing here works.
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }
        model?.refresh()
    }
}

/**
 * The sound settings and where each one lives: system settings, or the device-protected default
 * preferences that the in-call process reads.
 */
internal class SoundSettingsModel(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    /** Preferences readable before the user unlocks the phone, shared with the in-call process. */
    val devicePrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())

    val hasVibrator: Boolean = context.getSystemService(Vibrator::class.java)?.hasVibrator() == true
    val showDtmfToneLength: Boolean = run {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        telephony.canChangeDtmfToneLength() && (telephony.isWorldPhone || !hidesCarrierSettings())
    }
    val showCallRecording: Boolean = CallRecorderService.isEnabled(context)

    var vibrateWhenRinging by mutableStateOf(false)
        private set
    var dialpadTones by mutableStateOf(true)
        private set
    var dtmfToneLength by mutableIntStateOf(DTMF_TONE_TYPE_NORMAL)
        private set
    var ringtoneName by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    /** Re-reads the system settings, which can change while this screen is in the background. */
    fun refresh() {
        vibrateWhenRinging = hasVibrator &&
            Settings.System.getInt(resolver, Settings.System.VIBRATE_WHEN_RINGING, 0) == 1
        dialpadTones = Settings.System.getInt(resolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        dtmfToneLength =
            Settings.System.getInt(resolver, Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, DTMF_TONE_TYPE_NORMAL)
    }

    /** Runs [write] if Phone may write system settings, and says so otherwise. */
    private inline fun writeSystem(write: () -> Unit): Boolean {
        if (!Settings.System.canWrite(context)) {
            Toast.makeText(context, R.string.toast_cannot_write_system_settings, Toast.LENGTH_SHORT).show()
            return false
        }
        write()
        return true
    }

    fun changeVibrateWhenRinging(on: Boolean) {
        if (writeSystem { Settings.System.putInt(resolver, Settings.System.VIBRATE_WHEN_RINGING, if (on) 1 else 0) }) {
            vibrateWhenRinging = on
        }
    }

    fun changeDialpadTones(on: Boolean) {
        if (writeSystem { Settings.System.putInt(resolver, Settings.System.DTMF_TONE_WHEN_DIALING, if (on) 1 else 0) }) {
            dialpadTones = on
        }
    }

    fun changeDtmfToneLength(index: Int) {
        if (writeSystem { Settings.System.putInt(resolver, Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, index) }) {
            dtmfToneLength = index
        }
    }

    /** The current ringtone, for the picker to start from. */
    fun ringtoneUri(): Uri? = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)

    /** The system ringtone picker, starting from the current ringtone. */
    fun ringtonePickerIntent(title: String): Intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri())

    /** Makes [uri] the phone's ringtone; null is silent. */
    fun setRingtone(uri: Uri?) {
        writeSystem { RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri) }
    }

    /** Looks up the ringtone's title, which reads the media provider. */
    suspend fun loadRingtoneName() {
        ringtoneName = withContext(Dispatchers.IO) {
            val uri = ringtoneUri() ?: return@withContext context.getString(R.string.ringtone_silent)
            runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: context.getString(R.string.ringtone_unknown)
        }
    }

    fun hasDndAccess(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun hidesCarrierSettings(): Boolean = try {
        context.getSystemService(CarrierConfigManager::class.java).config
            ?.getBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL) == true
    } catch (e: SecurityException) {
        // Needs READ_PHONE_STATE; without it, treat the carrier as not hiding its settings.
        false
    }

    companion object {
        const val DTMF_TONE_TYPE_NORMAL = 0
        const val KEY_ENABLE_DND = "incall_enable_dnd"
    }
}

/** The sound settings rows, reading and writing through [model]. */
@Composable
internal fun SoundSettingsList(model: SoundSettingsModel) {
    val context = LocalContext.current
    val ringtoneTitle = stringResource(R.string.ringtone_title)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            if (!data.hasExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)) return@rememberLauncherForActivityResult
            model.setRingtone(IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java))
        }
    }
    LaunchedEffect(model.ringtoneName == null) { model.loadRingtoneName() }
    var askForDndAccess by rememberSaveable { mutableStateOf(false) }
    val inCallVibrations = listOf(
        Triple(stringResource(R.string.incall_vibrate_outgoing_key), R.string.incall_vibrate_outgoing_title, null),
        Triple(stringResource(R.string.incall_vibrate_call_waiting_key), R.string.incall_vibrate_call_waiting_title, null),
        Triple(stringResource(R.string.incall_vibrate_hangup_key), R.string.incall_vibrate_hangup_title, null),
        Triple(stringResource(R.string.incall_vibrate_45_key), R.string.incall_vibrate_45_title, R.string.incall_vibrate_45_summary),
    )

    SettingsList {
        item(key = "ringing") {
            SettingsGroup {
                PvotPreferenceRow(
                    title = ringtoneTitle,
                    value = model.ringtoneName,
                    onClick = { picker.launch(model.ringtonePickerIntent(ringtoneTitle)) },
                    modifier = Modifier.testTag(TestTags.settingsRow("ringtone")),
                    icon = settingsIcon(R.drawable.ic_bell),
                    iconStyle = PvotRowIconStyle.Plain,
                )
                if (model.hasVibrator) {
                    PvotSwitchRow(
                        title = stringResource(R.string.vibrate_on_ring_title),
                        checked = model.vibrateWhenRinging,
                        onCheckedChange = model::changeVibrateWhenRinging,
                        modifier = Modifier.testTag(TestTags.settingsRow("vibrate_when_ringing")),
                        icon = settingsIcon(R.drawable.ic_wave_sine),
                        iconStyle = PvotRowIconStyle.Plain,
                    )
                }
                PvotSwitchRow(
                    title = stringResource(R.string.dtmf_tone_enable_title),
                    checked = model.dialpadTones,
                    onCheckedChange = model::changeDialpadTones,
                    modifier = Modifier.testTag(TestTags.settingsRow("dialpad_tones")),
                    icon = settingsIcon(R.drawable.ic_dialpad),
                    iconStyle = PvotRowIconStyle.Plain,
                )
                if (model.showDtmfToneLength) {
                    val labels = stringArrayResource(R.array.dtmf_tone_length_entries)
                    ChoiceRow(
                        title = stringResource(R.string.dtmf_tone_length_title),
                        choices = labels.mapIndexed { index, label -> Choice(index, label) },
                        selected = model.dtmfToneLength,
                        onSelect = model::changeDtmfToneLength,
                        testTag = "dtmf_tone_length",
                        icon = R.drawable.ic_adjustments,
                    )
                }
            }
        }

        item(key = "incall") {
            SettingsGroup(title = stringResource(R.string.incall_category_title)) {
                var enabled by remember {
                    mutableStateOf(model.devicePrefs.getBoolean(SoundSettingsModel.KEY_ENABLE_DND, false))
                }
                PvotSwitchRow(
                    title = stringResource(R.string.incall_enable_dnd_title),
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on && !model.hasDndAccess()) {
                            // Leave it off until the user has granted access.
                            askForDndAccess = true
                        } else {
                            enabled = on
                            model.devicePrefs.edit { putBoolean(SoundSettingsModel.KEY_ENABLE_DND, on) }
                        }
                    },
                    modifier = Modifier.testTag(TestTags.settingsRow(SoundSettingsModel.KEY_ENABLE_DND)),
                    icon = settingsIcon(R.drawable.ic_bell),
                    iconStyle = PvotRowIconStyle.Plain,
                )
                if (model.hasVibrator) {
                    for ((keyName, title, summary) in inCallVibrations) {
                        DeviceSwitchRow(model.devicePrefs, keyName, stringResource(title), summary?.let { stringResource(it) })
                    }
                }
            }
        }

        if (model.showCallRecording) {
            item(key = "recording") {
                SettingsGroup(title = stringResource(R.string.call_recording_category_title)) {
                    StringChoiceRow(
                        prefs = model.devicePrefs,
                        title = R.string.call_recording_format,
                        key = R.string.call_recording_format_key,
                        default = R.string.call_recording_format_default,
                        entries = R.array.call_recording_encoder_entries,
                        values = R.array.call_recording_encoder_values,
                        icon = R.drawable.ic_player_record,
                    )
                }
            }
        }
    }

    PvotDialog(
        visible = askForDndAccess,
        onDismissRequest = { askForDndAccess = false },
        title = stringResource(R.string.incall_enable_dnd_title),
        message = stringResource(R.string.incall_dnd_dialog_message),
    ) {
        PvotDialogAction(
            label = stringResource(R.string.allow),
            style = PvotDialogActionStyle.Primary,
            onClick = {
                askForDndAccess = false
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
        )
        PvotDialogAction(label = stringResource(R.string.deny), onClick = { askForDndAccess = false })
    }
}

/** A switch stored as a boolean in [prefs] under [key], off until the user turns it on. */
@Composable
private fun DeviceSwitchRow(prefs: SharedPreferences, key: String, title: String, summary: String?) {
    var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
    PvotSwitchRow(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = {
            checked = it
            prefs.edit { putBoolean(key, it) }
        },
        modifier = Modifier.testTag(TestTags.settingsRow(key)),
        icon = settingsIcon(R.drawable.ic_wave_sine),
        iconStyle = PvotRowIconStyle.Plain,
    )
}
