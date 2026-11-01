/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.database

import android.Manifest
import android.app.Application
import android.provider.VoicemailContract.Voicemails
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The call log query, which the provider rejects if it names a column the app may not read. */
@RunWith(RobolectricTestRunner::class)
class CallLogFetchQueryTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun selection() =
        CallLogQueryHandler.buildFetchQuery(
                application,
                CallLogQueryHandler.CALL_TYPE_ALL,
                false,
                0,
                -1,
            )
            .selection

    @Test
    fun withoutVoicemailAccessTheDeletedColumnIsNotNamed() {
        assertThat(selection()).doesNotContain(Voicemails.DELETED)
    }

    @Test
    fun withVoicemailAccessDeletedVoicemailsAreLeftOut() {
        shadowOf(application)
            .grantPermissions(
                Manifest.permission.READ_VOICEMAIL,
                Manifest.permission.WRITE_VOICEMAIL,
            )
        assertThat(selection()).contains("${Voicemails.DELETED} = 0")
    }
}
