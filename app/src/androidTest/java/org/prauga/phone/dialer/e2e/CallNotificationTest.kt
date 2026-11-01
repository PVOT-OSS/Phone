/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.notification.NotificationChannelId
import org.prauga.phone.dialer.testing.FakeCallSpec
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.dialer.testing.FakeConnection
import org.prauga.phone.incallui.audiomode.AudioModeProvider
import org.prauga.phone.incallui.call.state.DialerCallState

/** The call notification: Android's call style, its buttons and its text. */
@RunWith(AndroidJUnit4::class)
class CallNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(deviceRule)

    @Test
    fun ringingCallUsesTheIncomingCallStyle() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        CallTests.waitForRinging()

        val notification = callNotification(NotificationChannelId.INCOMING_CALL) { it.callType == CALL_TYPE_INCOMING }
        assertNotNull("no answer button", notification.extras.getParcelable(Notification.EXTRA_ANSWER_INTENT, PendingIntent::class.java))
        assertNotNull("no decline button", notification.extras.getParcelable(Notification.EXTRA_DECLINE_INTENT, PendingIntent::class.java))
        check(!notification.extras.getBoolean(Notification.EXTRA_CALL_IS_VIDEO)) { "voice call marked as video" }
        assertNotNull("no full screen intent", notification.fullScreenIntent)
        assertEquals(Notification.CATEGORY_CALL, notification.category)
        @Suppress("DEPRECATION")
        assertEquals(Notification.PRIORITY_MAX, notification.priority)
    }

    @Test
    fun callRingingRightAfterAnotherEndsGetsItsNotification() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        CallTests.waitForRinging().hangUpFromFarEnd()
        InCall.waitForNoCalls()

        // Telecom still has the app bound from the first call when the second one rings.
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        CallTests.waitForRinging()

        callNotification(NotificationChannelId.INCOMING_CALL) { it.callType == CALL_TYPE_INCOMING }
    }

    @Test
    fun ringingVideoCallIsMarkedAsVideo() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), videoState = VideoProfile.STATE_BIDIRECTIONAL))
        CallTests.waitForRinging()

        callNotification(NotificationChannelId.INCOMING_CALL) {
            it.callType == CALL_TYPE_INCOMING && it.extras.getBoolean(Notification.EXTRA_CALL_IS_VIDEO)
        }
    }

    @Test
    fun answeringFromTheNotificationOpensTheCallScreen() {
        val number = CallTests.freshNumber()
        FakeCarrier.ring(context, FakeCallSpec(number))
        val far = CallTests.waitForRinging()
        val notification = callNotification(NotificationChannelId.INCOMING_CALL) { it.callType == CALL_TYPE_INCOMING }

        checkNotNull(notification.extras.getParcelable(Notification.EXTRA_ANSWER_INTENT, PendingIntent::class.java)).send()

        CallTests.waitFor { far.takeIf { it.state == Connection.STATE_ACTIVE } }
        assertNotNull(
            "call screen not shown",
            deviceRule.device.wait(Until.findObject(By.pkg(context.packageName).textContains(number.takeLast(4))), CallTests.TIMEOUT_MILLIS),
        )
    }

    @Test
    fun dialingCallUsesTheOngoingCallStyleAndSaysCalling() {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)

        val notification = callNotification(NotificationChannelId.ONGOING_CALL) { it.callType == CALL_TYPE_ONGOING }
        assertEquals(status(R.string.notification_dialing), notification.text)
    }

    @Test
    fun activeCallUsesTheOngoingCallStyleWithItsButtons() {
        connectedCall()

        val notification = callNotification(NotificationChannelId.ONGOING_CALL) {
            it.callType == CALL_TYPE_ONGOING && it.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        }
        assertNotNull("no hang up button", notification.extras.getParcelable(Notification.EXTRA_HANG_UP_INTENT, PendingIntent::class.java))
        assertNotNull("no speaker button", notification.action("Speaker"))
        assertNotNull("no mute button", notification.action("Mute"))
        @Suppress("DEPRECATION")
        assertEquals(Notification.PRIORITY_HIGH, notification.priority)
    }

    @Test
    fun hangingUpFromTheNotificationEndsTheCall() {
        val far = connectedCall()
        val notification = callNotification(NotificationChannelId.ONGOING_CALL) { it.callType == CALL_TYPE_ONGOING }

        checkNotNull(notification.extras.getParcelable(Notification.EXTRA_HANG_UP_INTENT, PendingIntent::class.java)).send()

        InCall.waitForNoCalls()
        assertEquals(DisconnectCause.LOCAL, far.disconnectCause.code)
    }

    @Test
    fun muteAndUnmuteFromTheNotification() {
        connectedCall()

        sendAction("Mute")
        CallTests.waitFor { if (audioState()?.isMuted == true) true else null }

        sendAction("Unmute")
        CallTests.waitFor { if (audioState()?.isMuted == false) true else null }
    }

    @Test
    fun speakerFromTheNotification() {
        connectedCall()

        sendAction("Speaker")
        CallTests.waitFor { if (audioState()?.route == CallAudioState.ROUTE_SPEAKER) true else null }

        sendAction("Speaker")
        CallTests.waitFor { if (audioState()?.route != CallAudioState.ROUTE_SPEAKER) true else null }
    }

    @Test
    fun heldCallCanBeResumedFromTheNotification() {
        val far = connectedCall()
        val call = InCall.waitForState(DialerCallState.ACTIVE)
        InCall.onMain { call.hold() }
        InCall.waitForState(DialerCallState.ONHOLD)
        callNotification(NotificationChannelId.ONGOING_CALL) {
            it.text == status(R.string.notification_on_hold)
        }

        sendAction("Unhold")
        CallTests.waitFor { far.takeIf { it.state == Connection.STATE_ACTIVE } }
    }

    @Test
    fun ringingCallTextsFollowTheCall() {
        ringAndExpect(FakeCallSpec(CallTests.freshNumber()), context.getString(R.string.notification_incoming_call))
        ringAndExpect(FakeCallSpec(CallTests.freshNumber(), rtt = true), context.getString(R.string.notification_incoming_rtt_call))
        ringAndExpect(
            FakeCallSpec(CallTests.freshNumber(), videoState = VideoProfile.STATE_BIDIRECTIONAL),
            context.getString(R.string.notification_incoming_video_call),
        )
        ringAndExpect(
            FakeCallSpec(CallTests.freshNumber(), properties = Connection.PROPERTY_WIFI),
            context.getString(R.string.notification_incoming_call_wifi_template, wifiBrand()),
        )
    }

    @Test
    fun activeCallTexts() {
        connectedCall()
        callNotification(NotificationChannelId.ONGOING_CALL) { it.text == status(R.string.notification_ongoing_call) }
    }

    @Test
    fun activeWifiCallSaysSoAndUsesTheWifiIcon() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), properties = Connection.PROPERTY_WIFI))
        CallTests.waitForRinging()
        CallTests.answerRingingCall()

        val notification = callNotification(NotificationChannelId.ONGOING_CALL) {
            it.text == status(context.getString(R.string.notification_ongoing_call_wifi_template, wifiBrand()))
        }
        assertEquals(R.drawable.ic_wifi_calling, notification.smallIcon.resId)
    }

    @Test
    fun verifiedCallerGetsTheCheck() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber(), verificationStatus = Connection.VERIFICATION_STATUS_PASSED))
        CallTests.waitForRinging()

        callNotification(NotificationChannelId.INCOMING_CALL) {
            it.callType == CALL_TYPE_INCOMING && it.extras.getParcelable(Notification.EXTRA_VERIFICATION_ICON, Icon::class.java) != null
        }
    }

    @Test
    fun unverifiedCallerGetsNoCheck() {
        FakeCarrier.ring(context, FakeCallSpec(CallTests.freshNumber()))
        CallTests.waitForRinging()

        val notification = callNotification(NotificationChannelId.INCOMING_CALL) { it.callType == CALL_TYPE_INCOMING }
        check(notification.extras.getParcelable(Notification.EXTRA_VERIFICATION_ICON, Icon::class.java) == null) { "unverified caller marked verified" }
    }

    private fun ringAndExpect(spec: FakeCallSpec, text: String) {
        FakeCarrier.ring(context, spec)
        val far = CallTests.waitForRinging()
        callNotification(NotificationChannelId.INCOMING_CALL) { it.text == status(text) }
        far.hangUpFromFarEnd()
        InCall.waitForNoCalls()
    }

    /** [text] as the notification shows it: with the calling account named when there is more than one. */
    private fun status(text: String): String {
        val accounts = context.getSystemService(TelecomManager::class.java).callCapablePhoneAccounts
        return if (accounts.size > 1) context.getString(R.string.notification_status_via_account, text, "Fake carrier") else text
    }

    private fun status(textId: Int): String = status(context.getString(textId))

    private fun wifiBrand(): String = context.getString(R.string.notification_call_wifi_brand)

    private val Notification.text: String? get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun connectedCall(): FakeConnection {
        InCall.place(context, CallTests.freshNumber())
        InCall.waitForState(DialerCallState.DIALING)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        far.answerFromFarEnd()
        InCall.waitForState(DialerCallState.ACTIVE)
        return far
    }

    /** The current call notification on [channel] once it matches [check]. */
    private fun callNotification(channel: String, check: (Notification) -> Boolean): Notification = try {
        CallTests.waitFor {
            notifications.activeNotifications.map { it.notification }.firstOrNull { it.channelId == channel && check(it) }
        }
    } catch (e: IllegalStateException) {
        val seen = notifications.activeNotifications.joinToString { n ->
            "[${n.notification.channelId} type=${n.notification.callType} text=${n.notification.extras.getCharSequence(Notification.EXTRA_TEXT)}]"
        }
        throw IllegalStateException("no matching notification on $channel; showing: $seen", e)
    }

    /** Presses the call notification's button labelled [title], waiting for it to appear. */
    private fun sendAction(title: String) {
        val action = CallTests.waitFor {
            notifications.activeNotifications.firstNotNullOfOrNull { it.notification.action(title) }
        }
        action.actionIntent.send()
    }

    private fun Notification.action(title: String): Notification.Action? =
        actions?.firstOrNull { it.title?.toString().equals(title, ignoreCase = true) }

    private val Notification.callType: Int get() = extras.getInt(Notification.EXTRA_CALL_TYPE)

    private fun audioState(): CallAudioState? = InCall.onMain { AudioModeProvider.getInstance().audioState }

    private companion object {
        // Notification.CallStyle.CALL_TYPE_INCOMING / CALL_TYPE_ONGOING
        const val CALL_TYPE_INCOMING = 1
        const val CALL_TYPE_ONGOING = 2
    }
}
