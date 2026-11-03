/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.location

import kotlin.math.floor

/**
 * Plus Codes (Open Location Code) for a place: a short code that names a spot about 14 m across,
 * which emergency services can use where there is no street address.
 */
object OpenLocationCode {

    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val BASE = 20
    private const val PAIRS = 5

    /** Units of the smallest pair, 1/8000 of a degree. */
    private const val UNITS_PER_DEGREE = 8000L

    /** The ten digit code for [latitude], [longitude], such as "8FVC2222+22". */
    @JvmStatic
    fun encode(latitude: Double, longitude: Double): String {
        val maxLatitudeUnits = 180 * UNITS_PER_DEGREE - 1
        var lat =
            floor((latitude.coerceIn(-90.0, 90.0) + 90) * UNITS_PER_DEGREE)
                .toLong()
                .coerceAtMost(maxLatitudeUnits)
        var lng = floor((normalizeLongitude(longitude) + 180) * UNITS_PER_DEGREE).toLong()

        val latDigits = CharArray(PAIRS)
        val lngDigits = CharArray(PAIRS)
        for (i in PAIRS - 1 downTo 0) {
            latDigits[i] = ALPHABET[(lat % BASE).toInt()]
            lngDigits[i] = ALPHABET[(lng % BASE).toInt()]
            lat /= BASE
            lng /= BASE
        }
        val code = StringBuilder()
        for (i in 0 until PAIRS) {
            code.append(latDigits[i]).append(lngDigits[i])
            if (i == 3) code.append('+')
        }
        return code.toString()
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var lng = longitude
        while (lng < -180) lng += 360
        while (lng >= 180) lng -= 360
        return lng
    }
}
