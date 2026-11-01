/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callrecord

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.callrecord.impl.CallRecorderService
import org.robolectric.RobolectricTestRunner

/** Call recording is on in the system build. */
@RunWith(RobolectricTestRunner::class)
class CallRecordingAvailabilityTest {
    @Test
    fun recordingIsOn() {
        assertThat(CallRecorderService.isEnabled(ApplicationProvider.getApplicationContext())).isTrue()
    }
}
