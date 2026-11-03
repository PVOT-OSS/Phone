/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog

/** Why a call was missed, as far as the missed call notification tells the user. */
enum class MissedCallReason {
    /** No reason worth mentioning. */
    NONE,

    /** The phone was on Do Not Disturb. */
    DO_NOT_DISTURB,

    /** The phone was on silent: ring volume too low and no vibration. */
    SILENT_MODE;

    companion object {
        /** Reads the call log's missed reason bits; Do Not Disturb wins over silent mode. */
        @JvmStatic
        fun of(missedReason: Long): MissedCallReason =
            when {
                missedReason and CallLog.Calls.USER_MISSED_DND_MODE != 0L -> DO_NOT_DISTURB
                missedReason and CallLog.Calls.USER_MISSED_LOW_RING_VOLUME != 0L &&
                    missedReason and CallLog.Calls.USER_MISSED_NO_VIBRATE != 0L -> SILENT_MODE
                else -> NONE
            }
    }
}
