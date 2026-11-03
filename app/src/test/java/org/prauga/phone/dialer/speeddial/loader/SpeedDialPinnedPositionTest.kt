/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial.loader

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntryDatabaseHelper
import org.prauga.phone.dialer.speeddial.speedDialItem
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Saving the favorites' order survives a contacts provider that rejects the pinned positions. */
@RunWith(RobolectricTestRunner::class)
class SpeedDialPinnedPositionTest {

    /** A contacts provider that refuses every batch, as vivo's does with "Empty values". */
    class RejectingContactsProvider : ContentProvider() {
        var batches = 0

        override fun applyBatch(
            operations: ArrayList<ContentProviderOperation>
        ): Array<ContentProviderResult> {
            batches++
            throw IllegalArgumentException("Empty values")
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            p: Array<String>?,
            s: String?,
            a: Array<String>?,
            o: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0

        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
    }

    @Test
    fun aRejectedPinIsLoggedNotThrown() {
        val provider =
            Robolectric.setupContentProvider(
                RejectingContactsProvider::class.java,
                ContactsContract.AUTHORITY,
            )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mutator =
            SpeedDialUiItemMutator(context, MoreExecutors.newDirectExecutorService(), null, null)
        val database = SpeedDialEntryDatabaseHelper(context)
        // The favorites as they are saved, so the speed dial database has rows to reorder.
        val items =
            listOf(speedDialItem(1, "Ada"), speedDialItem(2, "Grace"))
                .map { item ->
                    val id =
                        database.insert(item.buildSpeedDialEntry().toBuilder().setId(null).build())
                    item.toBuilder().setSpeedDialEntryId(id).build()
                }
                .reversed()
        var failure: Throwable? = null
        // The method asserts it runs off the main thread, as it does in the app.
        val worker = Thread {
            try {
                mutator.updatePinnedPosition(items)
            } catch (t: Throwable) {
                failure = t
            }
        }
        worker.start()
        worker.join()
        assertThat(failure).isNull()
        assertThat(provider.batches).isEqualTo(1)
        // The new order is still kept in the app's own database.
        val positions =
            database.allEntries.associate { it.contactId() to it.pinnedPosition().get() }
        assertThat(positions).containsExactly(2L, 0, 1L, 1)
    }
}
