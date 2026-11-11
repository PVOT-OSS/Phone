/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import org.prauga.phone.dialer.telecom.TelecomUtil

/**
 * Tells calls made in other apps, which Telecom logs for calling apps such as messaging apps, from
 * the phone's own, and names the app. One instance per list; it remembers each account it looked
 * up.
 */
class OtherAppsCalls(
    private val context: Context,
    /** Whether the call log can hold other apps' calls on this phone. */
    private val listsOtherApps: () -> Boolean = TelecomUtil::canListOtherAppsCalls,
) {
    private val apps = mutableMapOf<PhoneAccountHandle, String?>()

    /** The app [handle] belongs to, when it is another app's calling account; otherwise null. */
    fun appOf(handle: PhoneAccountHandle?): String? {
        if (handle == null || !listsOtherApps()) return null
        return apps.getOrPut(handle) { lookUp(handle) }
    }

    private fun lookUp(handle: PhoneAccountHandle): String? {
        val packageName = handle.componentName.packageName
        val account = TelecomUtil.getPhoneAccount(context, handle)
        if (account != null) {
            if (!account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) return null
            return account.label?.toString()?.takeIf { it.isNotEmpty() } ?: appName(packageName)
        }
        // An account no longer registered: the phone's own SIMs come from the phone process.
        if (packageName in PHONE_PACKAGES || packageName == context.packageName) return null
        return appName(packageName) ?: packageName
    }

    private fun appName(packageName: String): String? =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    private companion object {
        /** Where the phone's own calls come from. */
        val PHONE_PACKAGES = setOf("com.android.phone", "com.android.server.telecom")
    }
}
