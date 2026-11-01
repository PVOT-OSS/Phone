/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.searchfragment.calllog

import android.content.Context
import android.database.AbstractCursor
import android.database.Cursor
import android.provider.CallLog.Calls
import android.provider.ContactsContract.Directory
import android.telephony.PhoneNumberUtils
import android.view.View
import android.widget.QuickContactBadge
import android.widget.TextView
import androidx.loader.content.AsyncTaskLoader
import androidx.recyclerview.widget.RecyclerView
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.contactphoto.ContactPhotoManager
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.searchfragment.common.QueryBoldingUtil
import org.prauga.phone.dialer.searchfragment.common.RowClickListener
import org.prauga.phone.dialer.searchfragment.common.SearchCursor

/** A number from the call history that isn't saved as a contact. */
data class CallLogSearchEntry(val number: String, val formattedNumber: String, val location: String?)

/**
 * The "Not in your contacts" search section: numbers from the call history with no contact name,
 * newest first, up to three that match what was typed.
 */
class CallLogSearchCursor(
    private val header: String,
    private val entries: List<CallLogSearchEntry>,
) : AbstractCursor(), SearchCursor {

    private var shown: List<CallLogSearchEntry> = emptyList()

    override fun updateQuery(query: String): Boolean {
        val digits = query.filter(Char::isDigit)
        val matches = if (digits.isEmpty()) {
            emptyList()
        } else {
            entries.filter { it.number.filter(Char::isDigit).contains(digits) }.take(MAX_RESULTS)
        }
        if (matches == shown) return false
        shown = matches
        return true
    }

    override fun isHeader(): Boolean = position == 0

    override fun getDirectoryId(): Long = Directory.DEFAULT

    override fun getCount(): Int = if (shown.isEmpty()) 0 else shown.size + 1

    override fun getColumnNames(): Array<String> = COLUMNS

    override fun getString(column: Int): String? {
        if (position == 0) return if (column == SearchCursor.HEADER_TEXT_POSITION) header else null
        val entry = shown[position - 1]
        return when (column) {
            NUMBER -> entry.number
            FORMATTED_NUMBER -> entry.formattedNumber
            LOCATION -> entry.location
            else -> null
        }
    }

    override fun getShort(column: Int): Short = 0

    override fun getInt(column: Int): Int = 0

    override fun getLong(column: Int): Long = 0

    override fun getFloat(column: Int): Float = 0f

    override fun getDouble(column: Int): Double = 0.0

    override fun isNull(column: Int): Boolean = getString(column) == null

    companion object {
        const val NUMBER = 0
        const val FORMATTED_NUMBER = 1
        const val LOCATION = 2
        private val COLUMNS = arrayOf("number", "formatted_number", "geocoded_location")
        private const val MAX_RESULTS = 3
    }
}

/** Loads recent numbers from the call history that have no contact name. */
class CallLogSearchCursorLoader(context: Context) : AsyncTaskLoader<Cursor>(context) {

    override fun onStartLoading() = forceLoad()

    override fun loadInBackground(): Cursor {
        val entries = LinkedHashMap<String, CallLogSearchEntry>()
        runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, QUERY_LIMIT.toString()).build(),
                arrayOf(Calls.NUMBER, Calls.CACHED_NORMALIZED_NUMBER, Calls.CACHED_FORMATTED_NUMBER, Calls.GEOCODED_LOCATION),
                "(${Calls.CACHED_NAME} IS NULL OR ${Calls.CACHED_NAME} = '') AND ${Calls.NUMBER} IS NOT NULL AND ${Calls.NUMBER} != ''",
                null,
                "${Calls.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(0) ?: continue
                    val key = cursor.getString(1)?.takeIf { it.isNotEmpty() } ?: number
                    if (key in entries) continue
                    val formatted = cursor.getString(2)?.takeIf { it.isNotEmpty() }
                        ?: PhoneNumberUtils.formatNumber(number, null) ?: number
                    entries[key] = CallLogSearchEntry(number, formatted, cursor.getString(3))
                }
            }
        }
        return CallLogSearchCursor(context.getString(R.string.call_log_search_result_header), entries.values.toList())
    }

    private companion object {
        const val QUERY_LIMIT = 500
    }
}

/** A "Not in your contacts" row: tapping it calls the number. */
class CallLogSearchViewHolder(view: View, private val listener: RowClickListener) :
    RecyclerView.ViewHolder(view), View.OnClickListener {

    private val photo: QuickContactBadge = view.findViewById(R.id.photo)
    private val number: TextView = view.findViewById(R.id.primary)
    private val location: TextView = view.findViewById(R.id.secondary)
    private var dialNumber: String? = null

    init {
        view.setOnClickListener(this)
    }

    fun bind(cursor: SearchCursor, query: String) {
        val context = itemView.context
        dialNumber = cursor.getString(CallLogSearchCursor.NUMBER)
        val formatted = cursor.getString(CallLogSearchCursor.FORMATTED_NUMBER)
        number.text = QueryBoldingUtil.getNumberWithQueryBolded(query, formatted)
        val place = cursor.getString(CallLogSearchCursor.LOCATION)
        location.text = place
        location.visibility = if (place.isNullOrEmpty()) View.GONE else View.VISIBLE
        ContactPhotoManager.getInstance(context).loadDialerThumbnailOrPhoto(
            photo,
            null,
            0,
            null,
            formatted,
            LetterTileDrawable.TYPE_DEFAULT,
        )
    }

    override fun onClick(view: View) {
        dialNumber?.let { listener.placeVoiceCall(it, bindingAdapterPosition) }
    }
}
