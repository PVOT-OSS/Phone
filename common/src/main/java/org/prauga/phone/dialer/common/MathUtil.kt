/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.common

import kotlin.math.max
import kotlin.math.min

/** Utility class for common math operations */
object MathUtil {

    /**
     * Interpolates between two integer values based on percentage.
     *
     * @param begin Begin value
     * @param end End value
     * @param percent Percentage value, between 0 and 1
     * @return Interpolated result
     */
    @JvmStatic
    fun lerp(begin: Int, end: Int, percent: Float): Int = (begin * (1 - percent) + end * percent).toInt()

    /**
     * Interpolates between two float values based on percentage.
     *
     * @param begin Begin value
     * @param end End value
     * @param percent Percentage value, between 0 and 1
     * @return Interpolated result
     */
    @JvmStatic
    fun lerp(begin: Float, end: Float, percent: Float): Float = begin * (1 - percent) + end * percent

    /**
     * Clamps a value between two bounds inclusively.
     *
     * @param value Value to be clamped
     * @param min Lower bound
     * @param max Upper bound
     * @return Clamped value
     */
    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float = max(min, min(value, max))
}
