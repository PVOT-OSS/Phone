/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.call

import android.os.Bundle
import android.telecom.Call

/**
 * Messages the carrier asks to show during a call, such as a warning about poor coverage. Each has
 * an id so the carrier can clear it later; the newest one still showing is the one to display.
 */
class DiagnosticMessages {

    private val messages = LinkedHashMap<Int, CharSequence>()

    /** Handles a connection event; returns whether the message to show changed. */
    fun onEvent(event: String, extras: Bundle?): Boolean {
        if (extras == null || !extras.containsKey(Call.EXTRA_DIAGNOSTIC_MESSAGE_ID)) return false
        val id = extras.getInt(Call.EXTRA_DIAGNOSTIC_MESSAGE_ID)
        return when (event) {
            Call.EVENT_DISPLAY_DIAGNOSTIC_MESSAGE -> show(id, extras.getCharSequence(Call.EXTRA_DIAGNOSTIC_MESSAGE))
            Call.EVENT_CLEAR_DIAGNOSTIC_MESSAGE -> clear(id)
            else -> false
        }
    }

    /** Shows [message] under [id]; returns whether the message to show changed. */
    fun show(id: Int, message: CharSequence?): Boolean {
        if (message.isNullOrBlank()) return false
        val before = latest()
        // Showing an id again moves it to the end, so it becomes the newest.
        messages.remove(id)
        messages[id] = message
        return latest() != before
    }

    /** Clears the message under [id]; returns whether the message to show changed. */
    fun clear(id: Int): Boolean {
        val before = latest()
        messages.remove(id)
        return latest() != before
    }

    /** The newest message still showing, or null. */
    fun latest(): CharSequence? = messages.values.lastOrNull()
}
