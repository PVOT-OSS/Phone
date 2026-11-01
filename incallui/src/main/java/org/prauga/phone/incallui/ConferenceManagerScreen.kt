/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.content.Context
import android.telecom.Call
import android.telephony.PhoneNumberUtils
import android.text.TextDirectionHeuristics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import android.text.BidiFormatter
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.incallui.R
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.ContactInfoCache.ContactCacheEntry
import org.prauga.phone.incallui.call.DialerCall
import org.prauga.phone.incallui.call.state.DialerCallState
import java.lang.ref.WeakReference

/** One person in a conference call, as the conference screen shows them. */
data class ConferenceParticipant(
    val callId: String,
    val name: String?,
    val number: String?,
    val photoUri: String?,
    val colorKey: String,
    val onHold: Boolean,
    val canSeparate: Boolean,
    val canDisconnect: Boolean,
)

/**
 * The people in the conference as Compose state, sorted by name when someone joins. Each
 * participant's contact is looked up once, then their row refreshes with the result.
 */
class ConferenceParticipants(private val context: Context) {

    private class Info(var call: DialerCall, var contact: ContactCacheEntry, var lookupComplete: Boolean = false)

    private val infos = mutableListOf<Info>()
    private var parentCanSeparate = false

    var participants by mutableStateOf<List<ConferenceParticipant>>(emptyList())
        private set

    /** The conference's calls changed; [parentCanSeparate] is whether a call may be split out. */
    fun update(calls: List<DialerCall>, parentCanSeparate: Boolean) {
        this.parentCanSeparate = parentCanSeparate
        val cache = ContactInfoCache.getInstance(context)
        var added = false
        for (call in calls) {
            val contact = cache.getInfo(call.id) ?: ContactInfoCache.buildCacheEntryFromCall(context, call)
            val existing = infos.find { it.call.id == call.id }
            if (existing != null) {
                existing.call = call
                existing.contact = contact
            } else {
                added = true
                infos += Info(call, contact)
            }
        }
        val ids = calls.map { it.id }.toSet()
        infos.removeAll { it.call.id !in ids }
        if (added) {
            val preferences = ContactsComponent.get(context).contactDisplayPreferences()
            infos.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                preferences.getSortName(it.contact.namePrimary, it.contact.nameAlternative).orEmpty()
            })
        }
        rebuild()
    }

    /** A participant's capabilities changed. */
    fun refresh(call: DialerCall) {
        val info = infos.find { it.call.id == call.id } ?: return
        info.call = call
        rebuild()
    }

    private fun updateContact(callId: String, entry: ContactCacheEntry) {
        val info = infos.find { it.call.id == callId } ?: return
        info.contact = entry
        info.lookupComplete = true
        rebuild()
    }

    private fun rebuild() {
        val cache = ContactInfoCache.getInstance(context)
        val preferences = ContactsComponent.get(context).contactDisplayPreferences()
        participants = infos.map { info ->
            val call = info.call
            if (!info.lookupComplete) {
                cache.findInfo(call, call.state == DialerCallState.INCOMING, LookupCallback(this))
            }
            val contact = info.contact
            val name = call.updateNameIfRestricted(preferences.getDisplayName(contact.namePrimary, contact.nameAlternative))
            ConferenceParticipant(
                callId = call.id,
                name = name?.takeIf { it.isNotEmpty() },
                number = contact.number?.takeIf { it.isNotEmpty() },
                photoUri = contact.displayPhotoUri?.toString(),
                colorKey = contact.lookupKey ?: contact.number.orEmpty(),
                onHold = call.nonConferenceState == DialerCallState.ONHOLD,
                canSeparate = parentCanSeparate && call.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
                canDisconnect = call.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE),
            )
        }
    }

    /** Holds the participants weakly, as the contact cache may answer after the screen is gone. */
    private class LookupCallback(participants: ConferenceParticipants) : ContactInfoCache.ContactInfoCacheCallback {
        private val participants = WeakReference(participants)

        override fun onContactInfoComplete(callId: String, entry: ContactCacheEntry) {
            participants.get()?.updateContact(callId, entry)
        }

        override fun onImageLoadComplete(callId: String, entry: ContactCacheEntry) {
            participants.get()?.updateContact(callId, entry)
        }
    }
}

/** The manage conference screen: everyone in the call, with buttons to split them out or hang up on them. */
@Composable
fun ConferenceManagerScreen(
    participants: List<ConferenceParticipant>,
    onSeparate: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PvotTopBar(title = stringResource(R.string.manageConferenceLabel), onBack = onBack)
        PvotScreen(
            modifier = Modifier.fillMaxSize().testTag(TestTags.CONFERENCE_LIST),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            items(participants, key = { it.callId }) { ParticipantRow(it, onSeparate, onDisconnect) }
        }
    }
}

@Composable
private fun ParticipantRow(participant: ConferenceParticipant, onSeparate: (String) -> Unit, onDisconnect: (String) -> Unit) {
    val number = participant.number?.let {
        PhoneNumberUtils.createTtsSpannable(BidiFormatter.getInstance().unicodeWrap(it, TextDirectionHeuristics.LTR)).toString()
    }
    val onHold = stringResource(R.string.notification_on_hold)
    val dim = if (participant.onHold) Modifier.alpha(DimmedAlpha) else Modifier
    PvotListRow(
        title = {
            Text(
                text = participant.name ?: number.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = dim,
            )
        },
        modifier = Modifier.testTag(TestTags.conferenceParticipant(participant.callId)),
        supportingText = listOfNotNull(onHold.takeIf { participant.onHold }, number.takeIf { participant.name != null })
            .takeIf { it.isNotEmpty() }
            ?.let { parts -> { Text(parts.joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leading = {
            ContactAvatar(
                name = participant.name ?: participant.number.orEmpty(),
                photoUri = participant.photoUri,
                colorKey = participant.colorKey,
                modifier = dim,
            )
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.XS)) {
                if (participant.canSeparate) {
                    IconButton(onClick = { onSeparate(participant.callId) }) {
                        Icon(painterResource(R.drawable.ic_arrows_split), stringResource(R.string.goPrivate))
                    }
                }
                if (participant.canDisconnect) {
                    IconButton(onClick = { onDisconnect(participant.callId) }) {
                        Icon(
                            painterResource(R.drawable.ic_phone_off),
                            stringResource(R.string.conference_caller_disconnect_content_description),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}

/** How faint a participant on hold looks. */
private const val DimmedAlpha = 0.38f

