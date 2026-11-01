/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.database.Cursor
import android.provider.CallLog.Calls
import org.prauga.phone.dialer.calldetails.CallDetailsEntries
import org.prauga.phone.dialer.calldetails.CallDetailsEntries.CallDetailsEntry
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.phonenumbercache.CallLogQuery
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.prauga.phone.dialer.phonenumbercache.ContactInfoHelper

/**
 * Consecutive calls with the same number, account and kind, shown as one row of the call history.
 *
 * Fields hold the newest call's values; [callTypes] and [callIds] list every call, newest first.
 */
data class CallLogGroup(
    val rowId: Long,
    val callIds: List<Long>,
    val callTypes: List<Int>,
    val number: String,
    val postDialDigits: String,
    val viaNumber: String?,
    val presentation: Int,
    val countryIso: String?,
    val date: Long,
    val duration: Long,
    val features: Int,
    val geocode: String?,
    val accountComponentName: String?,
    val accountId: String?,
    val voicemailUri: String?,
    val isRead: Boolean,
    val dataUsage: Long?,
    val cachedInfo: ContactInfo,
    val dayGroup: Int,
    @CallbackAction val callbackAction: Int,
    val entries: CallDetailsEntries,
    /** A voicemail's transcript, when the carrier sent one. */
    val transcription: String? = null,
) {
    /** The newest call's type. */
    val callType: Int get() = callTypes.first()

    val isVoicemail: Boolean get() = callType == Calls.VOICEMAIL_TYPE
}

/** Groups the rows of a [CallLogQuery] cursor the way the call history always has. */
object CallLogGrouper {

    fun group(cursor: Cursor): List<CallLogGroup> {
        val positions = mutableListOf<Pair<Int, Int>>()
        val dayGroups = mutableMapOf<Long, Int>()
        val callbackActions = mutableMapOf<Long, Int>()
        val builder = CallLogGroupBuilder(object : CallLogGroupBuilder.GroupCreator {
            override fun addGroup(cursorPosition: Int, size: Int) {
                positions += cursorPosition to size
            }

            override fun setCallbackAction(rowId: Long, callbackAction: Int) {
                callbackActions[rowId] = callbackAction
            }

            override fun setDayGroup(rowId: Long, dayGroup: Int) {
                dayGroups[rowId] = dayGroup
            }

            override fun clearDayGroups() = dayGroups.clear()
        })
        builder.addGroups(cursor)
        // The builder only reports groups of two or more; every other row is a group of one.
        val sizes = positions.toMap()
        val groups = mutableListOf<CallLogGroup>()
        var position = 0
        while (position < cursor.count) {
            val size = sizes[position] ?: 1
            cursor.moveToPosition(position)
            groups += read(cursor, size, dayGroups, callbackActions)
            position += size
        }
        return groups
    }

    private fun read(
        cursor: Cursor,
        size: Int,
        dayGroups: Map<Long, Int>,
        callbackActions: Map<Long, Int>,
    ): CallLogGroup {
        val start = cursor.position
        val rowId = cursor.getLong(CallLogQuery.ID)
        val cachedInfo = ContactInfoHelper.getContactInfo(cursor)
        val first = Snapshot(cursor)
        val callIds = mutableListOf<Long>()
        val callTypes = mutableListOf<Int>()
        val entries = CallDetailsEntries.newBuilder()
        var features = 0
        repeat(size) {
            callIds += cursor.getLong(CallLogQuery.ID)
            callTypes += cursor.getInt(CallLogQuery.CALL_TYPE)
            features = features or cursor.getInt(CallLogQuery.FEATURES)
            entries.addEntries(
                CallDetailsEntry.newBuilder()
                    .setCallId(cursor.getLong(CallLogQuery.ID))
                    .setCallType(cursor.getInt(CallLogQuery.CALL_TYPE))
                    .setDataUsage(cursor.getLong(CallLogQuery.DATA_USAGE))
                    .setDate(cursor.getLong(CallLogQuery.DATE))
                    .setDuration(cursor.getLong(CallLogQuery.DURATION))
                    .setFeatures(cursor.getInt(CallLogQuery.FEATURES))
                    .setCallMappingId(cursor.getLong(CallLogQuery.DATE).toString())
                    .build(),
            )
            cursor.moveToNext()
        }
        cursor.moveToPosition(start)
        val unreadable = first.callType != Calls.VOICEMAIL_TYPE && first.callType != Calls.MISSED_TYPE
        return CallLogGroup(
            rowId = rowId,
            callIds = callIds,
            callTypes = callTypes,
            number = first.number,
            postDialDigits = first.postDialDigits,
            viaNumber = first.viaNumber,
            presentation = first.presentation,
            countryIso = first.countryIso,
            date = first.date,
            duration = first.duration,
            features = features,
            geocode = first.geocode,
            accountComponentName = first.accountComponentName,
            accountId = first.accountId,
            voicemailUri = first.voicemailUri,
            // Only missed calls and voicemails can be unread.
            isRead = unreadable || first.isRead,
            dataUsage = first.dataUsage,
            cachedInfo = cachedInfo,
            dayGroup = dayGroups[rowId] ?: CallLogGroupBuilder.DAY_GROUP_NONE,
            callbackAction = callbackActions[rowId] ?: CallbackAction.NONE,
            entries = entries.build(),
            transcription = first.transcription,
        )
    }

    /** The newest call's columns. */
    private class Snapshot(cursor: Cursor) {
        val number: String = cursor.getString(CallLogQuery.NUMBER).orEmpty()
        val postDialDigits: String = cursor.getString(CallLogQuery.POST_DIAL_DIGITS).orEmpty()
        val viaNumber: String? = cursor.getString(CallLogQuery.VIA_NUMBER)
        val presentation: Int = cursor.getInt(CallLogQuery.NUMBER_PRESENTATION)
        val countryIso: String? = cursor.getString(CallLogQuery.COUNTRY_ISO)
        val date: Long = cursor.getLong(CallLogQuery.DATE)
        val duration: Long = cursor.getLong(CallLogQuery.DURATION)
        val geocode: String? = cursor.getString(CallLogQuery.GEOCODED_LOCATION)
        val accountComponentName: String? = cursor.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME)
        val accountId: String? = cursor.getString(CallLogQuery.ACCOUNT_ID)
        val voicemailUri: String? = cursor.getString(CallLogQuery.VOICEMAIL_URI)
        val callType: Int = cursor.getInt(CallLogQuery.CALL_TYPE)
        val isRead: Boolean = cursor.getInt(CallLogQuery.IS_READ) == 1
        val dataUsage: Long? = if (cursor.isNull(CallLogQuery.DATA_USAGE)) null else cursor.getLong(CallLogQuery.DATA_USAGE)
        val transcription: String? = cursor.getString(CallLogQuery.TRANSCRIPTION)?.takeIf { it.isNotBlank() }
    }
}
