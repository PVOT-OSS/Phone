/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.preferredsim

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import androidx.annotation.WorkerThread
import org.prauga.phone.dialer.preferredsim.PreferredSimFallbackContract.PreferredSim

/**
 * The SIM a contact's number is always called with, which "Remember this choice" in the SIM picker
 * saves. Each phone number row of the contact keeps its own; these read and change them together.
 */
object ContactSim {

    /** The contact's phone number rows for [number], by contacts lookup key. */
    @JvmStatic
    @WorkerThread
    fun dataIds(context: Context, lookupKey: String, number: String): List<String> =
        context.contentResolver
            .query(
                Phone.CONTENT_URI,
                arrayOf(Phone._ID, Phone.NUMBER),
                "${Phone.LOOKUP_KEY} = ?",
                arrayOf(lookupKey),
                null,
            )
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        if (PhoneNumberUtils.areSamePhoneNumber(cursor.getString(1), number, "")) {
                            add(cursor.getLong(0).toString())
                        }
                    }
                }
            }
            .orEmpty()

    /** The SIM saved for any of [dataIds], or null when calls ask every time. */
    @JvmStatic
    @WorkerThread
    fun get(context: Context, dataIds: List<String>): PhoneAccountHandle? {
        if (dataIds.isEmpty()) return null
        return context.contentResolver
            .query(
                PreferredSimFallbackContract.CONTENT_URI,
                arrayOf(
                    PreferredSim.PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME,
                    PreferredSim.PREFERRED_PHONE_ACCOUNT_ID,
                ),
                "${PreferredSim.DATA_ID} IN (${dataIds.joinToString(",") { "?" }})",
                dataIds.toTypedArray(),
                null,
            )
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val component = cursor.getString(0)?.let(ComponentName::unflattenFromString)
                val id = cursor.getString(1)
                if (component == null || id == null) null else PhoneAccountHandle(component, id)
            }
    }

    /** Saves [account] for [dataIds], or with null forgets the choice so calls ask again. */
    @JvmStatic
    @WorkerThread
    fun set(context: Context, dataIds: List<String>, account: PhoneAccountHandle?) {
        val resolver = context.contentResolver
        for (dataId in dataIds) {
            if (account == null) {
                resolver.delete(
                    PreferredSimFallbackContract.CONTENT_URI,
                    "${PreferredSim.DATA_ID} = ?",
                    arrayOf(dataId),
                )
            } else {
                val values =
                    ContentValues().apply {
                        put(
                            PreferredSim.PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME,
                            account.componentName.flattenToString(),
                        )
                        put(PreferredSim.PREFERRED_PHONE_ACCOUNT_ID, account.id)
                    }
                resolver.update(
                    PreferredSimFallbackContract.CONTENT_URI,
                    values,
                    "${PreferredSim.DATA_ID} = ?",
                    arrayOf(dataId),
                )
            }
        }
        resolver.notifyChange(PreferredSimFallbackContract.CONTENT_URI, null)
    }
}
