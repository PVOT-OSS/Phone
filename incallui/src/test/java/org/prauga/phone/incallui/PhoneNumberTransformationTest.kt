/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The number prompt shows a typed number formatted, and the cursor keeps its place in it. */
@RunWith(RobolectricTestRunner::class)
class PhoneNumberTransformationTest {

    private val us = PhoneNumberTransformation("US")

    @Test
    fun formatsAsTyped() {
        assertEquals("(650) 253-0000", shown("6502530000"))
        assertEquals("650-2", shown("6502"))
    }

    @Test
    fun cursorFollowsTheDigits() {
        val mapping = us.filter(AnnotatedString("6502530000")).offsetMapping

        assertEquals(0, mapping.originalToTransformed(0))
        // After "650": "(650"
        assertEquals(4, mapping.originalToTransformed(3))
        assertEquals(14, mapping.originalToTransformed(10))
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(10, mapping.transformedToOriginal(14))
    }

    @Test
    fun aSeparatorTypedByHandStopsFormatting() {
        assertEquals("650 253", shown("650 253"))
    }

    private fun shown(raw: String): String = us.filter(AnnotatedString(raw)).text.text
}
