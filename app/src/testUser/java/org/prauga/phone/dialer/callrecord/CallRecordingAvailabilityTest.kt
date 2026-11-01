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

/** Call recording is off in the user build, which lacks CAPTURE_AUDIO_OUTPUT. */
@RunWith(RobolectricTestRunner::class)
class CallRecordingAvailabilityTest {
    @Test
    fun recordingIsOff() {
        assertThat(CallRecorderService.isEnabled(ApplicationProvider.getApplicationContext())).isFalse()
    }
}
