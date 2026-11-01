/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.searchfragment.list

import android.content.Context
import android.graphics.Typeface
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.searchfragment.calllog.CallLogSearchCursor
import org.prauga.phone.dialer.common.cp2.DirectoryUtils
import org.prauga.phone.dialer.searchfragment.common.Projections
import org.prauga.phone.dialer.searchfragment.common.QueryBoldingUtil
import org.prauga.phone.dialer.searchfragment.common.SearchCursor

/** One row of the search results. */
sealed interface SearchRow {
    val key: String

    data class Header(override val key: String, val text: String) : SearchRow

    /**
     * A phone number of a contact, a directory contact or a nearby place.
     *
     * @param name The name, or null for a contact's second number, shown under the first
     * @param position The row's place, which call logging reports as its ranking
     */
    data class Number(
        override val key: String,
        val kind: Kind,
        val position: Int,
        val number: String,
        val name: AnnotatedString?,
        val details: AnnotatedString,
        val photoUri: String?,
        val colorKey: String,
        val canVideoCall: Boolean,
        val isWork: Boolean,
    ) : SearchRow

    data class Action(override val key: String, @SearchAction val action: Int, val position: Int, val number: String) : SearchRow

    data object LocationRequest : SearchRow {
        override val key = "location_request"
    }

    /** Where a number comes from; [CALL_LOG] is a number from the call history that isn't a contact. */
    enum class Kind { CONTACT, DIRECTORY, PLACE, CALL_LOG }
}

/**
 * The search results as Compose state: the cursors the search loaders deliver, merged by
 * [SearchCursorManager] into rows, rebuilt whenever a cursor, the query or the actions change.
 */
class SearchResults(private val context: Context) {

    private val manager = SearchCursorManager()
    private var query: String? = null
    private var rawNumber: String? = null

    var rows by mutableStateOf<List<SearchRow>>(emptyList())
        private set

    /** Whether contacts cannot be read, so the results ask for the permission instead. */
    var needsPermission by mutableStateOf(false)

    fun setContactsCursor(cursor: SearchCursor?) {
        if (manager.setContactsCursor(cursor)) {
            manager.setQuery(query.orEmpty())
            rebuild()
        }
    }

    /** Sets the numbers from the call history that aren't contacts. */
    fun setCallLogCursor(cursor: SearchCursor?) {
        if (manager.setCallLogCursor(cursor)) {
            manager.setQuery(query.orEmpty())
            rebuild()
        }
    }

    fun setNearbyPlacesCursor(cursor: SearchCursor?) {
        if (manager.setNearbyPlacesCursor(cursor)) rebuild()
    }

    fun setDirectoryContactsCursor(cursor: SearchCursor?) {
        if (manager.setCorpDirectoryCursor(cursor)) rebuild()
    }

    fun setQuery(query: String?, rawNumber: String?) {
        this.query = query
        this.rawNumber = rawNumber
        // The actions read the raw number, so rebuild even when the manager has nothing new.
        manager.setQuery(query.orEmpty())
        rebuild()
    }

    fun setSearchActions(actions: List<Int>) {
        if (manager.setSearchActions(actions)) rebuild()
    }

    fun showLocationPermissionRequest(show: Boolean) {
        if (manager.showLocationPermissionRequest(show)) rebuild()
    }

    private fun rebuild() {
        val query = query.orEmpty()
        val actionNumber = rawNumber?.takeIf { it.isNotEmpty() } ?: query
        rows = (0 until manager.count).map { position ->
            when (manager.getRowType(position)) {
                SearchCursorManager.RowType.CONTACT_HEADER,
                SearchCursorManager.RowType.CALL_LOG_HEADER,
                SearchCursorManager.RowType.DIRECTORY_HEADER,
                SearchCursorManager.RowType.NEARBY_PLACES_HEADER ->
                    SearchRow.Header("header_$position", manager.getCursor(position).getString(SearchCursor.HEADER_TEXT_POSITION))
                SearchCursorManager.RowType.CONTACT_ROW -> number(manager.getCursor(position), SearchRow.Kind.CONTACT, position, query)
                SearchCursorManager.RowType.CALL_LOG_ROW -> callLogNumber(manager.getCursor(position), position, query)
                SearchCursorManager.RowType.DIRECTORY_ROW -> number(manager.getCursor(position), SearchRow.Kind.DIRECTORY, position, query)
                SearchCursorManager.RowType.NEARBY_PLACES_ROW -> number(manager.getCursor(position), SearchRow.Kind.PLACE, position, query)
                SearchCursorManager.RowType.SEARCH_ACTION ->
                    manager.getSearchAction(position).let { SearchRow.Action("action_$it", it, position, actionNumber) }
                else -> SearchRow.LocationRequest
            }
        }
    }

    private fun number(cursor: SearchCursor, kind: SearchRow.Kind, position: Int, query: String): SearchRow.Number {
        val number = cursor.getString(Projections.PHONE_NUMBER).orEmpty()
        val name = cursor.getString(Projections.DISPLAY_NAME).orEmpty()
        val lookupKey = cursor.getString(Projections.LOOKUP_KEY).orEmpty()
        val details: CharSequence = when (kind) {
            // A place's label column holds its address.
            SearchRow.Kind.PLACE -> QueryBoldingUtil.getNameWithQueryBolded(query, cursor.getString(Projections.PHONE_LABEL), context)
            SearchRow.Kind.CONTACT -> QueryBoldingUtil.getNumberWithQueryBolded(query, numberWithLabel(cursor, number))
            SearchRow.Kind.DIRECTORY, SearchRow.Kind.CALL_LOG ->
                QueryBoldingUtil.getNameWithQueryBolded(query, numberWithLabel(cursor, number), context)
        }
        return SearchRow.Number(
            key = "${kind}_${position}_$number",
            kind = kind,
            position = cursor.position,
            number = number,
            name = if (kind == SearchRow.Kind.PLACE || isFirstNumberOfContact(cursor, lookupKey)) {
                QueryBoldingUtil.getNameWithQueryBolded(query, name, context).toAnnotated()
            } else {
                null
            },
            details = details.toAnnotated(),
            photoUri = cursor.getString(Projections.PHOTO_URI),
            colorKey = lookupKey.ifEmpty { name },
            canVideoCall = kind == SearchRow.Kind.CONTACT &&
                cursor.getInt(Projections.CARRIER_PRESENCE) and Phone.CARRIER_PRESENCE_VT_CAPABLE != 0,
            isWork = kind == SearchRow.Kind.DIRECTORY && DirectoryUtils.isLocalEnterpriseDirectoryId(cursor.directoryId),
        )
    }

    /** A number from the call history that isn't a contact: the number, and where it is from. */
    private fun callLogNumber(cursor: SearchCursor, position: Int, query: String): SearchRow.Number {
        val number = cursor.getString(CallLogSearchCursor.NUMBER).orEmpty()
        val formatted = cursor.getString(CallLogSearchCursor.FORMATTED_NUMBER).orEmpty()
        return SearchRow.Number(
            key = "${SearchRow.Kind.CALL_LOG}_${position}_$number",
            kind = SearchRow.Kind.CALL_LOG,
            position = cursor.position,
            number = number,
            name = QueryBoldingUtil.getNumberWithQueryBolded(query, formatted).toAnnotated(),
            details = AnnotatedString(cursor.getString(CallLogSearchCursor.LOCATION).orEmpty()),
            photoUri = null,
            colorKey = formatted,
            canVideoCall = false,
            isWork = false,
        )
    }

    private fun numberWithLabel(cursor: SearchCursor, number: String): String {
        val type = cursor.getInt(Projections.PHONE_TYPE)
        val label = cursor.getString(Projections.PHONE_LABEL)
        val typeLabel = if (type == Phone.TYPE_CUSTOM && label.isNullOrEmpty()) {
            ""
        } else {
            Phone.getTypeLabel(context.resources, type, label).toString()
        }
        return if (typeLabel.isEmpty()) number else context.getString(R.string.call_subject_type_and_number, typeLabel, number)
    }

    /** A contact's numbers come one after another; only the first shows the name and photo. */
    private fun isFirstNumberOfContact(cursor: SearchCursor, lookupKey: String): Boolean {
        val position = cursor.position
        cursor.moveToPosition(position - 1)
        val first = cursor.isBeforeFirst || cursor.isHeader || cursor.getString(Projections.LOOKUP_KEY) != lookupKey
        cursor.moveToPosition(position)
        return first
    }
}

/** Keeps the bold runs QueryBoldingUtil marks on the query's matches. */
internal fun CharSequence?.toAnnotated(): AnnotatedString {
    val text = this ?: return AnnotatedString("")
    if (text !is Spanned) return AnnotatedString(text.toString())
    return buildAnnotatedString {
        append(text.toString())
        for (span in text.getSpans(0, text.length, StyleSpan::class.java)) {
            if (span.style and Typeface.BOLD != 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), text.getSpanStart(span), text.getSpanEnd(span))
            }
        }
    }
}
