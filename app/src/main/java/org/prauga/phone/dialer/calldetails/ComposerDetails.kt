/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.content.Context
import org.prauga.phone.dialer.R

/** What was sent with a call through call composer, in the call's details. */
object ComposerDetails {

    /** "Urgent", the note, or both; null when the call carried neither. */
    @JvmStatic
    fun describe(context: Context, isUrgent: Boolean, note: String?): String? =
        when {
            isUrgent && note != null ->
                context.getString(R.string.call_details_composer_urgent_note, note)
            isUrgent -> context.getString(R.string.call_details_composer_urgent)
            note != null -> context.getString(R.string.call_details_composer_note, note)
            else -> null
        }
}
