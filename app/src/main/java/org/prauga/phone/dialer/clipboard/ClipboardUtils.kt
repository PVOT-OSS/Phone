/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import org.prauga.phone.dialer.R

/** Copies provided label and text to the clipboard and optionally shows a "text copied" toast. */
object ClipboardUtils {

    /**
     * Copy a text to clipboard.
     *
     * @param context Context
     * @param label Label to show to the user describing this clip.
     * @param text Text to copy.
     * @param showToast If `true`, a toast is shown to the user.
     */
    @JvmStatic
    fun copyText(context: Context, label: CharSequence?, text: CharSequence?, showToast: Boolean) {
        if (text.isNullOrEmpty()) {
            return
        }

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label ?: "", text)
        clipboardManager.setPrimaryClip(clipData)

        if (showToast) {
            val toastText = context.getString(R.string.toast_text_copied)
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
    }
}
