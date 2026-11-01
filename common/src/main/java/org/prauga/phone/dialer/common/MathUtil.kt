/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
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
    fun lerp(begin: Int, end: Int, percent: Float): Int =
        (begin * (1 - percent) + end * percent).toInt()

    /**
     * Interpolates between two float values based on percentage.
     *
     * @param begin Begin value
     * @param end End value
     * @param percent Percentage value, between 0 and 1
     * @return Interpolated result
     */
    @JvmStatic
    fun lerp(begin: Float, end: Float, percent: Float): Float =
        begin * (1 - percent) + end * percent

    /**
     * Clamps a value between two bounds inclusively.
     *
     * @param value Value to be clamped
     * @param min Lower bound
     * @param max Upper bound
     * @return Clamped value
     */
    @JvmStatic fun clamp(value: Float, min: Float, max: Float): Float = max(min, min(value, max))
}
