/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.rtt.impl

import android.telecom.CallAudioState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.rtt.RttTranscript
import org.prauga.phone.dialer.rtt.RttTranscriptMessage
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo
import org.prauga.phone.incallui.rtt.protocol.RttChatMessage

/** One bubble of the RTT chat as the screen shows it. */
data class RttBubble(val content: String, val isRemote: Boolean)

/**
 * The RTT conversation as Compose state. It keeps the messages the way the old list adapter did:
 * the local message being typed grows character by character, remote text is merged in as it
 * arrives, and the chat can be saved to and restored from a transcript.
 */
class RttChatMessages {

    private var messages: MutableList<RttChatMessage> = ArrayList()
    private var lastIndexOfLocalMessage = -1

    var bubbles by mutableStateOf<List<RttBubble>>(emptyList())
        private set

    /** Whether the note that the other side sees our typing shows above the messages. */
    var showAdvisory by mutableStateOf(false)
        private set

    /** Bumped when our own text changed; the list then scrolls to the end. */
    var localUpdates by mutableIntStateOf(0)
        private set

    /** Bumped when the other side's text changed; the list follows only if it is at the end. */
    var remoteUpdates by mutableIntStateOf(0)
        private set

    fun showAdvisory() {
        showAdvisory = true
    }

    /** Adds what was typed to the message being written, starting one if needed. */
    fun addLocalMessage(newMessage: String) {
        val current = messages.getOrNull(lastIndexOfLocalMessage)
        if (current == null || current.isFinished) {
            messages += RttChatMessage().apply { append(newMessage) }
            lastIndexOfLocalMessage = messages.lastIndex
        } else {
            current.append(newMessage)
            if (current.content.isEmpty()) {
                messages.removeAt(lastIndexOfLocalMessage)
                lastIndexOfLocalMessage = -1
            }
        }
        publish()
        localUpdates++
    }

    /** Ends the message being written, so the next keystroke starts a new bubble. */
    fun submitLocalMessage() {
        LogUtil.enterBlock("RttChatMessages.submitLocalMessage")
        messages[lastIndexOfLocalMessage].finish()
        lastIndexOfLocalMessage = -1
        publish()
    }

    /** What changed between the message being written and the input's new text. */
    fun computeChangeOfLocalMessage(newMessage: String): String {
        val current = messages.getOrNull(lastIndexOfLocalMessage)
        return if (current == null || current.isFinished) {
            newMessage
        } else {
            RttChatMessage.computeChangedString(current.content, newMessage)
        }
    }

    fun addRemoteMessage(message: String?) {
        if (message.isNullOrEmpty()) return
        RttChatMessage.updateRemoteRttChatMessage(messages, message)
        lastIndexOfLocalMessage = RttChatMessage.getLastIndexLocalMessage(messages)
        publish()
        remoteUpdates++
    }

    /** Reopens our last message for editing and returns its text, or null when there is none. */
    fun retrieveLastLocalMessage(): String? {
        lastIndexOfLocalMessage = RttChatMessage.getLastIndexLocalMessage(messages)
        val message = messages.getOrNull(lastIndexOfLocalMessage) ?: return null
        message.unfinish()
        return message.content
    }

    /**
     * Restores the chat after the screen came back; returns our unfinished message, if any. A call
     * that has said nothing yet has no [transcript].
     */
    fun onRestoreRttChat(transcript: RttTranscript?): String? {
        LogUtil.enterBlock("RttChatMessages.onRestoreRttChat")
        messages = RttChatMessage.fromTranscript(transcript)
        lastIndexOfLocalMessage = RttChatMessage.getLastIndexLocalMessage(messages)
        publish()
        val message = messages.getOrNull(lastIndexOfLocalMessage) ?: return null
        return if (message.isFinished) null else message.content
    }

    fun getRttTranscriptMessageList(): List<RttTranscriptMessage> =
        RttChatMessage.toTranscriptMessageList(messages)

    private fun publish() {
        bubbles = messages.map { RttBubble(it.content, it.isRemote) }
    }
}

/** Everything around the RTT chat that the call drives: who, how long, banners and the menus. */
class RttChatUiState {
    var name by mutableStateOf("")
    var photoUri by mutableStateOf<String?>(null)
    var colorKey by mutableStateOf("")

    /** When the call connected, in wall-clock millis, or null before it is active. */
    var connectTimeMillis by mutableStateOf<Long?>(null)

    /** "Waiting for … to join RTT call", or null. */
    var statusBanner by mutableStateOf<String?>(null)

    /** The call on hold, or null when there is none to show. */
    var secondary by mutableStateOf<SecondaryInfo?>(null)

    var inputVisible by mutableStateOf(false)
    var submitEnabled by mutableStateOf(false)

    var menuOpen by mutableStateOf(false)
    var audioMenuOpen by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var dialpadShowing by mutableStateOf(false)
    var audioState by mutableStateOf<CallAudioState?>(null)
    var swapEnabled by mutableStateOf(false)
    var switchToSecondaryEnabled by mutableStateOf(false)

    /**
     * An emergency call, which offers ready messages for the fire department, ambulance and police.
     */
    var emergency by mutableStateOf(false)
}
