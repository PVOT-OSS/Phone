/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.widget

import android.content.Context
import android.provider.ContactsContract.Contacts
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.speeddial.speedDialItem
import org.robolectric.RobolectricTestRunner

/** The widget shows the starred contacts in their order, each with a picture and a call intent. */
@RunWith(RobolectricTestRunner::class)
class FavoritesWidgetDataTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun starredContactsBecomeTilesInPinnedOrder() {
        val tiles = FavoritesWidgetData.tiles(
            context,
            listOf(
                speedDialItem(1, "Ada Lovelace", pinned = 2),
                speedDialItem(2, "Grace Hopper", pinned = 1),
                speedDialItem(3, "Not Starred", starred = false),
            ),
        )

        assertThat(tiles.map { it.name }).containsExactly("Grace Hopper", "Ada Lovelace").inOrder()
        assertThat(tiles[0].intent.data).isEqualTo(Contacts.getLookupUri(2, "key2"))
        assertThat(tiles[0].intent.action).isEqualTo("org.prauga.phone.dialer.shortcuts.CALL_CONTACT")
        assertThat(tiles[0].photo.width).isGreaterThan(0)
    }

    @Test
    fun onlyTheFirstEightShow() {
        val many = (1L..12L).map { speedDialItem(it, "Contact $it", pinned = it.toInt()) }
        assertThat(FavoritesWidgetData.tiles(context, many)).hasSize(FavoritesWidgetData.MAX_TILES)
    }
}
