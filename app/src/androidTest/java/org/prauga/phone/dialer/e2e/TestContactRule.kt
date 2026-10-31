/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Adds contacts for a test and deletes them afterwards. */
class TestContactRule : ExternalResource() {

    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val added = mutableListOf<Long>()

    fun add(name: String, number: String) {
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build(),
        )
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
        added += checkNotNull(results[0].uri).lastPathSegment!!.toLong()
    }

    override fun after() {
        for (id in added) {
            val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendPath(id.toString())
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build()
            resolver.delete(uri, null, null)
        }
        added.clear()
    }
}
