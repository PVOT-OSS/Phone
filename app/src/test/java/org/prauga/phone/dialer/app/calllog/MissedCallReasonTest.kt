/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import org.junit.Assert.assertEquals
import org.junit.Test

class MissedCallReasonTest {

    @Test
    fun plainMissedCall() =
        assertEquals(MissedCallReason.NONE, MissedCallReason.of(Calls.MISSED_REASON_NOT_MISSED))

    @Test
    fun doNotDisturb() =
        assertEquals(
            MissedCallReason.DO_NOT_DISTURB,
            MissedCallReason.of(Calls.USER_MISSED_DND_MODE),
        )

    @Test
    fun silentNeedsBothLowVolumeAndNoVibration() {
        assertEquals(
            MissedCallReason.SILENT_MODE,
            MissedCallReason.of(Calls.USER_MISSED_LOW_RING_VOLUME or Calls.USER_MISSED_NO_VIBRATE),
        )
        assertEquals(MissedCallReason.NONE, MissedCallReason.of(Calls.USER_MISSED_LOW_RING_VOLUME))
        assertEquals(MissedCallReason.NONE, MissedCallReason.of(Calls.USER_MISSED_NO_VIBRATE))
    }

    @Test
    fun doNotDisturbWinsOverSilent() =
        assertEquals(
            MissedCallReason.DO_NOT_DISTURB,
            MissedCallReason.of(
                Calls.USER_MISSED_DND_MODE or
                    Calls.USER_MISSED_LOW_RING_VOLUME or
                    Calls.USER_MISSED_NO_VIBRATE
            ),
        )

    @Test
    fun otherReasonsAreNotMentioned() =
        assertEquals(MissedCallReason.NONE, MissedCallReason.of(Calls.USER_MISSED_SHORT_RING))
}
