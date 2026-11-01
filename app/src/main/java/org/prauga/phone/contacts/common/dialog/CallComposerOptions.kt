/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.dialog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.OutcomeReceiver
import android.os.ParcelUuid
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.telecom.TelecomUtil
import java.io.ByteArrayInputStream

/**
 * Call composer in the "call with a note" dialog: when the SIM supports it, the call can also be
 * marked urgent and carry a picture, which the network sends ahead of the call.
 *
 * Create it in the activity's onCreate, after its content view is set.
 */
class CallComposerOptions(
    private val activity: AppCompatActivity,
    accountHandle: PhoneAccountHandle?,
) {

    private val handle = accountHandle ?: defaultAccount(activity)

    /** Whether the SIM the call goes out on supports call composer. */
    val isSupported: Boolean = handle != null && supportsComposer(activity, handle)

    private val urgent: CheckBox = activity.findViewById(R.id.composer_urgent)
    private val addPicture: TextView = activity.findViewById(R.id.composer_add_picture)
    private val preview: ImageView = activity.findViewById(R.id.composer_picture_preview)
    private var picture: Uri? = null
    private var sending = false

    private val pickPicture = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        picture = uri
        preview.setImageURI(uri)
        preview.visibility = if (uri == null) View.GONE else View.VISIBLE
        addPicture.setText(if (uri == null) R.string.call_composer_add_picture else R.string.call_composer_change_picture)
    }

    init {
        activity.findViewById<View>(R.id.composer_options).visibility = if (isSupported) View.VISIBLE else View.GONE
        addPicture.setOnClickListener {
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
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
        if (urgent.isChecked) {
            builder.placeCallExtras.putInt(TelecomManager.EXTRA_PRIORITY, TelecomManager.PRIORITY_URGENT)
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
                Toast.makeText(activity, R.string.call_composer_picture_failed, Toast.LENGTH_SHORT).show()
            }
            place.run()
        }
        return true
    }

    private fun upload(picture: Uri, done: (ParcelUuid?) -> Unit) {
        val telephony = activity.getSystemService(TelephonyManager::class.java)?.createForPhoneAccountHandle(handle)
        if (telephony == null) {
            done(null)
            return
        }
        val receiver = object : OutcomeReceiver<ParcelUuid, TelephonyManager.CallComposerException> {
            override fun onResult(result: ParcelUuid) = done(result)

            override fun onError(error: TelephonyManager.CallComposerException) {
                LogUtil.e("CallComposerOptions.upload", "upload failed: ${error.errorCode}", error)
                done(null)
            }
        }
        // Shrinking a photo takes a moment; the network takes only small pictures.
        Thread({
            val bytes = ComposerPicture.shrink(activity, picture, TelephonyManager.getMaximumCallComposerPictureSize())
            activity.mainExecutor.execute {
                if (bytes == null) {
                    LogUtil.e("CallComposerOptions.upload", "can't make the picture small enough")
                    done(null)
                    return@execute
                }
                runCatching {
                    telephony.uploadCallComposerPicture(ByteArrayInputStream(bytes), PICTURE_TYPE, activity.mainExecutor, receiver)
                }.onFailure {
                    LogUtil.e("CallComposerOptions.upload", "can't upload", it)
                    done(null)
                }
            }
        }, "composer-picture").start()
    }

    companion object {
        private const val PICTURE_TYPE = "image/jpeg"

        /** Whether the SIM [handle] stands for supports call composer. */
        @JvmStatic
        fun supportsComposer(context: Context, handle: PhoneAccountHandle): Boolean =
            TelecomUtil.getPhoneAccount(context, handle)?.hasCapabilities(PhoneAccount.CAPABILITY_CALL_COMPOSER) == true

        private fun defaultAccount(context: Context): PhoneAccountHandle? {
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            return context.getSystemService(TelecomManager::class.java)
                ?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        }
    }
}
