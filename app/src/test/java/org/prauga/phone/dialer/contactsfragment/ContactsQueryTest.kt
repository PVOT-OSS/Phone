/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.contactsfragment

import android.database.MatrixCursor
import android.os.Bundle
import android.provider.ContactsContract.Contacts
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Grouping contacts under index letters, and their URIs. */
@RunWith(RobolectricTestRunner::class)
class ContactsQueryTest {

    private fun cursor(vararg names: String, titles: Array<String>? = null, counts: IntArray? = null) =
        MatrixCursor(arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI, Contacts.LOOKUP_KEY))
            .apply {
                names.forEachIndexed { i, name -> addRow(arrayOf<Any?>(i.toLong() + 1, name, null, "key$i")) }
                if (titles != null) {
                    extras = Bundle().apply {
                        putStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES, titles)
                        putIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS, counts)
                    }
                }
            }

    private fun List<ContactSection>.shape() = map { it.label to it.contacts.map(ContactEntry::name) }

    @Test
    fun groupsByTheProvidersIndex() {
        val sections = ContactsQuery.map(
            cursor("Ada", "Alan", "Grace", "Linus", titles = arrayOf("A", "G", "L"), counts = intArrayOf(2, 1, 1)))

        assertThat(sections.shape()).containsExactly(
            "A" to listOf("Ada", "Alan"), "G" to listOf("Grace"), "L" to listOf("Linus")).inOrder()
    }

    @Test
    fun skipsEmptyIndexSections() {
        val sections = ContactsQuery.map(
            cursor("Ada", "Zoe", titles = arrayOf("A", "B", "Z"), counts = intArrayOf(1, 0, 1)))

        assertThat(sections.map { it.label }).containsExactly("A", "Z").inOrder()
    }

    @Test
    fun keepsTheProvidersLocaleLabels() {
        // The provider groups Cyrillic and other scripts by the device locale; its labels win.
        val sections = ContactsQuery.map(
            cursor("Анна", "Борис", titles = arrayOf("А", "Б"), counts = intArrayOf(1, 1)))

        assertThat(sections.map { it.label }).containsExactly("А", "Б").inOrder()
    }

    @Test
    fun groupsByFirstLetterWithoutAnIndex() {
        val sections = ContactsQuery.map(cursor("ada", "Ali", "Ярослав", "007", "+1 555"))

        assertThat(sections.shape()).containsExactly(
            "A" to listOf("ada", "Ali"), "Я" to listOf("Ярослав"), "#" to listOf("007", "+1 555")).inOrder()
    }

    @Test
    fun rowsPastTheIndexFallBackToTheirLetter() {
        val sections = ContactsQuery.map(
            cursor("Ada", "Bea", "Cy", titles = arrayOf("A"), counts = intArrayOf(1)))

        assertThat(sections.map { it.label }).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun emptyCursorHasNoSections() {
        assertThat(ContactsQuery.map(cursor())).isEmpty()
    }

    @Test
    fun contactUriPrefersTheLookupKey() {
        assertThat(ContactEntry(7, "abc", "Ada", null).contactUri.toString())
            .isEqualTo("content://com.android.contacts/contacts/lookup/abc/7")
        assertThat(ContactEntry(7, null, "Ada", null).contactUri.toString())
            .isEqualTo("content://com.android.contacts/contacts/7")
    }

    @Test
    fun scrollSectionsPointAtEachHeader() {
        val sections = listOf(
            ContactSection("A", listOf(ContactEntry(1, null, "Ada", null), ContactEntry(2, null, "Alan", null))),
            ContactSection("B", listOf(ContactEntry(3, null, "Bea", null))),
        )

        assertThat(scrollSectionsOf(sections, showAddContact = true).map { it.label to it.index })
            .containsExactly("A" to 1, "B" to 4).inOrder()
        assertThat(scrollSectionsOf(sections, showAddContact = false).map { it.index })
            .containsExactly(0, 3).inOrder()
    }
}
