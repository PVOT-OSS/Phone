/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.provider.CallLog.Calls
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.compat.telephony.TelephonyManagerCompat
import org.robolectric.RobolectricTestRunner

/** The quality badges on a call log row's second line. */
@RunWith(RobolectricTestRunner::class)
class QualityBadgesTest {

    @Test
    fun badgesComeWifiThenHdThenAssistedDialing() {
        val all = Calls.FEATURES_HD_CALL or Calls.FEATURES_WIFI or TelephonyManagerCompat.FEATURES_ASSISTED_DIALING
        assertThat(qualityBadges(all).map { it.first })
            .containsExactly(R.drawable.ic_wifi, R.drawable.ic_badge_hd, R.drawable.ic_world).inOrder()
    }

    @Test
    fun videoAndRttHaveNoBadge() {
        assertThat(qualityBadges(Calls.FEATURES_VIDEO or Calls.FEATURES_RTT)).isEmpty()
    }
}
