/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.contactsfragment

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract.Contacts
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.contacts.displaypreference.ContactDisplayPreferences.DisplayOrder
import org.prauga.phone.dialer.contacts.displaypreference.ContactDisplayPreferences.SortOrder

/** One contact in the contacts tab. */
data class ContactEntry(
    val id: Long,
    val lookupKey: String?,
    val name: String,
    val photoUri: String?,
) {
    /** The contact's lookup URI, which survives contact joins, else its plain URI. */
    val contactUri: Uri
        get() = Contacts.getLookupUri(id, lookupKey) ?: ContentUris.withAppendedId(Contacts.CONTENT_URI, id)
}

/** Contacts that share an index letter, such as all names starting with "A". */
data class ContactSection(val label: String, val contacts: List<ContactEntry>)

/** Queries all contacts in the user's display and sort order, grouped by the address book index. */
object ContactsQuery {

    private const val ID = 0
    private const val DISPLAY_NAME = 1
    private const val PHOTO_URI = 2
    private const val LOOKUP_KEY = 3

    /** Loads every named contact, on the calling thread. */
    fun load(context: Context, hasPhoneNumbers: Boolean = false): List<ContactSection> {
        val preferences = ContactsComponent.get(context).contactDisplayPreferences()
        val nameColumn = when (preferences.displayOrder) {
            DisplayOrder.ALTERNATIVE -> Contacts.DISPLAY_NAME_ALTERNATIVE
            else -> Contacts.DISPLAY_NAME_PRIMARY
        }
        val sortColumn = when (preferences.sortOrder) {
            SortOrder.BY_ALTERNATIVE -> Contacts.SORT_KEY_ALTERNATIVE
            else -> Contacts.SORT_KEY_PRIMARY
        }
        val where = buildString {
            append("$nameColumn IS NOT NULL")
            if (hasPhoneNumbers) append(" AND ${Contacts.HAS_PHONE_NUMBER}=1")
        }
        val uri = Contacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
            .build()
        val projection = arrayOf(Contacts._ID, nameColumn, Contacts.PHOTO_THUMBNAIL_URI, Contacts.LOOKUP_KEY)
        return context.contentResolver.query(uri, projection, where, null, "$sortColumn ASC")
            ?.use(::map)
            .orEmpty()
    }

    /**
     * Groups the rows of [cursor] by the address book index the contacts provider puts in its
     * extras, which follows the device locale. Rows past the index, or every row when the provider
     * gives none, are grouped by their first letter.
     */
    fun map(cursor: Cursor): List<ContactSection> {
        val titles = cursor.extras?.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)
        val counts = cursor.extras?.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)
        if (titles != null && counts != null && counts.sum() != cursor.count) {
            LogUtil.e("ContactsQuery.map", "Count sum (%d) != cursor count (%d).", counts.sum(), cursor.count)
        }
        val sections = mutableListOf<ContactSection>()
        var label: String? = null
        var current = mutableListOf<ContactEntry>()
        var indexSection = 0
        var leftInSection = counts?.firstOrNull() ?: 0
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            val entry = ContactEntry(
                id = cursor.getLong(ID),
                lookupKey = cursor.getString(LOOKUP_KEY),
                name = cursor.getString(DISPLAY_NAME).orEmpty(),
                photoUri = cursor.getString(PHOTO_URI),
            )
            // Skip empty index sections before taking the next title.
            while (titles != null && counts != null && leftInSection == 0 && indexSection < counts.lastIndex) {
                leftInSection = counts[++indexSection]
            }
            val rowLabel = if (titles != null && leftInSection > 0 && indexSection < titles.size) {
                leftInSection--
                titles[indexSection]
            } else {
                firstLetter(entry.name)
            }
            if (rowLabel != label) {
                label?.let { sections += ContactSection(it, current) }
                label = rowLabel
                current = mutableListOf()
            }
            current += entry
        }
        label?.let { sections += ContactSection(it, current) }
        return sections
    }

    private fun firstLetter(name: String): String {
        val first = name.trim().codePoints().findFirst()
        if (!first.isPresent || !Character.isLetter(first.asInt)) return "#"
        return String(Character.toChars(first.asInt)).uppercase()
    }
}
