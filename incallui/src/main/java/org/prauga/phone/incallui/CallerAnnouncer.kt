/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import org.prauga.phone.incallui.R
import org.prauga.phone.dialer.app.settings.IncomingCallSettings
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.DialerCall

/**
 * Reads out who is calling while a call rings, if the user turned Caller ID announcement on, and
 * repeats it a few seconds after each time until the call stops ringing.
 */
class CallerAnnouncer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ringingCallId: String? = null
    private var text: String? = null

    /** The call whose ringer the user silenced; it isn't announced again while it rings. */
    private var silencedCallId: String? = null

    /** [call] started ringing. */
    fun onRinging(call: DialerCall, calls: CallList) {
        if (ringingCallId == call.id || silencedCallId == call.id) return
        stop()
        if (!shouldAnnounce(call, calls)) return
        ringingCallId = call.id
        text = announcementFor(call)
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) speak() else LogUtil.w("CallerAnnouncer", "text to speech unavailable")
        }.apply {
            setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit

                    override fun onDone(utteranceId: String) {
                        handler.postDelayed(::speak, REPEAT_DELAY_MILLIS)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) = Unit
                },
            )
        }
    }

    /** The user silenced the ringer, with the volume key or by flipping the phone over. */
    fun silence() {
        silencedCallId = ringingCallId
        stop()
    }

    /** No call rings any more. */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        ringingCallId = null
        text = null
    }

    private fun speak() {
        val engine = tts ?: return
        val words = text ?: return
        val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_RING) }
        engine.speak(words, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    private fun shouldAnnounce(call: DialerCall, calls: CallList): Boolean {
        val setting = IncomingCallSettings.announceCaller(context)
        if (setting == IncomingCallSettings.ANNOUNCE_NEVER) return false
        if (setting == IncomingCallSettings.ANNOUNCE_HEADSET && !headsetConnected()) return false
        if (calls.activeCall != null || calls.backgroundCall != null) return false
        if (call.isSilentRinging) return false
        if (call.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)) return false
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (notifications?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL } == true) return false
        return true
    }

    private fun announcementFor(call: DialerCall): String {
        if (call.isSpam) return context.getString(R.string.call_announcer_spam)
        val name = ContactInfoCache.getInstance(context).getInfo(call.id)?.namePrimary?.takeIf { it.isNotBlank() }
        val caller = name
            ?: call.number?.takeIf { it.isNotBlank() }?.let { PhoneNumberUtils.createTtsSpannable(it).toString() }
            ?: context.getString(R.string.unknown)
        val account = call.accountHandle?.let { TelecomUtil.getPhoneAccount(context, it) }?.label
        val canReadAccounts =
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val accounts = if (canReadAccounts) {
            context.getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts?.size ?: 1
        } else {
            1
        }
        return if (accounts > 1 && !account.isNullOrBlank()) {
            context.getString(R.string.call_announcer_text_multi_sim, caller, account)
        } else {
            context.getString(R.string.call_announcer_text, caller)
        }
    }

    private fun headsetConnected(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADSET_TYPES }
    }

    private companion object {
        const val UTTERANCE_ID = "caller_announcement"
        const val REPEAT_DELAY_MILLIS = 3_000L
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
