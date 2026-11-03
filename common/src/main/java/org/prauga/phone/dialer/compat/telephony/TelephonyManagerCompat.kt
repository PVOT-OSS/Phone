/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.compat.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import java.lang.reflect.InvocationTargetException
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.telecom.TelecomUtil

/** Hidden APIs in [android.telephony.TelephonyManager]. */
object TelephonyManagerCompat {

    // TODO(maxwelb): Use public API for these constants when available
    const val EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE =
        "android.telephony.event.EVENT_HANDOVER_VIDEO_FROM_WIFI_TO_LTE"
    const val EVENT_HANDOVER_VIDEO_FROM_LTE_TO_WIFI =
        "android.telephony.event.EVENT_HANDOVER_VIDEO_FROM_LTE_TO_WIFI"
    const val EVENT_HANDOVER_TO_WIFI_FAILED =
        "android.telephony.event.EVENT_HANDOVER_TO_WIFI_FAILED"
    const val EVENT_CALL_REMOTELY_HELD = "android.telecom.event.CALL_REMOTELY_HELD"
    const val EVENT_CALL_REMOTELY_UNHELD = "android.telecom.event.CALL_REMOTELY_UNHELD"
    const val EVENT_MERGE_START = "android.telecom.event.MERGE_START"
    const val EVENT_MERGE_COMPLETE = "android.telecom.event.MERGE_COMPLETE"

    const val EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC =
        "android.telephony.event.EVENT_NOTIFY_INTERNATIONAL_CALL_ON_WFC"
    const val EVENT_CALL_FORWARDED = "android.telephony.event.EVENT_CALL_FORWARDED"

    // TODO(erfanian): a bug Replace with the platform/telecom constant when available.
    /**
     * Indicates that the call being placed originated from a known contact.
     *
     * This signals to the telephony platform that an outgoing call qualifies for assisted dialing.
     */
    const val USE_ASSISTED_DIALING = "android.telecom.extra.USE_ASSISTED_DIALING"

    // TODO(erfanian): a bug Replace with the platform/telecom API when available.
    /** Additional information relating to the assisted dialing transformation. */
    const val ASSISTED_DIALING_EXTRAS = "android.telecom.extra.ASSISTED_DIALING_EXTRAS"

    const val EXTRA_IS_REFRESH = "android.telephony.extra.IS_REFRESH"

    /**
     * Indicates the call underwent Assisted Dialing; typically set as a feature available from the
     * CallLog.
     */
    const val FEATURES_ASSISTED_DIALING = 1 shl 4

    /**
     * Flag specifying whether to show an alert dialog for video call charges. By default this value
     * is `false`. TODO(a bug): Replace with public API for these constants when available.
     */
    const val CARRIER_CONFIG_KEY_SHOW_VIDEO_CALL_CHARGES_ALERT_DIALOG_BOOL =
        "show_video_call_charges_alert_dialog_bool"

    /**
     * Returns the number of phones available. Returns 1 for Single standby mode (Single SIM
     * functionality) Returns 2 for Dual standby mode.(Dual SIM functionality)
     *
     * Returns 1 if the method or telephonyManager is not available.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     */
    @JvmStatic
    fun getPhoneCount(telephonyManager: TelephonyManager?): Int {
        if (telephonyManager == null) {
            return 1
        }
        return telephonyManager.activeModemCount
    }

    /**
     * Whether the phone supports TTY mode.
     *
     * @param telecomManager The TelecomManager manager instance to use for method calls.
     * @return `true` if the device supports TTY mode, and `false` otherwise, including when the app
     *   may not read the phone state yet.
     */
    @JvmStatic
    @SuppressLint("MissingPermission")
    fun isTtyModeSupported(telecomManager: TelecomManager?): Boolean {
        return try {
            telecomManager != null && telecomManager.isTtySupported
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Whether the phone supports hearing aid compatibility.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @return `true` if the device supports hearing aid compatibility, and `false` otherwise.
     */
    @JvmStatic
    fun isHearingAidCompatibilitySupported(telephonyManager: TelephonyManager?): Boolean =
        telephonyManager != null && telephonyManager.isHearingAidCompatibilitySupported

    /**
     * This method uses a new system API to enable or disable visual voicemail. TODO(twyen):
     * restrict to N MR1, not needed in future SDK.
     */
    @JvmStatic
    fun setVisualVoicemailEnabled(
        telephonyManager: TelephonyManager?,
        handle: PhoneAccountHandle?,
        enabled: Boolean,
    ) {
        try {
            TelephonyManager::class
                .java
                .getMethod(
                    "setVisualVoicemailEnabled",
                    PhoneAccountHandle::class.java,
                    Boolean::class.javaPrimitiveType,
                )
                .invoke(telephonyManager, handle, enabled)
        } catch (e: NoSuchMethodException) {
            LogUtil.e("TelephonyManagerCompat.setVisualVoicemailEnabled", "failed", e)
        } catch (e: IllegalAccessException) {
            LogUtil.e("TelephonyManagerCompat.setVisualVoicemailEnabled", "failed", e)
        } catch (e: InvocationTargetException) {
            LogUtil.e("TelephonyManagerCompat.setVisualVoicemailEnabled", "failed", e)
        }
    }

    /**
     * Handles secret codes to launch arbitrary activities.
     *
     * @param context the context to use
     * @param secretCode the secret code without the "*#*#" prefix and "#*#*" suffix
     */
    @JvmStatic
    fun handleSecretCode(context: Context, secretCode: String?) {
        if (!TelecomUtil.isDefaultDialer(context)) {
            LogUtil.e(
                "TelephonyManagerCompat.handleSecretCode",
                "not default dialer, cannot send special code",
            )
            return
        }
        context.getSystemService(TelephonyManager::class.java).sendDialerSpecialCode(secretCode)
    }

    /**
     * Returns network country iso for given `PhoneAccountHandle` for O+ devices and country iso for
     * default sim for pre-O devices.
     */
    @JvmStatic
    fun getNetworkCountryIsoForPhoneAccountHandle(
        context: Context,
        phoneAccountHandle: PhoneAccountHandle?,
    ): String =
        getTelephonyManagerForPhoneAccountHandle(context, phoneAccountHandle).networkCountryIso

    /**
     * Returns TelephonyManager for given `PhoneAccountHandle` for O+ devices and default
     * `TelephonyManager` for pre-O devices.
     */
    @JvmStatic
    fun getTelephonyManagerForPhoneAccountHandle(
        context: Context,
        phoneAccountHandle: PhoneAccountHandle?,
    ): TelephonyManager {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        if (phoneAccountHandle == null) {
            return telephonyManager
        }
        val telephonyManagerForPhoneAccount =
            telephonyManager.createForPhoneAccountHandle(phoneAccountHandle)
        if (telephonyManagerForPhoneAccount != null) {
            return telephonyManagerForPhoneAccount
        }
        return telephonyManager
    }
}
