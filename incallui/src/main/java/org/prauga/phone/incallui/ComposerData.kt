/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.content.Context
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.multimedia.MultimediaData
import org.prauga.phone.incallui.call.DialerCall

/**
 * What a caller sent with call composer, for the call screen: whether the call is urgent and the
 * picture. The subject already shows in the call's status line, so it isn't repeated here.
 */
object ComposerData {

    /** The call's composer data, or null when the caller sent nothing worth showing. */
    @JvmStatic
    fun of(context: Context, call: DialerCall): MultimediaData? {
        val urgent = call.isUrgent
        val picture = call.composerPictureUri
        if (!urgent && picture == null) return null
        val builder = MultimediaData.builder().setImportant(urgent)
        if (picture != null) {
            val type = runCatching { context.contentResolver.getType(picture) }
                .onFailure { LogUtil.e("ComposerData.of", "can't read the picture's type", it) }
                .getOrNull()
            builder.setImage(picture, type ?: DEFAULT_PICTURE_TYPE)
        }
        return builder.build()
    }

    private const val DEFAULT_PICTURE_TYPE = "image/*"
}
