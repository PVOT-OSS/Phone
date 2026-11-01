/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.UserManager
import org.prauga.phone.dialer.app.settings.IncomingCallSettings
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.telecom.TelecomUtil

/**
 * Tells from accelerometer readings that a phone lying still face up was turned face down. Readings
 * are smoothed; each position has to hold for a moment before it counts.
 */
class FlipDetector {

    private var horizontal = Float.NaN
    private var vertical = Float.NaN
    private var stillSince = -1L
    private var faceUpSince = -1L
    private var faceDownSince = -1L
    private var sawFaceUp = false
    private var fired = false

    /** Feeds one reading ([x], [y], [z] in m/s², [timeMillis]); true once, when the flip is done. */
    fun onReading(x: Float, y: Float, z: Float, timeMillis: Long): Boolean {
        if (fired) return false
        val rawHorizontal = x * x + y * y
        val previousHorizontal = horizontal
        horizontal = if (horizontal.isNaN()) rawHorizontal else SMOOTHING * horizontal + (1 - SMOOTHING) * rawHorizontal
        vertical = if (vertical.isNaN()) z else SMOOTHING * vertical + (1 - SMOOTHING) * z

        val still = !previousHorizontal.isNaN() && kotlin.math.abs(horizontal - previousHorizontal) <= STILL_TOLERANCE
        stillSince = if (still) (if (stillSince < 0) timeMillis else stillSince) else -1
        val heldStill = stillSince >= 0 && timeMillis - stillSince > HOLD_MILLIS

        faceUpSince = if (vertical >= FACE_UP_Z) (if (faceUpSince < 0) timeMillis else faceUpSince) else -1
        if (heldStill && faceUpSince >= 0 && timeMillis - faceUpSince > HOLD_MILLIS) sawFaceUp = true

        faceDownSince = if (vertical <= FACE_DOWN_Z) (if (faceDownSince < 0) timeMillis else faceDownSince) else -1
        if (sawFaceUp && heldStill && faceDownSince >= 0 && timeMillis - faceDownSince > HOLD_MILLIS) {
            fired = true
            return true
        }
        return false
    }

    companion object {
        const val SMOOTHING = 0.8f
        const val STILL_TOLERANCE = 0.2f
        const val HOLD_MILLIS = 200L
        const val FACE_UP_Z = -1.5f
        const val FACE_DOWN_Z = -8.8f
    }
}

/**
 * Silences the ringer when the user turns the ringing phone face down, if they turned Flip To
 * Silence on. Listens to the accelerometer only while a call rings.
 */
class FlipToSilence(private val context: Context) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private var detector: FlipDetector? = null

    /** A call started ringing. */
    fun onRinging() {
        if (detector != null || !IncomingCallSettings.flipToSilence(context)) return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) return
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        detector = FlipDetector()
        sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    /** No call rings any more. */
    fun onRingingStopped() {
        if (detector == null) return
        detector = null
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        if (detector?.onReading(values[0], values[1], values[2], event.timestamp / NANOS_PER_MILLI) == true) {
            LogUtil.i("FlipToSilence.onSensorChanged", "phone turned face down, silencing the ringer")
            TelecomUtil.silenceRinger(context)
            onRingingStopped()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
