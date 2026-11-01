/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.telecom.VideoProfile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.DialerCall
import org.prauga.phone.incallui.call.state.DialerCallState
import org.prauga.phone.incallui.videotech.utils.SessionModificationState

@RunWith(AndroidJUnit4::class)
class VideoCallTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(DeviceRule())

    @Test
    fun answerVideoCallWithVideo() {
        val far = ringWithVideo()

        InCall.onMain { CallList.getInstance().incomingCall.answer(VideoProfile.STATE_BIDIRECTIONAL) }

        val call = InCall.waitForState(DialerCallState.ACTIVE)
        waitForVideoState(far, VideoProfile.STATE_BIDIRECTIONAL)
        CallTests.waitFor { if (InCall.onMain { call.isVideoCall }) true else null }
    }

    @Test
    fun answerVideoCallWithVoiceOnly() {
        val far = ringWithVideo()

        InCall.onMain { CallList.getInstance().incomingCall.answer(VideoProfile.STATE_AUDIO_ONLY) }

        val call = InCall.waitForState(DialerCallState.ACTIVE)
        waitForVideoState(far, VideoProfile.STATE_AUDIO_ONLY)
        CallTests.waitFor { if (InCall.onMain { !call.isVideoCall }) true else null }
    }

    @Test
    fun upgradeToVideoAcceptedByTheFarEnd() {
        val (call, far) = connectedVoiceCall()

        InCall.onMain { call.videoTech.upgradeToVideo(context) }
        CallTests.waitFor { far.video.pendingRequest }
        waitForSessionState(call, SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE)

        far.video.acceptRequest()
        waitForVideoState(far, VideoProfile.STATE_BIDIRECTIONAL)
        CallTests.waitFor { if (InCall.onMain { call.isVideoCall }) true else null }
        waitForSessionState(call, SessionModificationState.NO_REQUEST)
    }

    @Test
    fun upgradeToVideoTurnedDownByTheFarEnd() {
        val (call, far) = connectedVoiceCall()

        InCall.onMain { call.videoTech.upgradeToVideo(context) }
        CallTests.waitFor { far.video.pendingRequest }

        far.video.rejectRequest()
        waitForSessionState(call, SessionModificationState.REQUEST_REJECTED)
        check(!InCall.onMain { call.isVideoCall }) { "call became a video call" }
    }

    @Test
    fun farEndAsksForVideoAndTheUserAccepts() {
        val (call, far) = connectedVoiceCall()

        far.video.request(VideoProfile.STATE_BIDIRECTIONAL)
        waitForSessionState(call, SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST)

        InCall.onMain { call.videoTech.acceptVideoRequest(context) }
        waitForVideoState(far, VideoProfile.STATE_BIDIRECTIONAL)
        CallTests.waitFor { if (InCall.onMain { call.isVideoCall }) true else null }
    }

    @Test
    fun farEndAsksForVideoAndTheUserDeclines() {
        val (call, far) = connectedVoiceCall()

        far.video.request(VideoProfile.STATE_BIDIRECTIONAL)
        waitForSessionState(call, SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST)

        InCall.onMain { call.videoTech.declineVideoRequest() }
        CallTests.waitFor { far.video.lastResponse?.takeIf { it.videoState == VideoProfile.STATE_AUDIO_ONLY } }
        waitForSessionState(call, SessionModificationState.NO_REQUEST)
        check(far.videoState == VideoProfile.STATE_AUDIO_ONLY) { "far end video state ${far.videoState}" }
    }

    @Test
    fun farEndDropsVideo() {
        val far = ringWithVideo()
        InCall.onMain { CallList.getInstance().incomingCall.answer(VideoProfile.STATE_BIDIRECTIONAL) }
        val call = InCall.waitForState(DialerCallState.ACTIVE)
        CallTests.waitFor { if (InCall.onMain { call.isVideoCall }) true else null }

        far.video.downgradeToAudio()

        CallTests.waitFor { if (InCall.onMain { !call.isVideoCall }) true else null }
        InCall.waitForState(DialerCallState.ACTIVE)
    }

    @Test
    fun turnCameraOffAndOnAgain() {
        val far = ringWithVideo()
        InCall.onMain { CallList.getInstance().incomingCall.answer(VideoProfile.STATE_BIDIRECTIONAL) }
        val call = InCall.waitForState(DialerCallState.ACTIVE)
        waitForVideoState(far, VideoProfile.STATE_BIDIRECTIONAL)

        InCall.onMain { call.videoTech.stopTransmission() }
        CallTests.waitFor { far.video.pendingRequest?.takeIf { it.videoState == VideoProfile.STATE_RX_ENABLED } }
        far.video.acceptRequest()
        waitForVideoState(far, VideoProfile.STATE_RX_ENABLED)
        CallTests.waitFor { if (InCall.onMain { !call.videoTech.isTransmitting }) true else null }

        InCall.onMain { call.videoTech.resumeTransmission(context) }
        CallTests.waitFor { far.video.pendingRequest?.takeIf { it.videoState == VideoProfile.STATE_BIDIRECTIONAL } }
        far.video.acceptRequest()
        waitForVideoState(far, VideoProfile.STATE_BIDIRECTIONAL)
        CallTests.waitFor { if (InCall.onMain { call.videoTech.isTransmitting }) true else null }
    }

    private fun ringWithVideo(): FakeConnection {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), videoState = VideoProfile.STATE_BIDIRECTIONAL))
        return CallTests.waitForRinging()
    }

    private fun connectedVoiceCall(): Pair<DialerCall, FakeConnection> {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        far.answerFromFarEnd()
        return InCall.waitForState(DialerCallState.ACTIVE) to far
    }

    private fun waitForVideoState(far: FakeConnection, videoState: Int) =
        CallTests.waitFor { if (far.videoState == videoState) true else null }

    private fun waitForSessionState(call: DialerCall, state: Int) =
        CallTests.waitFor { if (InCall.onMain { call.videoTech.sessionModificationState } == state) true else null }
}
