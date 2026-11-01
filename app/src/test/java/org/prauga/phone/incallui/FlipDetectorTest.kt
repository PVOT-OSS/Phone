/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipDetectorTest {

    private val faceUp = Triple(0f, 0f, 9.8f)
    private val faceDown = Triple(0f, 0f, -9.8f)

    /** Feeds [reading] every 20 ms for [millis], starting at [start]; returns whether it fired. */
    private fun FlipDetector.hold(reading: Triple<Float, Float, Float>, start: Long, millis: Long): Boolean {
        var fired = false
        var t = start
        while (t <= start + millis) {
            fired = fired or onReading(reading.first, reading.second, reading.third, t)
            t += 20
        }
        return fired
    }

    @Test
    fun faceUpThenFaceDownFires() {
        val detector = FlipDetector()
        assertFalse(detector.hold(faceUp, 0, 500))
        assertTrue(detector.hold(faceDown, 520, 1000))
    }

    @Test
    fun startingFaceDownDoesNotFire() {
        assertFalse(FlipDetector().hold(faceDown, 0, 2000))
    }

    @Test
    fun aBriefFlipDoesNotFire() {
        val detector = FlipDetector()
        detector.hold(faceUp, 0, 500)
        assertFalse(detector.hold(faceDown, 520, 100))
        assertFalse(detector.hold(faceUp, 640, 500))
    }

    @Test
    fun movingAroundDoesNotFire() {
        val detector = FlipDetector()
        detector.hold(faceUp, 0, 500)
        var fired = false
        var t = 520L
        // Tilting back and forth while upside down: the phone is never still.
        while (t < 2000) {
            val tilt = if ((t / 20) % 2 == 0L) 3f else 0f
            fired = fired or detector.onReading(tilt, tilt, -9.8f, t)
            t += 20
        }
        assertFalse(fired)
    }

    @Test
    fun firesOnlyOnce() {
        val detector = FlipDetector()
        detector.hold(faceUp, 0, 500)
        assertTrue(detector.hold(faceDown, 520, 1000))
        assertFalse(detector.hold(faceDown, 1540, 1000))
    }
}
