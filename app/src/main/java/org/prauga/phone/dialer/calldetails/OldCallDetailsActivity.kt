/*
 * Copyright (C) 2017 The Android Open Source Project
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

package org.prauga.phone.dialer.calldetails

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog.Calls
import android.provider.MediaStore
import android.telecom.PhoneAccountHandle
import android.text.format.DateFormat
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.contacts.common.dialog.CallSubjectDialog
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.AccountSelectionActivity
import org.prauga.phone.dialer.app.calllog.IntentProvider
import org.prauga.phone.dialer.app.settings.IncomingCallSettings
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.clipboard.ClipboardUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.dialercontact.DialerContact
import org.prauga.phone.dialer.glidephotomanager.PhotoInfo
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.main.impl.SnackbarPrompt
import org.prauga.phone.dialer.postcall.PostCall
import org.prauga.phone.dialer.precall.PreCall
import org.prauga.phone.dialer.preferredsim.ContactSim
import org.prauga.phone.dialer.protos.ProtoParsers
import org.prauga.phone.dialer.reminder.CallbackReminderActivity
import org.prauga.phone.dialer.rtt.RttTranscriptActivity
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.PhoneSnackbarHost
import org.prauga.phone.dialer.ui.QuickContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.CallUtil
import org.prauga.phone.dialer.util.DialerUtils
import org.prauga.phone.dialer.util.IntentUtil
import org.prauga.phone.dialer.util.UriUtils

/**
 * The calls with one number, opened from the call history, with call back, copy, edit and delete.
 */
/** No number type for a contact made from a call, so the contacts app picks its default. */
private const val NO_NUMBER_TYPE = -1

class OldCallDetailsActivity : ComponentActivity() {

    private val viewModel: CallDetailsViewModel by viewModels {
        viewModelFactory {
            initializer { CallDetailsViewModel(application, createSavedStateHandle()) }
        }
    }
    private val contact: DialerContact
        get() = viewModel.contact

    private val snackbars = SnackbarHostState()

    /** The SIM row of a saved contact on a phone with more than one SIM; null hides it. */
    private var simForCalls by mutableStateOf<SimForCallsUi?>(null)
    private var simDataIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the view model already has the calls, less any the user deleted since.
        if (savedInstanceState == null) handleIntent(intent)
        if (!viewModel.hasEntries) {
            finish()
            return
        }
        loadSimForCalls()
        setPvotContent {
            PhoneScreenTheme {
                val finished = viewModel.finished
                LaunchedEffect(finished) { if (finished) finish() }
                val ui = viewModel.ui ?: return@PhoneScreenTheme
                Box(Modifier.fillMaxSize()) {
                    CallDetailsScreen(
                        ui = ui,
                        assistedDialingCountryCode = viewModel.assistedDialingCountryCode,
                        callWithIntent =
                            AccountSelectionActivity.createIntent(
                                this@OldCallDetailsActivity,
                                contact.number,
                                CallInitiationType.Type.CALL_DETAILS,
                            ),
                        actions = actions,
                        blocked = viewModel.blocked,
                        canAddContact = ui.hasNumber && !isSavedContact(),
                        canRemind = canRemind(ui),
                        simForCalls = simForCalls,
                        // As the dialpad offers it: when an account can send a note with a call.
                        canCallWithNote =
                            ui.hasNumber &&
                                ui.header.callbackAction != CallbackAction.NONE &&
                                CallUtil.isCallWithSubjectSupported(this@OldCallDetailsActivity),
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
                    // Post-call prompts, above the navigation bar.
                    PhoneSnackbarHost(
                        snackbars,
                        Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = PvotSpacing.L),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        if (!viewModel.hasEntries) finish()
    }

    override fun onResume() {
        super.onResume()
        PostCall.promptUserForMessageIfNecessary(this, SnackbarPrompt(lifecycleScope, snackbars))
    }

    /** Reads the SIM saved for a contact's number, when the phone has more than one to choose. */
    private fun loadSimForCalls() {
        if (!isSavedContact() || contact.number.isEmpty()) return
        val accounts = TelecomUtil.getCallCapablePhoneAccounts(this)
        if (accounts.size < 2) return
        val lookupKey = lookupKey(contact.contactUri) ?: return
        val number = contact.number
        lifecycleScope.launch {
            val (ids, current) =
                withContext(Dispatchers.IO) {
                    val ids = ContactSim.dataIds(this@OldCallDetailsActivity, lookupKey, number)
                    ids to ContactSim.get(this@OldCallDetailsActivity, ids)
                }
            if (ids.isEmpty()) return@launch
            simDataIds = ids
            simForCalls = simUi(accounts, current)
        }
    }

    private fun simUi(accounts: List<PhoneAccountHandle>, current: PhoneAccountHandle?) =
        SimForCallsUi(
            current = current?.let(::accountLabel),
            options = accounts.map { SimOption(accountLabel(it) ?: it.id, it) },
        )

    private fun accountLabel(account: PhoneAccountHandle): String? =
        TelecomUtil.getPhoneAccount(this, account)?.label?.toString()

    /** The lookup key in a contact lookup URI (…/contacts/lookup/<key>/<id>). */
    private fun lookupKey(contactUri: String): String? {
        val segments = Uri.parse(contactUri).pathSegments
        val at = segments.indexOf("lookup")
        return segments.getOrNull(at + 1).takeIf { at >= 0 }
    }

    /**
     * Whether the number belongs to a contact, rather than only to the call log's cached lookup.
     */
    private fun isSavedContact(): Boolean {
        val uri = contact.contactUri.ifEmpty { null }?.let(Uri::parse) ?: return false
        return !UriUtils.isEncodedContactUri(uri)
    }

    /** A reminder is offered for a missed call that can be returned, while reminders are on. */
    private fun canRemind(ui: CallDetailsUi): Boolean =
        ui.entries.firstOrNull()?.callType == Calls.MISSED_TYPE &&
            ui.header.callbackAction != CallbackAction.NONE &&
            viewModel.blocked != true &&
            IncomingCallSettings.callbackReminders(this)

    private fun handleIntent(intent: Intent) {
        viewModel.show(
            contact =
                ProtoParsers.getTrusted(intent, EXTRA_CONTACT, DialerContact.getDefaultInstance()),
            entries =
                ProtoParsers.getTrusted(
                    intent,
                    EXTRA_CALL_DETAILS_ENTRIES,
                    CallDetailsEntries.getDefaultInstance(),
                ),
        )
    }

    private val actions =
        object : CallDetailsActions {
            override fun back() = finish()

            override fun callBack(video: Boolean) {
                val builder =
                    if (video) {
                        CallIntentBuilder(contact.number, CallInitiationType.Type.CALL_DETAILS)
                            .setIsVideoCall(true)
                    } else {
                        CallIntentBuilder(
                                contact.number + contact.postDialDigits,
                                CallInitiationType.Type.CALL_DETAILS,
                            )
                            .apply {
                                if (
                                    intent.getBooleanExtra(
                                        EXTRA_CAN_SUPPORT_ASSISTED_DIALING,
                                        false,
                                    )
                                )
                                    setAllowAssistedDial(true)
                            }
                    }
                PreCall.start(this@OldCallDetailsActivity, builder)
            }

            override fun message() =
                DialerUtils.startActivityWithErrorToast(
                    this@OldCallDetailsActivity,
                    IntentUtil.getSendSmsIntent(contact.number),
                )

            override fun copyNumber() =
                ClipboardUtils.copyText(this@OldCallDetailsActivity, null, contact.number, true)

            override fun editNumber() =
                DialerUtils.startActivityWithErrorToast(
                    this@OldCallDetailsActivity,
                    Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(contact.number)),
                )

            override fun delete() = viewModel.deleteAll()

            override fun setBlocked(block: Boolean) = viewModel.setBlocked(block)

            override fun addContact(newContact: Boolean) {
                val intent =
                    IntentProvider.getAddContactIntentProvider(
                            contact.contactUri.ifEmpty { null }?.let(Uri::parse),
                            null,
                            contact.number,
                            NO_NUMBER_TYPE,
                            newContact,
                        )
                        .getClickIntent(this@OldCallDetailsActivity)
                DialerUtils.startActivityWithErrorToast(this@OldCallDetailsActivity, intent)
            }

            override fun lookUp() {
                val search =
                    Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, "\"${contact.number}\"")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(search)
                } catch (e: ActivityNotFoundException) {
                    lifecycleScope.launch {
                        snackbars.showSnackbar(getString(R.string.call_details_look_up_no_app))
                    }
                }
            }

            override fun setSimForCalls(account: PhoneAccountHandle?) {
                val ids = simDataIds
                val accounts = simForCalls?.options?.map { it.account } ?: return
                simForCalls = simUi(accounts, account)
                lifecycleScope.launch(Dispatchers.IO) {
                    ContactSim.set(this@OldCallDetailsActivity, ids, account)
                }
            }

            override fun callWithNote() =
                CallSubjectDialog.start(
                    this@OldCallDetailsActivity,
                    contact.photoId,
                    contact.photoUri.ifEmpty { null }?.let(Uri::parse),
                    contact.contactUri.ifEmpty { null }?.let(Uri::parse),
                    contact.nameOrNumber,
                    contact.number,
                    contact.displayNumber.ifEmpty { null },
                    contact.numberLabel.ifEmpty { null },
                    contact.contactType,
                    null,
                )

            override fun remind() =
                startActivity(
                    CallbackReminderActivity.newIntent(
                        this@OldCallDetailsActivity,
                        contact.number,
                        contact.nameOrNumber.takeIf { isSavedContact() },
                    )
                )

            override fun deleteCall(entry: CallDetailsEntryUi) = viewModel.deleteCall(entry.callId)

            override fun showTranscript(entry: CallDetailsEntryUi) =
                startActivity(
                    RttTranscriptActivity.getIntent(
                        this@OldCallDetailsActivity,
                        entry.callMappingId,
                        contact.nameOrNumber,
                        photoInfo(),
                    )
                )

            override fun play(recording: CallRecording) {
                val uri =
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        recording.mediaId,
                    )
                val extension = MimeTypeMap.getFileExtensionFromUrl(recording.fileName)
                val mime =
                    extension
                        .takeIf { it.isNotEmpty() }
                        ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                        ?: "audio/*"
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                            this@OldCallDetailsActivity,
                            R.string.call_playback_no_app_found_toast,
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }

            override fun recordingTime(recording: CallRecording): String {
                val pattern =
                    DateFormat.getBestDateTimePattern(
                        Locale.getDefault(),
                        if (DateFormat.is24HourFormat(this@OldCallDetailsActivity)) "Hmss"
                        else "hmssa",
                    )
                return SimpleDateFormat(pattern, Locale.getDefault())
                    .format(Date(recording.startRecordingTime))
            }
        }

    private fun photoInfo(): PhotoInfo =
        PhotoInfo.newBuilder()
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
        ): Intent =
            Intent(context, OldCallDetailsActivity::class.java).apply {
                ProtoParsers.put(this, EXTRA_CONTACT, contact)
                ProtoParsers.put(this, EXTRA_CALL_DETAILS_ENTRIES, details)
                putExtra(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, canSupportAssistedDialing)
            }

        /** The country calling code of [number], or 0 if it can't be parsed. */
        internal fun countryCodeOf(number: String): Int =
            try {
                PhoneNumberUtil.getInstance().parse(number, null).countryCode
            } catch (e: NumberParseException) {
                LogUtil.w(
                    "OldCallDetailsActivity.countryCodeOf",
                    "couldn't parse ${LogUtil.sanitizePii(number)}",
                    e,
                )
                0
            }
    }
}
