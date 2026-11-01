/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.searchfragment.list

import android.content.Context
import android.database.MatrixCursor
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.searchfragment.common.Projections
import org.prauga.phone.dialer.searchfragment.common.SearchCursor
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Turning the search cursors into rows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class SearchResultsTest {

    /** A contacts cursor whose first row is its "Contacts" header. */
    private class FakeContactsCursor : MatrixCursor(Projections.CP2_PROJECTION), SearchCursor {
        override fun isHeader() = position == 0

        override fun updateQuery(query: String) = false

        override fun getDirectoryId() = 0L
    }

    private fun contacts(vararg rows: Triple<String, String, String>) =
        FakeContactsCursor().apply {
            addRow(arrayOfNulls<Any?>(Projections.CP2_PROJECTION.size).also { it[0] = "Contacts" })
            rows.forEachIndexed { i, (name, number, key) ->
                addRow(
                    arrayOfNulls<Any?>(Projections.CP2_PROJECTION.size).also {
                        it[Projections.ID] = i + 1L
                        it[Projections.PHONE_TYPE] = Phone.TYPE_MOBILE
                        it[Projections.PHONE_NUMBER] = number
                        it[Projections.DISPLAY_NAME] = name
                        it[Projections.LOOKUP_KEY] = key
                        it[Projections.CARRIER_PRESENCE] =
                            if (number.endsWith("9")) Phone.CARRIER_PRESENCE_VT_CAPABLE else 0
                    }
                )
            }
        }

    private val results = SearchResults(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun contactsComeUnderTheirHeader() {
        results.setQuery("ada", null)
        results.setContactsCursor(contacts(Triple("Ada Lovelace", "+15550100", "a")))

        val rows = results.rows
        assertThat((rows[0] as SearchRow.Header).text).isEqualTo("Contacts")
        val ada = rows[1] as SearchRow.Number
        assertThat(ada.name?.text).isEqualTo("Ada Lovelace")
        assertThat(ada.details.text).isEqualTo("Mobile +15550100")
    }

    @Test
    fun theMatchIsBold() {
        results.setQuery("ada", null)
        results.setContactsCursor(contacts(Triple("Ada Lovelace", "+15550100", "a")))

        val name = (results.rows[1] as SearchRow.Number).name!!
        val bold = name.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertThat(bold.map { name.text.substring(it.start, it.end) }).containsExactly("Ada")
    }

    @Test
    fun aContactsSecondNumberHasNoName() {
        results.setQuery("a", null)
        results.setContactsCursor(
            contacts(
                Triple("Ada Lovelace", "+15550100", "a"),
                Triple("Ada Lovelace", "+15550109", "a"),
            )
        )

        val numbers = results.rows.filterIsInstance<SearchRow.Number>()
        assertThat(numbers.map { it.name != null }).containsExactly(true, false).inOrder()
        assertThat(numbers.map { it.canVideoCall }).containsExactly(false, true).inOrder()
    }

    @Test
    fun actionsFollowTheResultsWithTheTypedNumber() {
        results.setQuery("5550100", "555-0100")
        results.setSearchActions(listOf(SearchAction.CREATE_NEW_CONTACT, SearchAction.SEND_SMS))

        val actions = results.rows.filterIsInstance<SearchRow.Action>()
        assertThat(actions.map { it.action })
            .containsExactly(SearchAction.CREATE_NEW_CONTACT, SearchAction.SEND_SMS)
            .inOrder()
        assertThat(actions.first().number).isEqualTo("555-0100")
    }

    @Test
    fun theLocationRequestComesFirst() {
        results.setContactsCursor(contacts(Triple("Ada Lovelace", "+15550100", "a")))
        results.showLocationPermissionRequest(true)
        assertThat(results.rows.first()).isEqualTo(SearchRow.LocationRequest)
    }
}
