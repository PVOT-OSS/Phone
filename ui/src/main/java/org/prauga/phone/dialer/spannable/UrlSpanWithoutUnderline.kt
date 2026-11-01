/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.spannable

import android.text.TextPaint
import android.text.style.URLSpan

/** Version of [URLSpan] which doesn't underline links. */
internal class UrlSpanWithoutUnderline(url: String) : URLSpan(url) {

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
    }
}
