/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.Manifest
import android.content.ComponentName
import android.content.ContentProviderOperation
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.telecom.PhoneAccountHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.preferredsim.ContactSim

/** The SIM saved for a contact's number, as call details reads and changes it. */
@RunWith(AndroidJUnit4::class)
class ContactSimTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val number = CallTests.freshNumber()
    private var rawContactId = -1L

    @Before
    fun setUp() {
        for (permission in
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
        val ops =
            arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, "Sim Test")
                    .build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, number)
                    .build(),
            )
        val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        rawContactId = checkNotNull(results[0].uri).lastPathSegment!!.toLong()
    }

    @After
    fun tearDown() {
        context.contentResolver.delete(
            ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build(),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
        )
    }

    @Test
    fun aSavedSimIsReadBackAndCanBeForgotten() {
        val ids = ContactSim.dataIds(context, lookupKey(), number)
        assertEquals(1, ids.size)
        assertNull(ContactSim.get(context, ids))

        val sim = PhoneAccountHandle(ComponentName("com.android.phone", "Sim"), "2")
        ContactSim.set(context, ids, sim)
        assertEquals(sim, ContactSim.get(context, ids))

        ContactSim.set(context, ids, null)
        assertNull(ContactSim.get(context, ids))
    }

    private fun lookupKey(): String =
        checkNotNull(
            context.contentResolver
                .query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawContactId.toString()),
                    null,
                )
                ?.use { raw ->
                    raw.moveToFirst()
                    context.contentResolver
                        .query(
                            ContactsContract.Contacts.CONTENT_URI,
                            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                            "${ContactsContract.Contacts._ID} = ?",
                            arrayOf(raw.getLong(0).toString()),
                            null,
                        )
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }
        )
}
