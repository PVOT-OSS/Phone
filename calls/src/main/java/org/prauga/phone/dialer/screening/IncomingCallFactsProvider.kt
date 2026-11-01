/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telephony.CarrierConfigManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import java.util.concurrent.TimeUnit

/**
 * Gathers [IncomingCallFacts] for a call from Telephony, Contacts and the call log. Lookups block,
 * so call [factsFor] off the main thread; it throws when a required lookup fails.
 */
class IncomingCallFactsProvider(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val telephony = context.getSystemService(TelephonyManager::class.java)

    fun factsFor(details: Call.Details, hasActiveCall: Boolean): IncomingCallFacts {
        val number = details.handle?.schemeSpecificPart?.takeIf { it.isNotEmpty() }
        return IncomingCallFacts(
            number = number,
            presentation = details.handlePresentation,
            verificationStatus = details.callerNumberVerificationStatus,
            isEmergencyNumber = number != null && isEmergencyNumber(number),
            hasRecentEmergencyCall = hasRecentEmergencyCall(),
            isContact = number != null && (isContact(number) || isFixedDialingNumber(number)),
            hasCallLogHistory = number != null && latestCallDate(number, outgoingOnly = false) != null,
            hasRecentOutgoingCall = number != null && isRecent(latestCallDate(number, outgoingOnly = true), RECENT_OUTGOING_MILLIS),
            isMarkedDoNotScreen = false,
            hasActiveCall = hasActiveCall,
        )
    }

    private fun isEmergencyNumber(number: String): Boolean =
        runCatching { telephony.isEmergencyNumber(number) }.getOrDefault(false)

    private fun isContact(number: String): Boolean {
        val uri = Uri.withAppendedPath(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null).use {
            checkNotNull(it) { "Contacts lookup failed" }
            return it.moveToFirst()
        }
    }

    private fun isFixedDialingNumber(number: String): Boolean = runCatching {
        val countryIso = telephony.simCountryIso
        context.contentResolver.query(FDN_URI, arrayOf(FDN_NUMBER), null, null, null)?.use {
            while (it.moveToNext()) {
                val fdn = it.getString(0)
                if (!fdn.isNullOrEmpty() && PhoneNumberUtils.areSamePhoneNumber(fdn, number, countryIso)) return@runCatching true
            }
            false
        } ?: false
    }.getOrDefault(false)

    private fun latestCallDate(number: String, outgoingOnly: Boolean): Long? {
        val uri = CallLog.Calls.CONTENT_FILTER_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1")
            .appendEncodedPath(Uri.encode(number))
            .build()
        val selection = if (outgoingOnly) "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}" else null
        context.contentResolver.query(uri, arrayOf(CallLog.Calls.DATE), selection, null, "${CallLog.Calls.DATE} DESC").use {
            checkNotNull(it) { "Call log lookup failed" }
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private fun hasRecentEmergencyCall(): Boolean {
        val since = clock() - emergencyCallbackMillis()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE} AND ${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            null,
        ).use {
            checkNotNull(it) { "Call log lookup failed" }
            while (it.moveToNext()) {
                val number = it.getString(0)
                if (!number.isNullOrEmpty() && isEmergencyNumber(number)) return true
            }
            return false
        }
    }

    private fun emergencyCallbackMillis(): Long {
        val canReadConfig =
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val seconds = if (canReadConfig) {
            runCatching {
                context.getSystemService(CarrierConfigManager::class.java).config
                    ?.getInt(CarrierConfigManager.KEY_DURATION_BLOCKING_DISABLED_AFTER_EMERGENCY_INT)
            }.getOrNull()
        } else {
            null
        } ?: DEFAULT_EMERGENCY_CALLBACK_SECONDS
        return TimeUnit.SECONDS.toMillis(seconds.toLong())
    }

    private fun isRecent(date: Long?, windowMillis: Long): Boolean = date != null && date > clock() - windowMillis

    private companion object {
        val RECENT_OUTGOING_MILLIS = TimeUnit.DAYS.toMillis(7)
        const val DEFAULT_EMERGENCY_CALLBACK_SECONDS = 7200
        val FDN_URI: Uri = "content://icc/fdn".toUri()
        const val FDN_NUMBER = "number"
    }
}
