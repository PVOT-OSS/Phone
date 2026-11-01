/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog.Calls
import android.provider.MediaStore
import android.text.format.DateFormat
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.lifecycleScope
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.AccountSelectionActivity
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.callrecord.CallRecordingDataStore
import org.prauga.phone.dialer.callrecord.impl.CallRecorderService
import org.prauga.phone.dialer.clipboard.ClipboardUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.dialercontact.DialerContact
import org.prauga.phone.dialer.glidephotomanager.PhotoInfo
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.postcall.PostCall
import org.prauga.phone.dialer.precall.PreCall
import org.prauga.phone.dialer.protos.ProtoParsers
import org.prauga.phone.dialer.rtt.RttTranscriptActivity
import org.prauga.phone.dialer.rtt.RttTranscriptUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.QuickContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.CallUtil
import org.prauga.phone.dialer.util.DialerUtils
import org.prauga.phone.dialer.util.IntentUtil

/** The calls with one number, opened from the call history, with call back, copy, edit and delete. */
class OldCallDetailsActivity : ComponentActivity() {

    private lateinit var contact: DialerContact
    private var entries: CallDetailsEntries = CallDetailsEntries.getDefaultInstance()
    private var ui by mutableStateOf<CallDetailsUi?>(null)
    private var assistedDialingCountryCode by mutableStateOf<Int?>(null)
    private val recordingStore = CallRecordingDataStore()

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setPvotContent {
            PhoneScreenTheme {
                val ui = ui ?: return@PhoneScreenTheme
                CallDetailsScreen(
                    ui = ui,
                    assistedDialingCountryCode = assistedDialingCountryCode,
                    callWithIntent = AccountSelectionActivity.createIntent(
                        this, contact.number, CallInitiationType.Type.CALL_DETAILS),
                    actions = actions,
                    avatar = { size ->
                        QuickContactAvatar(
                            name = contact.nameOrNumber,
                            contactUri = contact.contactUri.ifEmpty { null }?.let(Uri::parse),
                            photoId = contact.photoId,
                            photoUri = contact.photoUri.ifEmpty { null }?.let(Uri::parse),
                            size = size,
                            modifier = Modifier.testTag(TestTags.CALL_DETAILS_AVATAR),
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PostCall.promptUserForMessageIfNecessary(this, findViewById(android.R.id.content))
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingStore.close()
    }

    private fun handleIntent(intent: Intent) {
        contact = ProtoParsers.getTrusted(intent, EXTRA_CONTACT, DialerContact.getDefaultInstance())
        entries = ProtoParsers.getTrusted(intent, EXTRA_CALL_DETAILS_ENTRIES, CallDetailsEntries.getDefaultInstance())
        if (entries.entriesCount == 0) {
            finish()
            return
        }
        ui = map()
        lifecycleScope.launch {
            val recordings = withContext(Dispatchers.IO) { loadRecordings() }
            val transcripts = runCatching {
                RttTranscriptUtil.getAvailableRttTranscriptIds(
                    this@OldCallDetailsActivity, entries.entriesList.map { it.callMappingId }.toSet().let {
                        com.google.common.collect.ImmutableSet.copyOf(it)
                    }).await()
            }.getOrDefault(emptySet())
            ui = map(recordings, transcripts)
        }
        if (ui?.header?.usedAssistedDialing == true) {
            lifecycleScope.launch {
                assistedDialingCountryCode = withContext(Dispatchers.Default) { countryCodeOf(contact.number) }
            }
        }
    }

    private fun map(
        recordings: Map<Long, List<CallRecording>> = emptyMap(),
        transcripts: Set<String> = emptySet(),
    ) = CallDetailsMapper.map(
        contact = contact,
        entries = entries,
        isEmergencyNumber = PhoneNumberHelper.isEmergencyNumber(this, contact.number),
        callbackAction = CallbackActionHelper.getCallbackAction(contact.number, entries.getEntries(0).features),
        numberLabelAndNumber = { label, number -> getString(R.string.call_subject_type_and_number, label, number) },
        emergencyTitle = getString(R.string.emergency_number),
        recordingsFor = { recordings[it.callId].orEmpty() },
        rttTranscriptIds = transcripts,
    )

    /** Recordings of each call, by call log ID; read up front so they don't pop in later. */
    private fun loadRecordings(): Map<Long, List<CallRecording>> {
        if (!CallRecorderService.isEnabled(this)) return emptyMap()
        recordingStore.open(this) // opens unless already open
        return entries.entriesList.associate { it.callId to recordingStore.getRecordings(contact.number, it.date).orEmpty() }
    }

    private val actions = object : CallDetailsActions {
        override fun back() = finish()

        override fun callBack(video: Boolean) {
            val builder = if (video) {
                CallIntentBuilder(contact.number, CallInitiationType.Type.CALL_DETAILS).setIsVideoCall(true)
            } else {
                CallIntentBuilder(contact.number + contact.postDialDigits, CallInitiationType.Type.CALL_DETAILS)
                    .apply { if (intent.getBooleanExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, false)) setAllowAssistedDial(true) }
            }
            PreCall.start(this@OldCallDetailsActivity, builder)
        }

        override fun message() = DialerUtils.startActivityWithErrorToast(
            this@OldCallDetailsActivity, IntentUtil.getSendSmsIntent(contact.number))

        override fun copyNumber() = ClipboardUtils.copyText(this@OldCallDetailsActivity, null, contact.number, true)

        override fun editNumber() = DialerUtils.startActivityWithErrorToast(
            this@OldCallDetailsActivity, Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(contact.number)))

        override fun delete() {
            val ids = entries.entriesList.map { it.callId.toString() }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { deleteCalls(ids) }
                finish()
            }
        }

        override fun deleteCall(entry: CallDetailsEntryUi) {
            entries = entries.toBuilder().apply {
                val index = entriesList.indexOfFirst { it.callId == entry.callId }
                if (index >= 0) removeEntries(index)
            }.build()
            ui = ui?.let { it.copy(entries = it.entries.filterNot { e -> e.callId == entry.callId }) }
            lifecycleScope.launch { withContext(Dispatchers.IO) { deleteCalls(listOf(entry.callId.toString())) } }
        }

        override fun showTranscript(entry: CallDetailsEntryUi) = startActivity(
            RttTranscriptActivity.getIntent(this@OldCallDetailsActivity, entry.callMappingId, contact.nameOrNumber, photoInfo()))

        override fun play(recording: CallRecording) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, recording.mediaId)
            val extension = MimeTypeMap.getFileExtensionFromUrl(recording.fileName)
            val mime = extension.takeIf { it.isNotEmpty() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: "audio/*"
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@OldCallDetailsActivity, R.string.call_playback_no_app_found_toast, Toast.LENGTH_LONG).show()
            }
        }

        override fun recordingTime(recording: CallRecording): String {
            val pattern = DateFormat.getBestDateTimePattern(
                Locale.getDefault(), if (DateFormat.is24HourFormat(this@OldCallDetailsActivity)) "Hmss" else "hmssa")
            return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(recording.startRecordingTime))
        }
    }

    // The user can't see call log entries without WRITE_CALL_LOG, so it's held whenever this runs.
    @SuppressLint("MissingPermission")
    private fun deleteCalls(ids: List<String>) {
        val selection = "${Calls._ID} IN (${ids.joinToString(",") { "?" }})"
        contentResolver.delete(Calls.CONTENT_URI, selection, ids.toTypedArray())
        contentResolver.notifyChange(Calls.CONTENT_URI, null)
    }

    private fun photoInfo(): PhotoInfo = PhotoInfo.newBuilder()
        .setPhotoUri(contact.photoUri)
        .setPhotoId(contact.photoId)
        .setName(contact.nameOrNumber)
        .setLookupUri(contact.contactUri)
        .apply {
            when (contact.contactType) {
                LetterTileDrawable.TYPE_VOICEMAIL -> setIsVoicemail(true)
                LetterTileDrawable.TYPE_BUSINESS -> setIsBusiness(true)
                LetterTileDrawable.TYPE_SPAM -> setIsSpam(true)
            }
        }
        .build()

    companion object {
        const val EXTRA_CALL_DETAILS_ENTRIES = "call_details_entries"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_CAN_SUPPORT_ASSISTED_DIALING = "can_support_assisted_dialing"

        @JvmStatic
        fun isLaunchIntent(intent: Intent): Boolean =
            intent.component?.className == OldCallDetailsActivity::class.java.name

        /** An intent to show [details] of calls with [contact]. */
        @JvmStatic
        fun newInstance(
            context: Context,
            details: CallDetailsEntries,
            contact: DialerContact,
            canSupportAssistedDialing: Boolean,
        ): Intent = Intent(context, OldCallDetailsActivity::class.java).apply {
            ProtoParsers.put(this, EXTRA_CONTACT, contact)
            ProtoParsers.put(this, EXTRA_CALL_DETAILS_ENTRIES, details)
            putExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, canSupportAssistedDialing)
        }

        /** The country calling code of [number], or 0 if it can't be parsed. */
        internal fun countryCodeOf(number: String): Int = try {
            PhoneNumberUtil.getInstance().parse(number, null).countryCode
        } catch (e: NumberParseException) {
            LogUtil.w("OldCallDetailsActivity.countryCodeOf", "couldn't parse ${LogUtil.sanitizePii(number)}", e)
            0
        }
    }
}
