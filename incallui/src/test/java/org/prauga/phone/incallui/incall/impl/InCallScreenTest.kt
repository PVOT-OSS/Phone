/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.incall.impl

import android.content.Context
import android.telecom.CallAudioState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.prauga.pvot.designsystem.theme.PvotSpacing
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.call.state.DialerCallState
import org.prauga.phone.incallui.contactgrid.ContactGrid
import org.prauga.phone.incallui.contactgrid.ContactGridState
import org.prauga.phone.incallui.incall.protocol.InCallButtonIds
import org.prauga.phone.incallui.incall.protocol.InCallButtonUiDelegate
import org.prauga.phone.incallui.incall.protocol.PrimaryCallState
import org.prauga.phone.incallui.incall.protocol.PrimaryInfo
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** The voice call screen: the control capsule, the More sheet, the End pill and the contact grid. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class InCallScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()

    /** Records each call the buttons make on the delegate, by method name and arguments. */
    private val delegate = Proxy.newProxyInstance(
        InCallButtonUiDelegate::class.java.classLoader,
        arrayOf(InCallButtonUiDelegate::class.java),
    ) { _, method, args ->
        calls += listOf(method.name).plus(args?.map { it.toString() }.orEmpty()).joinToString(" ")
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            CallAudioState::class.java -> CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE)
            Context::class.java -> context
            else -> null
        }
    } as InCallButtonUiDelegate

    @Test
    fun aCheckableButtonAsksForTheOppositeStateAndWaits() {
        val button = CheckableLabeledButton(context)
        var asked: Boolean? = null
        button.setOnCheckedChangeListener { _, checked -> asked = checked }
        button.performClick()
        assertThat(asked).isTrue()
        assertThat(button.isChecked()).isFalse()
    }

    @Test
    fun aPlainButtonReportsAClick() {
        val button = CheckableLabeledButton(context)
        var clicked = false
        button.setOnClickListener { clicked = true }
        button.performClick()
        assertThat(clicked).isTrue()
    }

    private fun controllers(): List<ButtonController> = listOf(
        ButtonController.MuteButtonController(delegate),
        ButtonController.SpeakerButtonController(delegate),
        ButtonController.DialpadButtonController(delegate),
        ButtonController.HoldButtonController(delegate),
        ButtonController.AddCallButtonController(delegate),
    ).onEach {
        it.isAllowed = true
        it.isEnabled = true
    }

    private var ended = 0

    /** More buttons than the panel holds, so the rest go to the More sheet. */
    private fun manyControllers(): List<ButtonController> = controllers() + listOf(
        ButtonController.UpgradeToVideoButtonController(delegate),
        ButtonController.MergeButtonController(delegate),
        ButtonController.SwapSimButtonController(delegate),
    ).onEach {
        it.isAllowed = true
        it.isEnabled = true
    }

    private fun showControls(controllers: List<ButtonController> = controllers()): Pair<InCallButtonSlots, List<ButtonController>> {
        val slots = InCallButtonSlots(context)
        slots.place(controllers, null, 0, 0)
        compose.setContent { PhoneScreenTheme { InCallControls(slots, endCallEnabled = true, onEndCall = { ended++ }) } }
        return slots to controllers
    }

    @Test
    fun aFewButtonsAllShowOnThePanelWithTheirLabels() {
        val (slots, _) = showControls()
        compose.onNode(hasTestTag(TestTags.INCALL_CAPSULE)).assertExists()
        compose.onNodeWithTag(muteTag(slots)).assertExists()
        compose.onNodeWithContentDescription("Keypad").assertExists()
        compose.onNodeWithText("Hold").assertExists()
        compose.onNodeWithText("Add call").assertExists()
        compose.onNodeWithTag(TestTags.INCALL_MORE).assertDoesNotExist()
    }

    @Test
    fun tappingALabelRunsItsButton() {
        showControls()
        compose.onNodeWithText("Hold").performClick()
        assertThat(calls).contains("holdClicked true")
    }

    @Test
    fun whenTheyDontFitMoreHoldsTheRest() {
        showControls(manyControllers())
        compose.onNodeWithContentDescription("Keypad").assertExists()
        compose.onNodeWithTag(TestTags.INCALL_MORE).performClick()
        // The sheet lists what isn't on the panel.
        compose.onAllNodes(hasAnyAncestor(isDialog())).fetchSemanticsNodes().let { assertThat(it).isNotEmpty() }
    }

    @Test
    fun tappingMuteAsksTheDelegateAndShowsItOn() {
        val (slots, controllers) = showControls()
        compose.onNodeWithTag(muteTag(slots)).performClick()
        assertThat(calls).contains("muteClicked true true")
        compose.runOnIdle { controllers.first { it is ButtonController.MuteButtonController }.setChecked(true) }
        compose.onNodeWithTag(muteTag(slots)).assertIsOn()
    }

    private fun muteTag(slots: InCallButtonSlots) =
        TestTags.incallButton(slots.ids.indexOf(InCallButtonIds.BUTTON_MUTE))

    @Test
    fun theKeypadButtonOpensTheDialpad() {
        showControls()
        compose.onNodeWithContentDescription("Keypad").performClick()
        assertThat(calls).contains("showDialpadClicked true")
    }

    @Test
    fun anActionInMoreRunsAndClosesTheSheet() {
        val (slots, _) = showControls(manyControllers())
        compose.onNodeWithTag(TestTags.INCALL_MORE).performClick()
        val inSheet = slots.ids.indices.last { slots.ids[it] != InCallButtonSlots.NO_BUTTON && slots.buttons[it].visible }
        val label = slots.buttons[inSheet].label.toString()
        compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasAnyAncestor(isDialog()) and hasText(label)).assertDoesNotExist()
    }

    @Test
    fun thePanelKeepsAudioMuteAndKeypadFirstAndMoreLast() {
        val ids = mapOf(0 to InCallButtonIds.BUTTON_HOLD, 1 to InCallButtonIds.BUTTON_MUTE, 2 to InCallButtonIds.BUTTON_AUDIO,
            3 to InCallButtonIds.BUTTON_DIALPAD, 4 to InCallButtonIds.BUTTON_ADD_CALL, 5 to InCallButtonIds.BUTTON_MERGE,
            6 to InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO)
        val layout = ControlsLayout.of(ids.keys.toList()) { ids.getValue(it) }
        assertThat(layout.panel).containsExactly(2, 1, 3, 0, 4).inOrder()
        assertThat(layout.more).containsExactly(5, 6).inOrder()
        val few = ControlsLayout.of(listOf(0, 1, 2, 3, 4, 5)) { ids.getValue(it) }
        assertThat(few.more).isEmpty()
    }

    @Test
    fun theEndPillSaysEndAndEndsTheCall() {
        showControls()
        compose.onNodeWithText("End", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TestTags.INCALL_END_CALL).performClick()
        assertThat(ended).isEqualTo(1)
    }

    private fun gridState(): ContactGridState = ContactGridState(context).apply {
        setPrimary(PrimaryInfo.empty().let { PrimaryInfo.builder().setName("Ada Lovelace").setNameIsNumber(false).setPhotoType(0).setIsSipCall(false).setIsContactPhotoShown(false).setIsWorkCall(false).setIsSpam(false).setIsLocalContact(true).setAnsweringDisconnectsOngoingCall(false).setShowInCallButtonGrid(true).setNumberPresentation(1).setNumber("+15550100").build() })
        setCallState(PrimaryCallState.builder().setState(DialerCallState.ACTIVE).setConnectTimeMillis(System.currentTimeMillis() - 65_000).build())
    }

    @Test
    fun anActiveCallShowsTheNameAndATimer() {
        val state = gridState()
        compose.setContent { PhoneScreenTheme { ContactGrid(state) } }
        compose.onNodeWithTag(TestTags.CONTACT_GRID_NAME).assertExists()
        compose.onNodeWithText("Ada Lovelace").assertExists()
        assertThat(state.connectTimeMillis).isNotNull()
        compose.onNodeWithText("01:05").assertExists()
    }

    @Test
    fun screenshotDark() {
        val state = gridState()
        val slots = InCallButtonSlots(context)
        val controllers = controllers()
        slots.place(controllers, null, 0, 0)
        controllers.first { it is ButtonController.MuteButtonController }.setChecked(true)
        compose.setContent {
            PhoneScreenTheme {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    ContactGrid(state)
                    InCallControls(slots, endCallEnabled = true, onEndCall = {}, modifier = Modifier.padding(bottom = PvotSpacing.L))
                }
            }
        }
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/incall/call_screen_dark.png")
    }
}
