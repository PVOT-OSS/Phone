/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.voicemail

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.prauga.phone.dialer.R
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The playback controls of the open voicemail, as Compose state. The [VoicemailPlaybackPresenter]
 * drives it through [VoicemailPlaybackPresenter.PlaybackView], as it drove the old view.
 */
class VoicemailPlaybackState : VoicemailPlaybackPresenter.PlaybackView {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var presenter: VoicemailPlaybackPresenter? = null
    private var positionUpdates: ScheduledFuture<*>? = null

    var voicemailUri: Uri? by mutableStateOf(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var isSpeakerOn by mutableStateOf(false)
        private set

    /** Whether play, pause and seeking work; off while the audio downloads or after an error. */
    var controlsEnabled by mutableStateOf(false)
        private set
    var isSeekEnabled by mutableStateOf(false)
        private set
    var isShareVisible by mutableStateOf(false)
        private set

    /** What the playback is doing, such as "Fetching voicemail", or null when it is ready. */
    @get:StringRes
    var statusText by mutableStateOf<Int?>(null)
        private set

    /** Called by the presenter to show or hide the share action. */
    fun showShare(visible: Boolean) {
        isShareVisible = visible
    }

    fun togglePlayback() {
        val presenter = presenter ?: return
        if (isPlaying) presenter.pausePlayback() else presenter.resumePlayback()
    }

    fun toggleSpeaker() = presenter?.toggleSpeakerphone()

    fun startSeeking() = presenter?.pausePlaybackForSeeking()

    fun seekTo(positionMs: Int) {
        setClipPosition(positionMs, durationMs)
        presenter?.seek(positionMs)
    }

    fun finishSeeking(positionMs: Int) = presenter?.resumePlaybackAfterSeeking(positionMs)

    fun pause() = presenter?.pausePlayback()

    fun share() = presenter?.shareVoicemail()

    override fun getDesiredClipPosition(): Int = positionMs

    override fun disableUiElements() {
        controlsEnabled = false
        resetSeekBar()
    }

    override fun enableUiElements() {
        controlsEnabled = true
        isSeekEnabled = true
    }

    override fun onPlaybackError() {
        stopPositionUpdates()
        disableUiElements()
        statusText = R.string.voicemail_playback_error
    }

    override fun onPlaybackStarted(duration: Int, executorService: ScheduledExecutorService) {
        isPlaying = true
        stopPositionUpdates()
        positionUpdates = executorService.scheduleWithFixedDelay(
            {
                val position = presenter?.getMediaPlayerPosition() ?: return@scheduleWithFixedDelay
                mainHandler.post { setClipPosition(position, duration) }
            },
            0,
            POSITION_UPDATE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun onPlaybackStopped() {
        isPlaying = false
        stopPositionUpdates()
    }

    override fun onSpeakerphoneOn(on: Boolean) {
        isSpeakerOn = on
    }

    override fun setClipPosition(clipPositionInMillis: Int, clipLengthInMillis: Int) {
        val position = clipPositionInMillis.coerceAtLeast(0)
        positionMs = position
        durationMs = clipLengthInMillis.coerceAtLeast(position)
    }

    override fun setSuccess() {
        statusText = null
    }

    override fun setFetchContentTimeout() {
        controlsEnabled = true
        statusText = R.string.voicemail_fetching_timout
    }

    override fun setIsFetchingContent() {
        disableUiElements()
        statusText = R.string.voicemail_fetching_content
    }

    override fun setPresenter(presenter: VoicemailPlaybackPresenter, voicemailUri: Uri) {
        this.presenter = presenter
        this.voicemailUri = voicemailUri
    }

    override fun resetSeekBar() {
        positionMs = 0
        isSeekEnabled = false
    }

    private fun stopPositionUpdates() {
        positionUpdates?.cancel(false)
        positionUpdates = null
    }

    private companion object {
        /** About 30 position updates a second, as the old seek bar had. */
        const val POSITION_UPDATE_MILLIS = 1000L / 30
    }
}

/** "01:05" for 65 seconds; minutes stop at 99. */
internal fun formatClipTime(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = (totalSeconds / 60).coerceAtMost(99)
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, totalSeconds % 60)
}
