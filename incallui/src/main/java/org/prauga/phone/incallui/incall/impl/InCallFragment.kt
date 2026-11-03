/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023-2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prauga.phone.incallui.incall.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.CallAudioState
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.WindowBackdrop
import org.prauga.phone.incallui.InCallPresenter
import org.prauga.phone.incallui.R
import org.prauga.phone.incallui.audioroute.AudioRouteSelectorDialogFragment
import org.prauga.phone.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter
import org.prauga.phone.incallui.contactgrid.ContactGrid
import org.prauga.phone.incallui.contactgrid.ContactGridState
import org.prauga.phone.incallui.hold.OnHoldBanner
import org.prauga.phone.incallui.incall.impl.ButtonController.CallRecordButtonController
import org.prauga.phone.incallui.incall.impl.ButtonController.SpeakerButtonController
import org.prauga.phone.incallui.incall.impl.ButtonController.UpgradeToRttButtonController
import org.prauga.phone.incallui.incall.protocol.InCallButtonIds
import org.prauga.phone.incallui.incall.protocol.InCallButtonIdsExtension
import org.prauga.phone.incallui.incall.protocol.InCallButtonUi
import org.prauga.phone.incallui.incall.protocol.InCallButtonUiDelegate
import org.prauga.phone.incallui.incall.protocol.InCallButtonUiDelegateFactory
import org.prauga.phone.incallui.incall.protocol.InCallScreen
import org.prauga.phone.incallui.incall.protocol.InCallScreenDelegate
import org.prauga.phone.incallui.incall.protocol.InCallScreenDelegateFactory
import org.prauga.phone.incallui.incall.protocol.PrimaryCallState
import org.prauga.phone.incallui.incall.protocol.PrimaryCallState.ButtonState
import org.prauga.phone.incallui.incall.protocol.PrimaryInfo
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo

/** The call screen of a voice call: who, how long, the call buttons and end call. */
class InCallFragment : Fragment(), InCallScreen, InCallButtonUi, AudioRouteSelectorPresenter {

    private val buttonControllers = mutableListOf<ButtonController>()
    private lateinit var inCallScreenDelegate: InCallScreenDelegate
    private lateinit var inCallButtonUiDelegate: InCallButtonUiDelegate
    private var contactGrid: ContactGridState? = null
    private var slots: InCallButtonSlots? = null
    private var buttonChooser: ButtonChooser? = null
    private var savedSecondaryInfo: SecondaryInfo? = null
    private var voiceNetworkType = 0
    private var phoneType = 0
    private var userDeniedBluetooth = false

    private var onHold by mutableStateOf<SecondaryInfo?>(null)
    private var endCallEnabled by mutableStateOf(true)
    private var dialpadShowing by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) inCallButtonUiDelegate.callRecordClicked(true)
        }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Noted first: the selector asks for the permission again unless the refusal is known.
            if (!results.values.all { it }) userDeniedBluetooth = true
            inCallButtonUiDelegate.showAudioRouteSelector()
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        savedSecondaryInfo?.let(::setSecondary)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inCallButtonUiDelegate =
            FragmentUtils.getParent(this, InCallButtonUiDelegateFactory::class.java)!!
                .newInCallButtonUiDelegate()
    }

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        LogUtil.i("InCallFragment.onCreateView", null)
        val context = requireContext()
        val grid =
            ContactGridState(context).apply {
                isInMultiWindowMode = requireActivity().isInMultiWindowMode
            }
        val buttons = InCallButtonSlots(context)
        contactGrid = grid
        slots = buttons
        val telephony = context.getSystemService(TelephonyManager::class.java)
        voiceNetworkType =
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                TelephonyManager.NETWORK_TYPE_UNKNOWN
            } else {
                telephony.voiceNetworkType
            }
        phoneType = telephony.phoneType
        return InCallScreenHost.createView(
            context,
            top = {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = PvotSpacing.M)) {
                    onHold?.let { OnHoldBanner(it, Modifier.padding(horizontal = PvotSpacing.L)) }
                    // Who the call is with sits in the upper third, clear of the controls below.
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContactGrid(grid)
                    }
                    Spacer(Modifier.weight(2f))
                }
            },
            bottom = {
                Column(
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = PvotSpacing.L)
                        .padding(bottom = EndCallBottomGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    InCallControls(buttons, endCallEnabled, ::onEndCall)
                }
            },
            overDialpad = {
                // The dialpad leaves room at its bottom for this, so the call can end while it
                // shows.
                if (dialpadShowing) {
                    Box(Modifier.navigationBarsPadding().padding(bottom = EndCallBottomGap)) {
                        EndCallButton(endCallEnabled, ::onEndCall)
                    }
                }
            },
        )
    }

    private fun onEndCall() {
        LogUtil.i("InCallFragment.onEndCall", "end call button clicked")
        inCallScreenDelegate.onEndCallClicked()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        LogUtil.i("InCallFragment.onViewCreated", null)
        super.onViewCreated(view, savedInstanceState)
        inCallScreenDelegate =
            Assert.isNotNull(
                FragmentUtils.getParent(this, InCallScreenDelegateFactory::class.java)!!
                    .newInCallScreenDelegate()
            )
        val delegate = inCallButtonUiDelegate
        buttonControllers.clear()
        buttonControllers +=
            listOf(
                ButtonController.MuteButtonController(delegate),
                ButtonController.SpeakerButtonController(delegate),
                ButtonController.DialpadButtonController(delegate),
                ButtonController.HoldButtonController(delegate),
                ButtonController.AddCallButtonController(delegate),
                ButtonController.SwapButtonController(delegate),
                ButtonController.MergeButtonController(delegate),
                ButtonController.SwapSimButtonController(delegate),
                ButtonController.UpgradeToVideoButtonController(delegate),
                UpgradeToRttButtonController(delegate),
                ButtonController.TransferButtonController(delegate),
                ButtonController.ManageConferenceButtonController(inCallScreenDelegate),
                ButtonController.SwitchToSecondaryButtonController(inCallScreenDelegate),
                ButtonController.CallRecordButtonController(delegate),
            )
        inCallScreenDelegate.onInCallScreenDelegateInit(this)
        inCallScreenDelegate.onInCallScreenReady()
        // The button grid exists with the view; it used to arrive with a child fragment.
        LogUtil.i("InCallFragment.onViewCreated", "InCallUiReady")
        inCallButtonUiDelegate.onInCallButtonUiReady(this)
        updateButtonStates()
    }

    override fun onResume() {
        super.onResume()
        inCallScreenDelegate.onInCallScreenResumed()
    }

    override fun onPause() {
        super.onPause()
        inCallScreenDelegate.onInCallScreenPaused()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LogUtil.i("InCallFragment.onDestroyView", "InCallUiUnready")
        inCallButtonUiDelegate.onInCallButtonUiUnready()
        inCallScreenDelegate.onInCallScreenUnready()
        // The controllers stay until the view is recreated: presenters already notifying can still
        // reach this fragment during a fragment swap, as when a waiting call arrives.
        buttonControllers.forEach { it.setButton(null) }
        slots = null
    }

    override fun setPrimary(primaryInfo: PrimaryInfo) {
        LogUtil.i("InCallFragment.setPrimary", primaryInfo.toString())
        contactGrid?.setPrimary(primaryInfo)
    }

    override fun setSecondary(secondaryInfo: SecondaryInfo) {
        LogUtil.i("InCallFragment.setSecondary", secondaryInfo.toString())
        updateButtonStates()
        if (!isAdded) {
            savedSecondaryInfo = secondaryInfo
            return
        }
        savedSecondaryInfo = null
        onHold = secondaryInfo.takeIf { it.shouldShow() }
    }

    override fun setCallState(primaryCallState: PrimaryCallState) {
        LogUtil.i("InCallFragment.setCallState", primaryCallState.toString())
        contactGrid?.setCallState(primaryCallState)
        getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY).apply {
            isAllowed = primaryCallState.swapToSecondaryButtonState() != ButtonState.NOT_SUPPORT
            isEnabled = primaryCallState.swapToSecondaryButtonState() == ButtonState.ENABLED
        }
        buttonChooser =
            ButtonChooserFactory.newButtonChooser(
                voiceNetworkType,
                primaryCallState.isWifi,
                phoneType,
            )
        updateButtonStates()
    }

    override fun setEndCallButtonEnabled(enabled: Boolean, animate: Boolean) {
        endCallEnabled = enabled
    }

    override fun showManageConferenceCallButton(visible: Boolean) {
        getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).apply {
            isAllowed = visible
            isEnabled = visible
        }
        updateButtonStates()
    }

    override fun isManageConferenceVisible(): Boolean =
        getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).isAllowed

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent) {
        val grid = contactGrid ?: return
        for (text in grid.accessibilityText()) event.text += text
    }

    override fun showNoteSentToast() {
        LogUtil.i("InCallFragment.showNoteSentToast", null)
        Toast.makeText(context, R.string.incall_note_sent, Toast.LENGTH_LONG).show()
    }

    override fun updateInCallScreenColors() = Unit

    override fun onInCallScreenDialpadVisibilityChange(isShowing: Boolean) {
        LogUtil.i("InCallFragment.onInCallScreenDialpadVisibilityChange", "isShowing: $isShowing")
        getButtonController(InCallButtonIds.BUTTON_DIALPAD).setChecked(isShowing)
        slots?.onDialpadVisibilityChange(isShowing)
        dialpadShowing = isShowing
    }

    override fun getAnswerAndDialpadContainerResourceId(): Int = R.id.incall_dialpad_container

    override fun getInCallScreenFragment(): Fragment = this

    override fun showButton(@InCallButtonIds buttonId: Int, show: Boolean) {
        LogUtil.v(
            "InCallFragment.showButton",
            "buttionId: %s, show: %b",
            InCallButtonIdsExtension.toString(buttonId),
            show,
        )
        if (isSupportedButton(buttonId)) getButtonController(buttonId).isAllowed = show
    }

    override fun enableButton(@InCallButtonIds buttonId: Int, enable: Boolean) {
        LogUtil.v(
            "InCallFragment.enableButton",
            "buttonId: %s, enable: %b",
            InCallButtonIdsExtension.toString(buttonId),
            enable,
        )
        if (isSupportedButton(buttonId)) getButtonController(buttonId).isEnabled = enable
    }

    override fun setEnabled(enabled: Boolean) {
        LogUtil.v("InCallFragment.setEnabled", "enabled: $enabled")
        buttonControllers.forEach { it.isEnabled = enabled }
    }

    override fun setHold(value: Boolean) {
        getButtonController(InCallButtonIds.BUTTON_HOLD).setChecked(value)
    }

    override fun setCameraSwitched(isBackFacingCamera: Boolean) = Unit

    override fun setVideoPaused(isPaused: Boolean) = Unit

    override fun setAudioState(audioState: CallAudioState) {
        LogUtil.i("InCallFragment.setAudioState", "audioState: $audioState")
        (getButtonController(InCallButtonIds.BUTTON_AUDIO) as SpeakerButtonController)
            .setAudioState(audioState)
        getButtonController(InCallButtonIds.BUTTON_MUTE).setChecked(audioState.isMuted)
    }

    override fun setCallRecordingState(isRecording: Boolean) {
        (getButtonController(InCallButtonIds.BUTTON_RECORD_CALL) as CallRecordButtonController)
            .setRecordingState(isRecording)
    }

    override fun setCallRecordingDuration(durationMs: Long) {
        (getButtonController(InCallButtonIds.BUTTON_RECORD_CALL) as CallRecordButtonController)
            .setRecordingDuration(durationMs)
    }

    override fun requestCallRecordingPermissions(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    override fun updateButtonStates() {
        val buttons = slots ?: return
        if (buttonControllers.isEmpty()) return
        buttons.place(buttonControllers, buttonChooser, voiceNetworkType, phoneType)
    }

    override fun getInCallButtonUiFragment(): Fragment = this

    override fun showAudioRouteSelector() {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        if (
            permissions.all {
                requireContext().checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            } || userDeniedBluetooth
        ) {
            AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.currentAudioState)
                .show(childFragmentManager, null)
        } else {
            bluetoothPermissionLauncher.launch(permissions)
        }
    }

    override fun onAudioRouteSelected(audioRoute: Int) {
        inCallButtonUiDelegate.setAudioRoute(audioRoute)
    }

    override fun onAudioRouteSelectorDismiss() = Unit

    fun getButtonController(@InCallButtonIds id: Int): ButtonController =
        buttonControllers.firstOrNull { it.inCallButtonId == id }
            ?: throw Assert.createAssertionFailException("")

    override fun isShowingLocationUi(): Boolean = locationFragment()?.isVisible == true

    override fun showLocationUi(locationUi: Fragment?) {
        val isVisible = isShowingLocationUi()
        if (locationUi != null && !isVisible) {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.incall_location_holder, locationUi)
                .commitAllowingStateLoss()
        } else if (locationUi == null && isVisible) {
            childFragmentManager
                .beginTransaction()
                .remove(locationFragment()!!)
                .commitAllowingStateLoss()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        @Suppress("DEPRECATION") super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode == isShowingLocationUi()) {
            LogUtil.i("InCallFragment.onMultiWindowModeChanged", "hide = $isInMultiWindowMode")
            showLocationUi(if (isInMultiWindowMode) null else locationFragment())
        }
        contactGrid?.isInMultiWindowMode = isInMultiWindowMode
    }

    private fun locationFragment(): Fragment? =
        childFragmentManager.findFragmentById(R.id.incall_location_holder)

    private companion object {
        fun isSupportedButton(@InCallButtonIds id: Int) =
            id in
                setOf(
                    InCallButtonIds.BUTTON_AUDIO,
                    InCallButtonIds.BUTTON_MUTE,
                    InCallButtonIds.BUTTON_DIALPAD,
                    InCallButtonIds.BUTTON_HOLD,
                    InCallButtonIds.BUTTON_SWAP,
                    InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO,
                    InCallButtonIds.BUTTON_ADD_CALL,
                    InCallButtonIds.BUTTON_MERGE,
                    InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE,
                    InCallButtonIds.BUTTON_SWAP_SIM,
                    InCallButtonIds.BUTTON_UPGRADE_TO_RTT,
                    InCallButtonIds.BUTTON_TRANSFER,
                    InCallButtonIds.BUTTON_RECORD_CALL,
                )
    }
}

/**
 * Builds a call screen's view: Compose [top] and [bottom] parts around the emergency location
 * holder, with the dialpad's container over the bottom and [overDialpad] centred above that. The
 * holders stay plain views because fragments are placed into them by id.
 */
object InCallScreenHost {
    fun createView(
        context: Context,
        top: @Composable () -> Unit,
        bottom: @Composable () -> Unit,
        overDialpad: @Composable () -> Unit = {},
    ): View {
        fun compose(glass: Boolean = true, content: @Composable () -> Unit) =
            ComposeView(context).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    PhoneScreenTheme(fillBackground = false) {
                        if (glass) {
                            // The window's gradient changes with the call's colors; the glass
                            // redraws its copy then.
                            val background =
                                InCallPresenter.getInstance()
                                    .themeColorManager
                                    ?.windowBackgroundVersion
                                    ?.intValue
                            WindowBackdrop(backgroundKey = background, content = content)
                        } else {
                            content()
                        }
                    }
                }
            }
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    compose(content = top),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
                addView(
                    FrameLayout(context).apply { id = R.id.incall_location_holder },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    compose(content = bottom),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        return FrameLayout(context).apply {
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                FrameLayout(context).apply {
                    id = R.id.incall_dialpad_container
                    clipChildren = false
                    clipToPadding = false
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
            // Wraps its content, so it only takes touches where it draws.
            addView(
                // Over the dialpad, not the window background, so it has nothing to be glass over.
                compose(glass = false, content = overDialpad),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ),
            )
        }
    }
}
