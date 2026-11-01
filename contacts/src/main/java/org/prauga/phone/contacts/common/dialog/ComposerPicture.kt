/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.dialog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Makes a picture small enough for call composer, which caps pictures at a few tens of kilobytes. */
object ComposerPicture {

    /** Picture qualities tried at each size, best first. */
    private val QUALITIES = listOf(85, 70, 55, 40)

    /** The longest side the picture starts from; the network shows it on a phone screen. */
    private const val START_SIDE = 1024

    /** Below this the picture isn't worth sending. */
    private const val MIN_SIDE = 160

    /**
     * Reads [picture] and encodes it as a JPEG of at most [maxBytes], shrinking it as needed; null
     * if it can't be read or made small enough. Slow: call it off the main thread.
     */
    fun shrink(context: Context, picture: Uri, maxBytes: Long): ByteArray? {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, picture)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > START_SIDE) {
                    val scale = START_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
                // Software bitmaps can be scaled and compressed.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return null
        return fitUnder(maxBytes, max(bitmap.width, bitmap.height)) { side, quality ->
            val scale = side.toFloat() / max(bitmap.width, bitmap.height)
            val scaled = if (scale >= 1f) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
            }
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        }
    }

    /**
     * Tries [encode] at the picture's [longestSide] with falling qualities, then at smaller sizes,
     * and returns the first result of at most [maxBytes], or null.
     */
    internal fun fitUnder(maxBytes: Long, longestSide: Int, encode: (side: Int, quality: Int) -> ByteArray): ByteArray? {
        var side = longestSide
        while (side >= MIN_SIDE) {
            for (quality in QUALITIES) {
                val bytes = encode(side, quality)
                if (bytes.size <= maxBytes) return bytes
            }
            side = side * 3 / 4
        }
        return null
    }
}
