/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.database.MatrixCursor
import android.provider.CallLog.Calls
import org.prauga.phone.dialer.calldetails.CallDetailsEntries
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.phonenumbercache.CallLogQuery
import org.prauga.phone.dialer.phonenumbercache.ContactInfo

/** One call log row, in [CallLogQuery] column order. */
internal data class CallRow(
    val id: Long,
    val number: String,
    val date: Long,
    val type: Int = Calls.INCOMING_TYPE,
    val isRead: Int = 1,
    val features: Int = 0,
    val duration: Long = 30,
    val presentation: Int = Calls.PRESENTATION_ALLOWED,
    val name: String? = null,
)

internal fun callLogCursor(vararg rows: CallRow) = MatrixCursor(CallLogQuery.getProjection()).apply {
    for (row in rows) {
        val values = arrayOfNulls<Any?>(CallLogQuery.getProjection().size)
        values[CallLogQuery.ID] = row.id
        values[CallLogQuery.NUMBER] = row.number
        values[CallLogQuery.DATE] = row.date
        values[CallLogQuery.DURATION] = row.duration
        values[CallLogQuery.CALL_TYPE] = row.type
        values[CallLogQuery.COUNTRY_ISO] = "US"
        values[CallLogQuery.CACHED_NAME] = row.name
        values[CallLogQuery.IS_READ] = row.isRead
        values[CallLogQuery.NUMBER_PRESENTATION] = row.presentation
        values[CallLogQuery.FEATURES] = row.features
        values[CallLogQuery.POST_DIAL_DIGITS] = ""
        // The provider gives an empty string, never null, for a call with no via number.
        values[CallLogQuery.VIA_NUMBER] = ""
        addRow(values)
    }
}

internal fun callLogGroup(
    rowId: Long,
    number: String = "+15550100",
    callTypes: List<Int> = listOf(Calls.INCOMING_TYPE),
    date: Long = System.currentTimeMillis(),
    isRead: Boolean = true,
    dayGroup: Int = CallLogGroupBuilder.DAY_GROUP_TODAY,
    voicemailUri: String? = null,
    presentation: Int = Calls.PRESENTATION_ALLOWED,
    cachedInfo: ContactInfo = ContactInfo.EMPTY,
) = CallLogGroup(
    rowId = rowId,
    callIds = callTypes.indices.map { rowId + it },
    callTypes = callTypes,
    number = number,
    postDialDigits = "",
    viaNumber = null,
    presentation = presentation,
    countryIso = "US",
    date = date,
    duration = 30,
    features = 0,
    geocode = null,
    accountComponentName = null,
    accountId = null,
    voicemailUri = voicemailUri,
    isRead = isRead,
    dataUsage = null,
    cachedInfo = cachedInfo,
    dayGroup = dayGroup,
    callbackAction = CallbackAction.VOICE,
    entries = CallDetailsEntries.getDefaultInstance(),
)

internal fun row(
    group: CallLogGroup,
    title: String = group.number,
    actions: List<RowAction> = listOf(RowAction.SEND_MESSAGE, RowAction.DETAILS),
    primaryAction: PrimaryAction = PrimaryAction.CALL,
    details: String = "Mobile, 5 mins ago",
) = CallLogRowUi(
    group = group,
    info = ContactInfo.EMPTY,
    title = title,
    titleIsNumber = title == group.number,
    details = details,
    accountLabel = null,
    accountColor = null,
    displayNumber = group.number,
    numberType = "",
    accountHandle = null,
    isBlocked = false,
    isEmergency = false,
    primaryAction = primaryAction,
    actions = actions,
)
