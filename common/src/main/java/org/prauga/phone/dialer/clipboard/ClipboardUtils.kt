/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2025 The LineageOS Project
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
 * limitations under the License
 */

package org.prauga.phone.dialer.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import org.prauga.phone.common.R

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

        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label ?: "", text)
        clipboardManager.setPrimaryClip(clipData)

        if (showToast) {
            val toastText = context.getString(R.string.toast_text_copied)
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
    }
}
