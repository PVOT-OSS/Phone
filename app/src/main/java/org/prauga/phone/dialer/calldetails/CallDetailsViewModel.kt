/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.annotation.SuppressLint
import android.app.Application
import android.provider.CallLog.Calls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.callrecord.CallRecordingDataStore
import org.prauga.phone.dialer.callrecord.impl.CallRecorderService
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.dialercontact.DialerContact
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.rtt.RttTranscriptUtil

/**
 * The call details across recreation: the contact and the calls as the user has left them, so a
 * call deleted here stays deleted after a rotation instead of coming back from the intent, and
 * after process death too. One load runs at a time; showing new calls cancels the last load.
 */
internal class CallDetailsViewModel(
    private val application: Application,
    private val handle: SavedStateHandle,
) : ViewModel() {

    var contact: DialerContact =
        handle.get<ByteArray>(KEY_CONTACT)?.let(DialerContact::parseFrom)
            ?: DialerContact.getDefaultInstance()
        private set

    var entries: CallDetailsEntries =
        handle.get<ByteArray>(KEY_ENTRIES)?.let(CallDetailsEntries::parseFrom)
            ?: CallDetailsEntries.getDefaultInstance()
        private set

    /** The screen, or null until the calls are known. */
    var ui by mutableStateOf<CallDetailsUi?>(null)
        private set

    var assistedDialingCountryCode by mutableStateOf<Int?>(null)
        private set

    /** True once the whole history is deleted and the screen has nothing left to show. */
    var finished by mutableStateOf(false)
        private set

    val hasEntries: Boolean
        get() = entries.entriesCount > 0

    private val recordingStore = CallRecordingDataStore()
    private var loadJob: Job? = null

    init {
        // Restored after process death: the calls are in the handle, the screen is not.
        if (hasEntries) load()
    }

    /** Shows the calls with [contact]; the last load, if still running, is dropped. */
    fun show(contact: DialerContact, entries: CallDetailsEntries) {
        this.contact = contact
        this.entries = entries
        handle[KEY_CONTACT] = contact.toByteArray()
        handle[KEY_ENTRIES] = entries.toByteArray()
        assistedDialingCountryCode = null
        if (hasEntries) load()
    }

    /** Removes one call from the history, here and in the call log. */
    fun deleteCall(callId: Long) {
        entries =
            entries
                .toBuilder()
                .apply {
                    val index = entriesList.indexOfFirst { it.callId == callId }
                    if (index >= 0) removeEntries(index)
                }
                .build()
        handle[KEY_ENTRIES] = entries.toByteArray()
        ui = ui?.let { it.copy(entries = it.entries.filterNot { e -> e.callId == callId }) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deleteCalls(listOf(callId.toString())) }
        }
    }

    /** Removes the whole history from the call log; [finished] turns true once it is gone. */
    fun deleteAll() {
        val ids = entries.entriesList.map { it.callId.toString() }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deleteCalls(ids) }
            finished = true
        }
    }

    private fun load() {
        loadJob?.cancel()
        ui = map()
        loadJob =
            viewModelScope.launch {
                val recordings = withContext(Dispatchers.IO) { loadRecordings() }
                val transcripts =
                    try {
                        val ids = ImmutableSet.copyOf(entries.entriesList.map { it.callMappingId })
                        RttTranscriptUtil.getAvailableRttTranscriptIds(application, ids).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LogUtil.w(
                            "CallDetailsViewModel.load",
                            "no transcripts: %s",
                            e.javaClass.simpleName,
                        )
                        emptySet()
                    }
                ui = map(recordings, transcripts)
            }
        if (ui?.header?.usedAssistedDialing == true) {
            viewModelScope.launch {
                assistedDialingCountryCode =
                    withContext(Dispatchers.Default) {
                        OldCallDetailsActivity.countryCodeOf(contact.number)
                    }
            }
        }
    }

    private fun map(
        recordings: Map<Long, List<CallRecording>> = emptyMap(),
        transcripts: Set<String> = emptySet(),
    ) =
        CallDetailsMapper.map(
            contact = contact,
            entries = entries,
            isEmergencyNumber = PhoneNumberHelper.isEmergencyNumber(application, contact.number),
            callbackAction =
                CallbackActionHelper.getCallbackAction(
                    contact.number,
                    entries.getEntries(0).features,
                ),
            numberLabelAndNumber = { label, number ->
                application.getString(R.string.call_subject_type_and_number, label, number)
            },
            emergencyTitle = application.getString(R.string.emergency_number),
            recordingsFor = { recordings[it.callId].orEmpty() },
            rttTranscriptIds = transcripts,
        )

    /** Recordings of each call, by call log ID; read up front so they don't pop in later. */
    private fun loadRecordings(): Map<Long, List<CallRecording>> {
        if (!CallRecorderService.isEnabled(application)) return emptyMap()
        recordingStore.open(application) // opens unless already open
        return entries.entriesList.associate {
            it.callId to recordingStore.getRecordings(contact.number, it.date).orEmpty()
        }
    }

    // The user can't see call log entries without WRITE_CALL_LOG, so it's held whenever this runs.
    @SuppressLint("MissingPermission")
    private fun deleteCalls(ids: List<String>) {
        val selection = "${Calls._ID} IN (${ids.joinToString(",") { "?" }})"
        application.contentResolver.delete(Calls.CONTENT_URI, selection, ids.toTypedArray())
        application.contentResolver.notifyChange(Calls.CONTENT_URI, null)
    }

    override fun onCleared() {
        recordingStore.close()
    }

    private companion object {
        const val KEY_CONTACT = "contact"
        const val KEY_ENTRIES = "entries"
    }
}
