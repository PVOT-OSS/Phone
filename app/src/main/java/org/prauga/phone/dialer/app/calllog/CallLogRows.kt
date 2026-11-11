/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.content.Context
import android.content.res.Resources
import android.provider.CallLog.Calls
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import java.util.concurrent.TimeUnit
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.calllog.calllogcache.CallLogCache
import org.prauga.phone.dialer.app.settings.IncomingCallSettings
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.calllogutils.PhoneCallDetails
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.contacts.displaypreference.ContactDisplayPreferences.DisplayOrder
import org.prauga.phone.dialer.logging.ContactSource
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.util.UriUtils

/** What the button at the end of a call history row does. */
enum class PrimaryAction {
    NONE,
    CALL,
    VIDEO_CALL,
    CALL_VOICEMAIL,
    PLAY_VOICEMAIL,

    /** Calls back through the app that made the call, such as a messaging app. */
    CALL_IN_APP,
}

/** Actions shown when a call history row is opened. */
enum class RowAction {
    CALL,
    VIDEO_CALL,
    SEND_MESSAGE,
    CALL_WITH_NOTE,
    REMIND,
    CREATE_CONTACT,
    ADD_TO_CONTACT,
    BLOCK,
    UNBLOCK,
    DETAILS,
    DELETE,
}

/** A call history row ready to show. */
data class CallLogRowUi(
    val group: CallLogGroup,
    val info: ContactInfo,
    /** The contact's name, "Emergency number", or the number. */
    val title: String,
    /** Whether [title] is a number, which always reads left to right. */
    val titleIsNumber: Boolean,
    /** Call count, number type or place, and date; with the length for a voicemail. */
    val details: String,
    val accountLabel: String?,
    /** The account's highlight color, or null for none. */
    val accountColor: Int?,
    val displayNumber: String,
    val numberType: String,
    val accountHandle: PhoneAccountHandle?,
    val isBlocked: Boolean,
    val isEmergency: Boolean,
    val primaryAction: PrimaryAction,
    val actions: List<RowAction>,
    /** The app the call was made in, such as a messaging app, or null for the phone's own calls. */
    val otherApp: String? = null,
) {
    val hasContact: Boolean
        get() = !info.name.isNullOrEmpty()
}

/**
 * Turns a [CallLogGroup] and its contact into a [CallLogRowUi], with the same text and actions the
 * call history has always offered. What it asks telephony comes through [callLogCache], once per
 * number or account, so it can run for every row of a list off the main thread; one list at a time,
 * as the details helper it uses is not thread safe.
 */
class CallLogRowResolver(
    private val context: Context,
    private val callLogCache: CallLogCache,
    private val displayOrder: () -> DisplayOrder = {
        ContactsComponent.get(context).contactDisplayPreferences().displayOrder
    },
    private val otherApps: OtherAppsCalls = OtherAppsCalls(context),
) {

    private val resources: Resources = context.resources
    private val detailsHelper = PhoneCallDetailsHelper(context, resources, callLogCache)
    private val defaultAccount: PhoneAccountHandle? by lazy {
        TelecomUtil.getDefaultOutgoingPhoneAccount(context, PhoneAccount.SCHEME_TEL)
    }
    // Only the default phone app may read or change blocked numbers.
    private val canBlockNumbers: Boolean by lazy {
        try {
            TelecomUtil.isDefaultDialer(context) &&
                android.provider.BlockedNumberContract.canCurrentUserBlockNumbers(context)
        } catch (e: SecurityException) {
            false
        }
    }

    fun resolve(group: CallLogGroup, info: ContactInfo, isBlocked: Boolean): CallLogRowUi {
        val accountHandle =
            TelecomUtil.composePhoneAccountHandle(group.accountComponentName, group.accountId)
        otherApps.appOf(accountHandle)?.let { app ->
            return resolveInApp(group, info, app)
        }
        val isVoicemailNumber = callLogCache.isVoicemailNumber(accountHandle, group.number)
        val details =
            PhoneCallDetails(group.number, group.presentation, group.postDialDigits).apply {
                viaNumber = group.viaNumber
                countryIso = group.countryIso
                date = group.date
                duration = group.duration
                features = group.features
                geocode = group.geocode
                callTypes = group.callTypes.toIntArray()
                accountComponentName = group.accountComponentName
                accountId = group.accountId
                cachedContactInfo = group.cachedInfo
                dataUsage = group.dataUsage
                isRead = group.isRead
                voicemailUri = group.voicemailUri
                this.isBlocked = isBlocked
                this.accountHandle = accountHandle
            }
        val formattedNumber = info.formattedNumber?.let(PhoneNumberUtils::createTtsSpannable)
        details.updateDisplayNumber(context, formattedNumber, isVoicemailNumber)
        if (!info.name.isNullOrEmpty() || !info.nameAlternative.isNullOrEmpty()) {
            details.contactUri = info.lookupUri
            details.namePrimary = info.name
            details.nameAlternative = info.nameAlternative
            details.nameDisplayOrder = displayOrder()
            details.numberType = info.type
            details.numberLabel = info.label
            details.photoUri = info.photoUri
            details.sourceType = info.sourceType
            details.objectId = info.objectId
            details.contactUserType = info.userType
        }
        if (!info.geoDescription.isNullOrEmpty()) details.geocode = info.geoDescription

        val preferredName = details.preferredName?.toString()
        val displayNumber = details.displayNumber.orEmpty()
        val isEmergencyName =
            preferredName.isNullOrEmpty() && callLogCache.isEmergencyNumber(displayNumber)
        val title =
            when {
                !preferredName.isNullOrEmpty() -> preferredName
                isEmergencyName -> resources.getString(R.string.emergency_number)
                else -> displayNumber
            }

        val canCall = PhoneNumberHelper.canPlaceCallsTo(group.number, group.presentation)
        val isEmergencyNumber = callLogCache.isEmergencyNumber(group.number)
        val accountLabel = accountLabel(accountHandle, group.viaNumber)
        val color =
            callLogCache.getAccountColor(accountHandle).takeIf {
                it != PhoneAccount.NO_HIGHLIGHT_COLOR
            }
        return CallLogRowUi(
            group = group,
            info = info,
            title = title,
            titleIsNumber = preferredName.isNullOrEmpty() && !isEmergencyName,
            details = detailText(group, details),
            accountLabel = accountLabel,
            accountColor = if (accountLabel != null) color else null,
            displayNumber = displayNumber,
            numberType = numberType(details),
            accountHandle = accountHandle,
            isBlocked = isBlocked,
            isEmergency = isEmergencyNumber,
            primaryAction = primaryAction(group, canCall, isVoicemailNumber),
            actions =
                actions(
                    group,
                    info,
                    accountHandle,
                    canCall,
                    isVoicemailNumber,
                    isBlocked,
                    isEmergencyNumber,
                ),
        )
    }

    /**
     * A call made in another app: it is called back through that app, which also blocks and
     * messages, so only the call and delete are offered here.
     */
    private fun resolveInApp(group: CallLogGroup, info: ContactInfo, app: String): CallLogRowUi {
        val name = info.name?.takeIf { it.isNotEmpty() } ?: group.cachedInfo.name
        val title = name?.takeIf { it.isNotEmpty() } ?: group.number
        val details =
            PhoneCallDetails(group.number, group.presentation, group.postDialDigits).apply {
                callTypes = group.callTypes.toIntArray()
                date = group.date
                duration = group.duration
                features = group.features
                isRead = group.isRead
            }
        return CallLogRowUi(
            group = group,
            info = info,
            title = title,
            titleIsNumber = name.isNullOrEmpty(),
            details = detailText(group, details),
            accountLabel = app,
            accountColor = null,
            displayNumber = group.number,
            numberType = "",
            accountHandle =
                TelecomUtil.composePhoneAccountHandle(group.accountComponentName, group.accountId),
            isBlocked = false,
            isEmergency = false,
            primaryAction = PrimaryAction.CALL_IN_APP,
            actions = listOf(RowAction.DELETE),
            otherApp = app,
        )
    }

    private fun accountLabel(accountHandle: PhoneAccountHandle?, viaNumber: String?): String? {
        val label = callLogCache.getAccountLabel(accountHandle)
        return when {
            !viaNumber.isNullOrEmpty() && !label.isNullOrEmpty() ->
                resources.getString(R.string.call_log_via_number_phone_account, label, viaNumber)
            !viaNumber.isNullOrEmpty() ->
                resources.getString(R.string.call_log_via_number, viaNumber)
            else -> label?.takeIf { it.isNotEmpty() }
        }
    }

    private fun detailText(group: CallLogGroup, details: PhoneCallDetails): String {
        val locationAndDate = detailsHelper.getCallLocationAndDate(details)
        val text: CharSequence =
            if (group.callTypes.size > MAX_CALL_TYPE_ICONS) {
                resources.getString(
                    R.string.call_log_item_count_and_date,
                    group.callTypes.size,
                    locationAndDate,
                )
            } else {
                locationAndDate
            }
        if (group.isVoicemail && group.duration > 0) {
            return resources.getString(
                R.string.voicemailCallLogDateTimeFormatWithDuration,
                text,
                voicemailDuration(group.duration),
            )
        }
        return text.toString()
    }

    private fun voicemailDuration(seconds: Long): String {
        val minutes = TimeUnit.SECONDS.toMinutes(seconds).coerceAtMost(99)
        return resources.getString(
            R.string.voicemailDurationFormat,
            minutes,
            seconds - TimeUnit.MINUTES.toSeconds(minutes),
        )
    }

    private fun numberType(details: PhoneCallDetails): String {
        if (
            details.sourceType == ContactSource.Type.SOURCE_TYPE_CNAP ||
                details.sourceType == ContactSource.Type.SOURCE_TYPE_CEQUINT_CALLER_ID
        ) {
            return ""
        }
        if (details.numberType == Phone.TYPE_CUSTOM && details.numberLabel.isNullOrEmpty())
            return ""
        return Phone.getTypeLabel(resources, details.numberType, details.numberLabel).toString()
    }

    private fun primaryAction(
        group: CallLogGroup,
        canCall: Boolean,
        isVoicemailNumber: Boolean,
    ): PrimaryAction =
        when {
            !group.voicemailUri.isNullOrEmpty() -> PrimaryAction.PLAY_VOICEMAIL
            !canCall -> PrimaryAction.NONE
            group.callbackAction == CallbackAction.IMS_VIDEO -> PrimaryAction.VIDEO_CALL
            group.callbackAction == CallbackAction.VOICE && isVoicemailNumber ->
                PrimaryAction.CALL_VOICEMAIL
            group.callbackAction == CallbackAction.VOICE -> PrimaryAction.CALL
            else -> PrimaryAction.NONE
        }

    private fun actions(
        group: CallLogGroup,
        info: ContactInfo,
        accountHandle: PhoneAccountHandle?,
        canCall: Boolean,
        isVoicemailNumber: Boolean,
        isBlocked: Boolean,
        isEmergency: Boolean,
    ): List<RowAction> {
        if (isEmergency) return listOf(RowAction.DETAILS)
        // A voicemail from a number that cannot be dialed offers only its playback.
        if (group.isVoicemail && group.number.none(PhoneNumberUtils::isDialable))
            return listOf(RowAction.DELETE)

        val actions = mutableListOf<RowAction>()
        if (!group.voicemailUri.isNullOrEmpty() && canCall) actions += RowAction.CALL
        if (
            canCall &&
                group.callbackAction == CallbackAction.VOICE &&
                callLogCache.isVideoEnabled() &&
                (hasPlacedCarrierVideoCall(group, accountHandle) || canCarrierVideoCall(info))
        ) {
            actions += RowAction.VIDEO_CALL
        }
        if (canCall && !isBlocked && !isVoicemailNumber) actions += RowAction.SEND_MESSAGE
        if (callLogCache.doesAccountSupportCallSubject(accountHandle) && !isVoicemailNumber) {
            actions += RowAction.CALL_WITH_NOTE
        }
        if (
            group.callType == Calls.MISSED_TYPE &&
                canCall &&
                !isBlocked &&
                IncomingCallSettings.callbackReminders(context)
        ) {
            actions += RowAction.REMIND
        }
        if (!isBlocked && UriUtils.isEncodedContactUri(info.lookupUri)) {
            actions += RowAction.CREATE_CONTACT
            actions += RowAction.ADD_TO_CONTACT
        }
        if (
            canCall &&
                !isVoicemailNumber &&
                canBlockNumbers &&
                !callLogCache.isEmergencyNumber(
                    PhoneNumberUtils.formatNumberToE164(group.number, group.countryIso)
                )
        ) {
            actions += if (isBlocked) RowAction.UNBLOCK else RowAction.BLOCK
        }
        if (!group.isVoicemail) actions += RowAction.DETAILS
        // Every row can be deleted from its actions, which asks first, as well as by swiping.
        actions += RowAction.DELETE
        return actions
    }

    private fun hasPlacedCarrierVideoCall(
        group: CallLogGroup,
        accountHandle: PhoneAccountHandle?,
    ): Boolean {
        if (group.features and Calls.FEATURES_VIDEO != Calls.FEATURES_VIDEO) return false
        val default = defaultAccount ?: return false
        return accountHandle?.componentName == default.componentName
    }

    private fun canCarrierVideoCall(info: ContactInfo): Boolean =
        callLogCache.canRelyOnVideoPresence() &&
            info.carrierPresence and Phone.CARRIER_PRESENCE_VT_CAPABLE != 0

    private companion object {
        const val MAX_CALL_TYPE_ICONS = 3
    }
}
