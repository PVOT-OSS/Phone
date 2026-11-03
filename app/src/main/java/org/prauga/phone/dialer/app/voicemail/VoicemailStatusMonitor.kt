/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.voicemail

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.VoicemailContract
import android.telecom.PhoneAccountHandle
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.util.PermissionsUtil
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessage
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessageCreator
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailStatus
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailStatusReader
import org.prauga.phone.voicemail.VoicemailComponent

/**
 * Watches the visual voicemail status of each account and publishes the one message the voicemail
 * tab should show, such as "Inbox almost full" or the carrier's terms, or null when all is well.
 */
class VoicemailStatusMonitor(private val context: Context) :
    CallLogQueryHandler.Listener, VoicemailStatusReader {

    private val _message = MutableStateFlow<VoicemailErrorMessage?>(null)
    val message: StateFlow<VoicemailErrorMessage?> = _message.asStateFlow()

    private val messageCreator = VoicemailErrorMessageCreator()
    private val queryHandler = CallLogQueryHandler(context, context.contentResolver, this)
    private val serviceListeners = mutableMapOf<PhoneAccountHandle, TelephonyCallback>()
    private var isForeground = false
    private var statusInvalidated = true

    private val statusObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = fetchStatus()
        }

    init {
        if (
            PermissionsUtil.hasReadVoicemailPermissions(context) &&
                PermissionsUtil.hasAddVoicemailPermissions(context)
        ) {
            context.contentResolver.registerContentObserver(
                VoicemailContract.Status.CONTENT_URI,
                true,
                statusObserver,
            )
        } else {
            LogUtil.w("VoicemailStatusMonitor", "read voicemail permission unavailable.")
        }
    }

    fun onResume() {
        isForeground = true
        if (statusInvalidated) fetchStatus()
    }

    fun onPause() {
        isForeground = false
        statusInvalidated = false
    }

    fun destroy() {
        context.contentResolver.unregisterContentObserver(statusObserver)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        serviceListeners.values.forEach(telephonyManager::unregisterTelephonyCallback)
        serviceListeners.clear()
    }

    override fun refresh() = fetchStatus()

    override fun onVoicemailStatusFetched(statusCursor: Cursor) {
        val statuses = mutableListOf<VoicemailStatus>()
        while (statusCursor.moveToNext()) {
            val status = VoicemailStatus(context, statusCursor)
            if (status.isActive(context)) {
                statuses += status
                listenForServiceChanges(status)
            }
        }
        _message.value = statuses.firstNotNullOfOrNull { messageCreator.create(context, it, this) }
    }

    override fun onVoicemailUnreadCountFetched(cursor: Cursor) = Unit

    override fun onMissedCallsUnreadCountFetched(cursor: Cursor) = Unit

    override fun onCallsFetched(combinedCursor: Cursor): Boolean = false

    private fun fetchStatus() {
        if (!isForeground) {
            statusInvalidated = true
            return
        }
        queryHandler.fetchVoicemailStatus()
    }

    /** Service changes, such as losing signal, change some messages. */
    private fun listenForServiceChanges(status: VoicemailStatus) {
        if (!VoicemailComponent.get(context).voicemailClient.isVoicemailModuleEnabled) return
        if (status.sourcePackage != context.packageName) return
        val handle = status.phoneAccountHandle ?: return
        if (handle in serviceListeners) return
        val telephonyManager =
            context
                .getSystemService(TelephonyManager::class.java)
                .createForPhoneAccountHandle(handle) ?: return
        val listener =
            object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) = fetchStatus()
            }
        telephonyManager.registerTelephonyCallback(context.mainExecutor, listener)
        serviceListeners[handle] = listener
    }
}
