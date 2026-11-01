/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.provider.CallLog.Calls
import android.telecom.PhoneAccount
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.calllogutils.CallTypeHelper
import org.prauga.phone.dialer.compat.telephony.TelephonyManagerCompat
import org.prauga.phone.dialer.dialercontact.DialerContact
import org.prauga.phone.dialer.lettertile.LetterTileDrawable

/** Who the call details are for, and how to call them back. */
internal data class CallDetailsHeader(
    /** The name, the number, or "Emergency number". */
    val title: String,
    /** Number label and number under the name, if there is a name. */
    val subtitle: String?,
    /** The SIM's network name and highlight color, if known. */
    val network: String?,
    val networkColor: Int?,
    /** Callback: voice, video, or none for numbers that can't be called. */
    @CallbackAction val callbackAction: Int,
    /** Whether the most recent call used assisted dialing. */
    val usedAssistedDialing: Boolean,
)

/** One call in the history. */
internal data class CallDetailsEntryUi(
    val callId: Long,
    val callType: Int,
    val date: Long,
    val duration: Long,
    val dataUsage: Long,
    val isVideo: Boolean,
    val isPulledExternally: Boolean,
    val isHd: Boolean,
    val isRtt: Boolean,
    val hasRttTranscript: Boolean,
    val callMappingId: String,
    val recordings: List<CallRecording>,
    /** What was sent with the call through call composer: urgent, a note and a picture. */
    val isUrgent: Boolean = false,
    val composerNote: String? = null,
    val composerPicture: String? = null,
) {
    /** Missed, rejected and blocked calls had no talk time to show. */
    val showsDuration: Boolean get() = !CallTypeHelper.isMissedCallType(callType)
}

/** What the call details screen shows. */
internal data class CallDetailsUi(
    val header: CallDetailsHeader,
    val entries: List<CallDetailsEntryUi>,
    /** Copy and "edit before call" need a number. */
    val hasNumber: Boolean,
    /** Whether a text message can go to the number: one that can be called back, not an emergency or voicemail number. */
    val canMessage: Boolean = false,
)

/** Builds [CallDetailsUi] from the call log data the call history passes in. */
internal object CallDetailsMapper {

    fun map(
        contact: DialerContact,
        entries: CallDetailsEntries,
        isEmergencyNumber: Boolean,
        @CallbackAction callbackAction: Int,
        numberLabelAndNumber: (label: String, number: String) -> String,
        emergencyTitle: String,
        recordingsFor: (CallDetailsEntries.CallDetailsEntry) -> List<CallRecording> = { emptyList() },
        rttTranscriptIds: Set<String> = emptySet(),
    ): CallDetailsUi {
        val subtitle = when {
            isEmergencyNumber || contact.displayNumber.isEmpty() -> null
            contact.numberLabel.isEmpty() -> contact.displayNumber
            else -> numberLabelAndNumber(contact.numberLabel, contact.displayNumber)
        }
        val sim = contact.simDetails
        val header = CallDetailsHeader(
            title = if (isEmergencyNumber) emergencyTitle else contact.nameOrNumber,
            subtitle = subtitle,
            network = sim.network.ifEmpty { null },
            networkColor = sim.color.takeIf { sim.network.isNotEmpty() && it != PhoneAccount.NO_HIGHLIGHT_COLOR },
            callbackAction = callbackAction,
            usedAssistedDialing = entries.entriesList.firstOrNull()?.let {
                it.features and TelephonyManagerCompat.FEATURES_ASSISTED_DIALING != 0
            } == true,
        )
        return CallDetailsUi(
            header = header,
            entries = entries.entriesList.map { entry ->
                val features = entry.features
                CallDetailsEntryUi(
                    callId = entry.callId,
                    callType = entry.callType,
                    date = entry.date,
                    duration = entry.duration,
                    dataUsage = entry.dataUsage,
                    isVideo = features has Calls.FEATURES_VIDEO,
                    isPulledExternally = features has Calls.FEATURES_PULLED_EXTERNALLY,
                    isHd = features has Calls.FEATURES_HD_CALL,
                    isRtt = features has Calls.FEATURES_RTT,
                    hasRttTranscript = entry.hasRttTranscript || entry.callMappingId in rttTranscriptIds,
                    callMappingId = entry.callMappingId,
                    recordings = recordingsFor(entry),
                    isUrgent = entry.isUrgent,
                    composerNote = entry.subject.takeIf { it.isNotBlank() },
                    composerPicture = entry.composerPictureUri.takeIf { it.isNotBlank() },
                )
            },
            hasNumber = contact.number.isNotEmpty(),
            canMessage = contact.number.isNotEmpty() && callbackAction != CallbackAction.NONE && !isEmergencyNumber &&
                contact.contactType != LetterTileDrawable.TYPE_VOICEMAIL,
        )
    }

    private infix fun Int.has(flag: Int) = this and flag == flag
}
