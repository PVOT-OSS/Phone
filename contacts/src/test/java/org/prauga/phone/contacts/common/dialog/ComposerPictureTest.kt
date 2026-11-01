/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerPictureTest {

    /** A pretend encoder whose output shrinks with the side and the quality. */
    private val tries = mutableListOf<Pair<Int, Int>>()
    private val encode: (Int, Int) -> ByteArray = { side, quality ->
        tries += side to quality
        ByteArray(side * quality / 10)
    }

    @Test
    fun smallEnoughPictureKeepsItsSizeAndBestQuality() {
        val bytes = ComposerPicture.fitUnder(100_000, 1024, encode)
        assertEquals(1024 * 85 / 10, bytes?.size)
        assertEquals(listOf(1024 to 85), tries)
    }

    @Test
    fun lowersTheQualityBeforeTheSize() {
        // 1024 px fits only at quality 55.
        ComposerPicture.fitUnder(6_000, 1024, encode)
        assertEquals(listOf(1024 to 85, 1024 to 70, 1024 to 55), tries)
    }

    @Test
    fun shrinksWhenNoQualityFits() {
        val bytes = ComposerPicture.fitUnder(3_000, 1024, encode)
        // 1024 → 768 → 576: 576 * 55 / 10 = 3168 is too big, 576 * 40 / 10 = 2304 fits.
        assertEquals(2304, bytes?.size)
        assertEquals(576 to 40, tries.last())
    }

    @Test
    fun givesUpBelowTheSmallestUsefulSize() {
        assertNull(ComposerPicture.fitUnder(10, 1024, encode))
        check(tries.all { it.first >= 160 }) { "tried a picture smaller than 160 px: $tries" }
    }
}
