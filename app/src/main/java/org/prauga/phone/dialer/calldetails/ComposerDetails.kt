/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.calldetails.CallDetailsEntries.CallDetailsEntry

/** What was sent with a call through call composer, in the call's details. */
object ComposerDetails {

    /** "Urgent", the note, or both; null when the call carried neither. */
    @JvmStatic
    fun describe(context: Context, entry: CallDetailsEntry): CharSequence? {
        val note = entry.subject.takeIf { it.isNotBlank() }
        return when {
            entry.isUrgent && note != null -> context.getString(R.string.call_details_composer_urgent_note, note)
            entry.isUrgent -> context.getString(R.string.call_details_composer_urgent)
            note != null -> context.getString(R.string.call_details_composer_note, note)
            else -> null
        }
    }

    /** Shows the picture sent with the call in [view], or hides it. */
    @JvmStatic
    fun showPicture(view: ImageView, entry: CallDetailsEntry) {
        val picture = entry.composerPictureUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (picture == null) {
            Glide.with(view).clear(view)
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        Glide.with(view).load(picture).into(view)
    }
}
