/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.testing

import android.net.Uri
import android.telecom.Connection
import android.telecom.VideoProfile
import android.view.Surface

/**
 * The video side of a fake carrier call. Nothing is streamed: the far end only answers and makes
 * session modify requests, and the call's video state follows what both sides agreed.
 */
class FakeVideoProvider(private val connection: FakeConnection) : Connection.VideoProvider() {

    /** The last change the user asked the far end for, until the far end answers it. */
    @Volatile
    var pendingRequest: VideoProfile? = null
        private set

    /** The user's answer to the far end's last request. */
    @Volatile
    var lastResponse: VideoProfile? = null
        private set

    /** The camera the user's side chose; null when the camera is off. */
    @Volatile
    var cameraId: String? = null
        private set

    override fun onSendSessionModifyRequest(fromProfile: VideoProfile?, toProfile: VideoProfile) {
        pendingRequest = toProfile
    }

    override fun onSendSessionModifyResponse(responseProfile: VideoProfile) {
        lastResponse = responseProfile
        connection.setVideoState(responseProfile.videoState)
    }

    /** The far end agrees to the user's pending request. */
    fun acceptRequest() = FakeCarrier.onMain {
        val request = checkNotNull(pendingRequest) { "no pending request" }
        pendingRequest = null
        receiveSessionModifyResponse(SESSION_MODIFY_REQUEST_SUCCESS, request, request)
        connection.setVideoState(request.videoState)
    }

    /** The far end turns the user's pending request down. */
    fun rejectRequest() = FakeCarrier.onMain {
        val request = checkNotNull(pendingRequest) { "no pending request" }
        pendingRequest = null
        receiveSessionModifyResponse(SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE, request, VideoProfile(connection.videoState))
    }

    /** The far end asks to change the call to [videoState], for example to upgrade it to video. */
    fun request(videoState: Int) = FakeCarrier.onMain { receiveSessionModifyRequest(VideoProfile(videoState)) }

    /** The far end drops video; the call carries on as a voice call. */
    fun downgradeToAudio() = FakeCarrier.onMain {
        receiveSessionModifyRequest(VideoProfile(VideoProfile.STATE_AUDIO_ONLY))
        connection.setVideoState(VideoProfile.STATE_AUDIO_ONLY)
    }

    override fun onSetCamera(cameraId: String?) {
        this.cameraId = cameraId
    }

    override fun onSetPreviewSurface(surface: Surface?) = Unit

    override fun onSetDisplaySurface(surface: Surface?) = Unit

    override fun onSetDeviceOrientation(rotation: Int) = Unit

    override fun onSetZoom(value: Float) = Unit

    override fun onSetPauseImage(uri: Uri?) = Unit

    override fun onRequestCameraCapabilities() = Unit

    override fun onRequestConnectionDataUsage() = setCallDataUsage(0)
}
