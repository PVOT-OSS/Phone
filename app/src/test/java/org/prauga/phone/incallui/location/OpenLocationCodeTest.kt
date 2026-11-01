/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLocationCodeTest {

    @Test
    fun knownCode() = assertEquals("8FVC2222+22", OpenLocationCode.encode(47.0000625, 8.0000625))

    @Test
    fun southWestCorner() = assertEquals("22222222+22", OpenLocationCode.encode(-90.0, -180.0))

    @Test
    fun northPoleStaysInRange() = assertTrue(OpenLocationCode.encode(90.0, 0.0).startsWith("C"))

    @Test
    fun longitudeWrapsAround() = assertEquals(OpenLocationCode.encode(10.0, -170.0), OpenLocationCode.encode(10.0, 190.0))

    @Test
    fun shape() {
        val code = OpenLocationCode.encode(12.9716, 77.5946)
        assertEquals(11, code.length)
        assertEquals('+', code[8])
        assertTrue(code.all { it == '+' || it in "23456789CFGHJMPQRVWX" })
    }

    @Test
    fun nearbyPointsShareTheirStart() =
        assertEquals(OpenLocationCode.encode(12.97160, 77.59460).take(8), OpenLocationCode.encode(12.97161, 77.59461).take(8))
}
