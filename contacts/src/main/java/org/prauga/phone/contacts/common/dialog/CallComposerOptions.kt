/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.dialog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.OutcomeReceiver
import android.os.ParcelUuid
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.BundleCompat
import java.io.ByteArrayInputStream
import org.prauga.phone.contacts.R
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.telecom.TelecomUtil

/**
 * Call composer in the "call with a note" dialog: when the SIM supports it, the call can also be
 * marked urgent and carry a picture, which the network sends ahead of the call. The dialog shows
 * [urgent] and [picture] and calls [pickPicture]; create this in the activity's onCreate.
 */
class CallComposerOptions(
    private val activity: ComponentActivity,
    accountHandle: PhoneAccountHandle?,
) {

    private val handle = accountHandle ?: defaultAccount(activity)

    /** Whether the SIM the call goes out on supports call composer. */
    val isSupported: Boolean = handle != null && supportsComposer(activity, handle)

    /** Whether the call goes out marked urgent. */
    var urgent by mutableStateOf(false)

    /** The picture to send with the call, if the user chose one. */
    var picture by mutableStateOf<Uri?>(null)
        private set

    private var sending = false

    private val picker =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            picture = uri
        }

    /** Keeps the choices across a recreation; [restore] reads them back. */
    fun save(outState: Bundle) {
        outState.putBoolean(STATE_URGENT, urgent)
        outState.putParcelable(STATE_PICTURE, picture)
    }

    /** Reads back what [save] kept. */
    fun restore(state: Bundle?) {
        if (state == null) return
        urgent = state.getBoolean(STATE_URGENT, false)
        picture = BundleCompat.getParcelable(state, STATE_PICTURE, Uri::class.java)
    }

    /** Lets the user choose a picture to send with the call. */
    fun pickPicture() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /**
     * Adds what the user chose to [builder] and then calls [place]; a picture is uploaded first,
     * and the call is placed without it if the upload fails. Returns false while an upload runs.
     */
    fun send(builder: CallIntentBuilder, place: Runnable): Boolean {
        if (sending) return false
        if (!isSupported) {
            place.run()
            return true
        }
        if (urgent) {
            builder.placeCallExtras.putInt(
                TelecomManager.EXTRA_PRIORITY,
                TelecomManager.PRIORITY_URGENT,
            )
        }
        val chosen = picture
        if (chosen == null) {
            place.run()
            return true
        }
        sending = true
        upload(chosen) { token ->
            if (token != null) {
                builder.placeCallExtras.putParcelable(TelecomManager.EXTRA_OUTGOING_PICTURE, token)
            } else {
                Toast.makeText(activity, R.string.call_composer_picture_failed, Toast.LENGTH_SHORT)
                    .show()
            }
            place.run()
        }
        return true
    }

    private fun upload(picture: Uri, done: (ParcelUuid?) -> Unit) {
        val telephony =
            activity
                .getSystemService(TelephonyManager::class.java)
                ?.createForPhoneAccountHandle(handle)
        if (telephony == null) {
            done(null)
            return
        }
        val receiver =
            object : OutcomeReceiver<ParcelUuid, TelephonyManager.CallComposerException> {
                override fun onResult(result: ParcelUuid) = done(result)

                override fun onError(error: TelephonyManager.CallComposerException) {
                    LogUtil.e(
                        "CallComposerOptions.upload",
                        "upload failed: ${error.errorCode}",
                        error,
                    )
                    done(null)
                }
            }
        // Shrinking a photo takes a moment; the network takes only small pictures.
        Thread(
                {
                    val bytes =
                        ComposerPicture.shrink(
                            activity,
                            picture,
                            TelephonyManager.getMaximumCallComposerPictureSize(),
                        )
                    activity.mainExecutor.execute {
                        // The dialog may have been closed meanwhile; then there is no call to
                        // place.
                        if (activity.isFinishing || activity.isDestroyed) return@execute
                        if (bytes == null) {
                            LogUtil.e(
                                "CallComposerOptions.upload",
                                "can't make the picture small enough",
                            )
                            done(null)
                            return@execute
                        }
                        runCatching {
                                telephony.uploadCallComposerPicture(
                                    ByteArrayInputStream(bytes),
                                    PICTURE_TYPE,
                                    activity.mainExecutor,
                                    receiver,
                                )
                            }
                            .onFailure {
                                LogUtil.e("CallComposerOptions.upload", "can't upload", it)
                                done(null)
                            }
                    }
                },
                "composer-picture",
            )
            .start()
    }

    companion object {
        private const val PICTURE_TYPE = "image/jpeg"
        private const val STATE_URGENT = "composer_urgent"
        private const val STATE_PICTURE = "composer_picture"

        /** Whether the SIM [handle] stands for supports call composer. */
        @JvmStatic
        fun supportsComposer(context: Context, handle: PhoneAccountHandle): Boolean =
            TelecomUtil.getPhoneAccount(context, handle)
                ?.hasCapabilities(PhoneAccount.CAPABILITY_CALL_COMPOSER) == true

        private fun defaultAccount(context: Context): PhoneAccountHandle? {
            if (
                context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            return context
                .getSystemService(TelecomManager::class.java)
                ?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        }
    }
}
