/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callrecord

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.callrecord.impl.CallRecorderService
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Call recording is on in the system build, which is granted the permission to record calls. */
@RunWith(RobolectricTestRunner::class)
class CallRecordingAvailabilityTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun recordingIsOnWithThePermission() {
        shadowOf(context).grantPermissions(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
        assertThat(CallRecorderService.isEnabled(context)).isTrue()
    }

    @Test
    fun recordingIsOffWithoutThePermission() {
        shadowOf(context).denyPermissions(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
        assertThat(CallRecorderService.isEnabled(context)).isFalse()
    }
}
