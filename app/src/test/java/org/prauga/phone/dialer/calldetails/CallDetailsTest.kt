/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import android.content.Intent
import android.provider.CallLog.Calls
import android.telecom.PhoneAccount
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.components.list.PvotAvatar
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callrecord.CallRecording
import org.prauga.phone.dialer.calllogutils.CallbackActionHelper.CallbackAction
import org.prauga.phone.dialer.compat.telephony.TelephonyManagerCompat
import org.prauga.phone.dialer.dialercontact.DialerContact
import org.prauga.phone.dialer.dialercontact.SimDetails
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.ui.SolidGlassTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What call details show for a contact's calls, and what its controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class CallDetailsTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // Call times show in the device's zone; pin it so the screenshots match on any machine.
    private val zone = TimeZone.getDefault()

    @Before
    fun pinTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restoreTimeZone() = TimeZone.setDefault(zone)

    private val contact = DialerContact.newBuilder()
        .setNameOrNumber("Alex Doe")
        .setNumber("+15551234567")
        .setDisplayNumber("+1 555-123-4567")
        .setNumberLabel("Mobile")
        .setSimDetails(SimDetails.newBuilder().setNetwork("Carrier").setColor(PhoneAccount.NO_HIGHLIGHT_COLOR))
        .build()

    private fun entry(id: Long, type: Int, features: Int = 0, duration: Long = 65, mappingId: String = "m$id") =
        CallDetailsEntries.CallDetailsEntry.newBuilder()
            .setCallId(id).setCallType(type).setFeatures(features).setDate(1_790_000_000_000L - id * 3_600_000)
            .setDuration(duration).setCallMappingId(mappingId).build()

    private val entries = CallDetailsEntries.newBuilder()
        .addEntries(entry(1, Calls.OUTGOING_TYPE))
        .addEntries(entry(2, Calls.MISSED_TYPE, duration = 0))
        .addEntries(entry(3, Calls.INCOMING_TYPE, features = Calls.FEATURES_RTT))
        .build()

    private fun map(
        contact: DialerContact = this.contact,
        entries: CallDetailsEntries = this.entries,
        emergency: Boolean = false,
        callback: Int = CallbackAction.VOICE,
        recordings: Map<Long, List<CallRecording>> = emptyMap(),
        transcripts: Set<String> = emptySet(),
    ) = CallDetailsMapper.map(
        contact = contact, entries = entries, isEmergencyNumber = emergency, callbackAction = callback,
        numberLabelAndNumber = { label, number -> "$label $number" }, emergencyTitle = "Emergency number",
        recordingsFor = { recordings[it.callId].orEmpty() }, rttTranscriptIds = transcripts,
    )

    @Test
    fun headerShowsNameWithLabelAndNumber() {
        val header = map().header
        assertThat(header.title).isEqualTo("Alex Doe")
        assertThat(header.subtitle).isEqualTo("Mobile +1 555-123-4567")
        assertThat(header.network).isEqualTo("Carrier")
        assertThat(header.networkColor).isNull()
    }

    @Test
    fun emergencyNumbersSayEmergencyAndHideTheNumber() {
        val header = map(emergency = true).header
        assertThat(header.title).isEqualTo("Emergency number")
        assertThat(header.subtitle).isNull()
    }

    @Test
    fun missedCallsHaveNoDuration() {
        val ui = map()
        assertThat(ui.entries.map { it.showsDuration }).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun rttTranscriptsComeFromTheAvailableIds() {
        val rtt = map(transcripts = setOf("m3")).entries.single { it.callId == 3L }
        assertThat(rtt.isRtt).isTrue()
        assertThat(rtt.hasRttTranscript).isTrue()
    }

    @Test
    fun assistedDialingFollowsTheMostRecentCall() {
        val assisted = CallDetailsEntries.newBuilder()
            .addEntries(entry(1, Calls.OUTGOING_TYPE, features = TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)).build()
        assertThat(map(entries = assisted).header.usedAssistedDialing).isTrue()
        assertThat(map().header.usedAssistedDialing).isFalse()
    }

    @Test
    fun countryCodeComesFromTheNumber() {
        assertThat(OldCallDetailsActivity.countryCodeOf("+919876543210")).isEqualTo(91)
        assertThat(OldCallDetailsActivity.countryCodeOf("not a number")).isEqualTo(0)
    }

    @Test
    fun aNumberThatCantBeCalledBackHasNoCallButton() {
        show(map(callback = CallbackAction.NONE))
        compose.onNodeWithTag(TestTags.CALL_DETAILS_CALL_BACK).assertDoesNotExist()
    }

    @Test
    fun callBackAndDeleteReachTheActivity() {
        val actions = RecordingActions()
        show(map(), actions)
        compose.onNodeWithTag(TestTags.CALL_DETAILS_CALL_BACK).performClick()
        compose.onNodeWithTag(TestTags.CALL_DETAILS_DELETE).performClick()
        // Deleting the whole history asks first.
        assertThat(actions.calls).containsExactly("callBack video=false")
        compose.onNode(hasText(context.getString(R.string.delete)) and hasAnyAncestor(isDialog())).performClick()
        assertThat(actions.calls).containsExactly("callBack video=false", "delete").inOrder()
    }

    @Test
    fun oneRecordingPlaysAtOnceAndSeveralOfferAChoice() {
        val one = recording(10)
        val two = listOf(recording(20), recording(30))
        val actions = RecordingActions()
        show(map(recordings = mapOf(1L to listOf(one), 3L to two)), actions)

        compose.onNodeWithTag(TestTags.callDetailsRecordings(1)).performClick()
        assertThat(actions.calls).containsExactly("play 10")

        compose.onNodeWithTag(TestTags.callDetailsRecordings(3)).performClick()
        compose.onNodeWithText("time 30").performClick()
        assertThat(actions.calls).containsExactly("play 10", "play 30").inOrder()
    }

    @Test
    fun rttCallWithoutTranscriptSaysSo() {
        show(map())
        compose.onNodeWithText(context.getString(R.string.rtt_transcript_not_available)).assertIsDisplayed()
    }

    @Test
    fun messageIsOfferedForANumberThatCanBeCalledBack() {
        assertThat(map().canMessage).isTrue()
        assertThat(map(callback = CallbackAction.NONE).canMessage).isFalse()
        assertThat(map(emergency = true).canMessage).isFalse()
        val voicemail = contact.toBuilder().setContactType(LetterTileDrawable.TYPE_VOICEMAIL).build()
        assertThat(map(contact = voicemail).canMessage).isFalse()
    }

    @Test
    fun messageReachesTheActivity() {
        val actions = RecordingActions()
        show(map(), actions)
        compose.onNodeWithTag(TestTags.CALL_DETAILS_MESSAGE).performClick()
        assertThat(actions.calls).containsExactly("message")
    }

    @Test
    fun theHeroShowsTheNameAndNumber() {
        show(map())
        compose.onNodeWithText("Alex Doe").assertIsDisplayed()
        compose.onNodeWithText("Mobile +1 555-123-4567").assertIsDisplayed()
    }

    @Test
    fun screenshotDark() = screenshot("call_details_dark")

    @Test
    @Config(qualifiers = "+notnight")
    fun screenshotLight() = screenshot("call_details_light")

    private fun screenshot(name: String) {
        show(map(recordings = mapOf(1L to listOf(recording(10))), transcripts = setOf("m3")))
        compose.onRoot().captureRoboImage("src/test/screenshots/calldetails/$name.png")
    }

    private fun recording(mediaId: Long) = CallRecording("+15551234567", 0, "rec$mediaId.m4a", mediaId, mediaId)

    private fun show(ui: CallDetailsUi, actions: CallDetailsActions = RecordingActions(), callWith: Intent? = null) {
        compose.setContent {
            SolidGlassTheme {
                CallDetailsScreen(ui, assistedDialingCountryCode = null, callWithIntent = callWith, actions = actions,
                    avatar = { PvotAvatar(name = "Alex Doe", size = it) })
            }
        }
    }

    /** Records what the screen asked for. */
    private class RecordingActions : CallDetailsActions {
        val calls = mutableListOf<String>()
        override fun back() { calls += "back" }
        override fun callBack(video: Boolean) { calls += "callBack video=$video" }
        override fun message() { calls += "message" }
        override fun copyNumber() { calls += "copy" }
        override fun editNumber() { calls += "edit" }
        override fun delete() { calls += "delete" }
        override fun deleteCall(entry: CallDetailsEntryUi) { calls += "deleteCall" }
        override fun showTranscript(entry: CallDetailsEntryUi) { calls += "transcript ${entry.callId}" }
        override fun play(recording: CallRecording) { calls += "play ${recording.mediaId}" }
        override fun recordingTime(recording: CallRecording) = "time ${recording.mediaId}"
    }
}
