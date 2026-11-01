/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.app.voicemail.formatClipTime
import org.robolectric.RobolectricTestRunner

/** How the playback controls follow the voicemail presenter. */
@RunWith(RobolectricTestRunner::class)
class VoicemailPlaybackStateTest {

    private val state = VoicemailPlaybackState()

    @Test
    fun fetchingDisablesControlsUntilReady() {
        state.setIsFetchingContent()
        assertThat(state.controlsEnabled).isFalse()
        assertThat(state.statusText).isEqualTo(R.string.voicemail_fetching_content)

        state.enableUiElements()
        state.setSuccess()

        assertThat(state.controlsEnabled).isTrue()
        assertThat(state.isSeekEnabled).isTrue()
        assertThat(state.statusText).isNull()
    }

    @Test
    fun aTimeoutStillLetsTheUserRetry() {
        state.setIsFetchingContent()
        state.setFetchContentTimeout()
        assertThat(state.controlsEnabled).isTrue()
        assertThat(state.statusText).isEqualTo(R.string.voicemail_fetching_timout)
    }

    @Test
    fun anErrorDisablesPlayback() {
        state.enableUiElements()
        state.onPlaybackError()
        assertThat(state.controlsEnabled).isFalse()
        assertThat(state.statusText).isEqualTo(R.string.voicemail_playback_error)
    }

    @Test
    fun positionNeverPassesTheLengthOrGoesBelowZero() {
        state.setClipPosition(50_000, 40_000)
        assertThat(state.durationMs).isEqualTo(50_000)
        state.setClipPosition(-5, 40_000)
        assertThat(state.positionMs).isEqualTo(0)
    }

    @Test
    fun speakerAndShareFollowThePresenter() {
        state.onSpeakerphoneOn(true)
        state.showShare(true)
        assertThat(state.isSpeakerOn).isTrue()
        assertThat(state.isShareVisible).isTrue()
    }

    @Test
    fun timesReadAsMinutesAndSeconds() {
        assertThat(formatClipTime(65_000)).isEqualTo("01:05")
        assertThat(formatClipTime(200 * 60_000)).isEqualTo("99:00")
    }
}
