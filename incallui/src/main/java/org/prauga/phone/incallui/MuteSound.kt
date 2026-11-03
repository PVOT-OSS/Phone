/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.UserManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * A short chirp confirming the microphone was muted (falling) or unmuted (rising), made on the fly
 * rather than from a sound file.
 */
object MuteSound {

    /**
     * Plays the sound for [muted]; nothing before the first unlock, as the call screen stays quiet
     * then.
     */
    @JvmStatic
    fun play(context: Context, muted: Boolean) {
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) return
        val samples = chirp(if (muted) FREQUENCIES_MUTE else FREQUENCIES_UNMUTE)
        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        track.write(samples, 0, samples.size)
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) = track.release()

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            }
        )
        track.play()
    }

    /** Two short notes one after the other, each faded in and out so they don't click. */
    internal fun chirp(frequencies: List<Double>): ShortArray {
        val noteSamples = SAMPLE_RATE * NOTE_MILLIS / 1000
        val out = ShortArray(noteSamples * frequencies.size)
        frequencies.forEachIndexed { note, frequency ->
            for (i in 0 until noteSamples) {
                val envelope = minOf(1.0, i / FADE_SAMPLES, (noteSamples - i) / FADE_SAMPLES)
                val value = sin(2 * PI * frequency * i / SAMPLE_RATE) * envelope * VOLUME
                out[note * noteSamples + i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    private const val SAMPLE_RATE = 44_100
    private const val NOTE_MILLIS = 70
    private const val FADE_SAMPLES = 400.0
    private const val VOLUME = 0.35
    private val FREQUENCIES_MUTE = listOf(880.0, 660.0)
    private val FREQUENCIES_UNMUTE = listOf(660.0, 880.0)
}
