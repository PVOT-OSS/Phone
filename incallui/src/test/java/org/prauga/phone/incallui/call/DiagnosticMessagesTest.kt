/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticMessagesTest {

    private val messages = DiagnosticMessages()

    @Test
    fun showsTheNewestMessage() {
        assertTrue(messages.show(1, "Weak signal"))
        assertTrue(messages.show(2, "Wi-Fi lost"))
        assertEquals("Wi-Fi lost", messages.latest())
    }

    @Test
    fun clearingTheNewestShowsThePreviousOne() {
        messages.show(1, "Weak signal")
        messages.show(2, "Wi-Fi lost")
        assertTrue(messages.clear(2))
        assertEquals("Weak signal", messages.latest())
    }

    @Test
    fun clearingAnOlderOneChangesNothingShown() {
        messages.show(1, "Weak signal")
        messages.show(2, "Wi-Fi lost")
        assertFalse(messages.clear(1))
        assertEquals("Wi-Fi lost", messages.latest())
    }

    @Test
    fun showingAnIdAgainMakesItTheNewest() {
        messages.show(1, "Weak signal")
        messages.show(2, "Wi-Fi lost")
        assertTrue(messages.show(1, "Weak signal"))
        assertEquals("Weak signal", messages.latest())
    }

    @Test
    fun blankMessagesAreIgnored() {
        assertFalse(messages.show(1, " "))
        assertNull(messages.latest())
    }

    @Test
    fun clearingEverythingShowsNothing() {
        messages.show(1, "Weak signal")
        assertTrue(messages.clear(1))
        assertNull(messages.latest())
    }
}
