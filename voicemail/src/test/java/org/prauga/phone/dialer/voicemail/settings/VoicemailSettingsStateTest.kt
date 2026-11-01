/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.voicemail.settings

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.voicemail.PinChanger
import org.prauga.phone.voicemail.VoicemailClient
import org.robolectric.RobolectricTestRunner

/** Which voicemail settings show, from what the voicemail client reports. */
@RunWith(RobolectricTestRunner::class)
class VoicemailSettingsStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val handle = PhoneAccountHandle(ComponentName("com.android.phone", "Telephony"), "1")

    @Test
    fun withoutCarrierSupportOnlyTheBasicsShow() {
        val state = read(carrierSupport = false, enabled = true, activated = true)
        assertThat(state.carrierSupport).isFalse()
        assertThat(state.enabled).isFalse()
        assertThat(state.archiveAvailable).isFalse()
    }

    @Test
    fun enabledButNotActivatedIsActivating() {
        val state = read(carrierSupport = true, enabled = true, activated = false)
        assertThat(state.activating).isTrue()
        // Auto-archive waits until the carrier has set visual voicemail up.
        assertThat(state.archiveAvailable).isFalse()
    }

    @Test
    fun activatedShowsArchiveWhenAvailable() {
        val state =
            read(carrierSupport = true, enabled = true, activated = true, archiveAvailable = true)
        assertThat(state.activating).isFalse()
        assertThat(state.archiveAvailable).isTrue()
    }

    @Test
    fun scrambledPinAsksToSetAPin() {
        assertThat(
                read(carrierSupport = true, enabled = true, activated = true, scrambledPin = "1234")
                    .pinScrambled
            )
            .isTrue()
        assertThat(
                read(carrierSupport = true, enabled = true, activated = true, scrambledPin = null)
                    .pinScrambled
            )
            .isFalse()
    }

    private fun read(
        carrierSupport: Boolean,
        enabled: Boolean,
        activated: Boolean,
        archiveAvailable: Boolean = false,
        scrambledPin: String? = null,
    ): VoicemailSettingsState {
        val pinChanger =
            proxy<PinChanger> { name -> if (name == "getScrambledPin") scrambledPin else null }
        val client =
            proxy<VoicemailClient> { name ->
                when (name) {
                    "hasCarrierSupport" -> carrierSupport
                    "isVoicemailEnabled" -> enabled
                    "isActivated" -> activated
                    "isVoicemailArchiveAvailable" -> archiveAvailable
                    "isVoicemailArchiveEnabled" -> false
                    "createPinChanger" -> pinChanger
                    else -> error("unexpected call $name")
                }
            }
        return VoicemailSettingsState.read(context, client, handle)
    }

    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name)
        } as T
}
