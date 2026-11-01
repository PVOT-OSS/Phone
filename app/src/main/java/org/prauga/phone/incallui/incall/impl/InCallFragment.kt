/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
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
    private var buttonRows by mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) inCallButtonUiDelegate.callRecordClicked(true)
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        inCallButtonUiDelegate.showAudioRouteSelector()
        if (!results.values.all { it }) userDeniedBluetooth = true
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        savedSecondaryInfo?.let(::setSecondary)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inCallButtonUiDelegate =
            FragmentUtils.getParent(this, InCallButtonUiDelegateFactory::class.java)!!.newInCallButtonUiDelegate()
    }

    @SuppressLint("MissingPermission")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        LogUtil.i("InCallFragment.onCreateView", null)
        requireActivity().setTheme(R.style.Theme_InCallScreen)
        val context = requireContext()
        val grid = ContactGridState(context).apply { isInMultiWindowMode = requireActivity().isInMultiWindowMode }
        val buttons = InCallButtonSlots(context)
        contactGrid = grid
        slots = buttons
        buttonRows = resources.getInteger(R.integer.incall_num_rows)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        voiceNetworkType = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
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
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ContactGrid(grid) }
                }
            },
            bottom = {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = PvotSpacing.L),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.L),
                ) {
                    InCallButtonGrid(buttons, buttonRows)
                    EndCallButton(endCallEnabled) {
                        LogUtil.i("InCallFragment.onEndCall", "end call button clicked")
                        inCallScreenDelegate.onEndCallClicked()
                    }
                }
            },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        LogUtil.i("InCallFragment.onViewCreated", null)
        super.onViewCreated(view, savedInstanceState)
        inCallScreenDelegate = Assert.isNotNull(
            FragmentUtils.getParent(this, InCallScreenDelegateFactory::class.java)!!.newInCallScreenDelegate(),
        )
        val delegate = inCallButtonUiDelegate
        buttonControllers += listOf(
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
        buttonControllers.forEach { it.setButton(null) }
        buttonControllers.clear()
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
        buttonChooser = ButtonChooserFactory.newButtonChooser(voiceNetworkType, primaryCallState.isWifi, phoneType)
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
    }

    override fun getAnswerAndDialpadContainerResourceId(): Int = R.id.incall_dialpad_container

    override fun getInCallScreenFragment(): Fragment = this

    override fun showButton(@InCallButtonIds buttonId: Int, show: Boolean) {
        LogUtil.v("InCallFragment.showButton", "buttionId: %s, show: %b", InCallButtonIdsExtension.toString(buttonId), show)
        if (isSupportedButton(buttonId)) getButtonController(buttonId).isAllowed = show
    }

    override fun enableButton(@InCallButtonIds buttonId: Int, enable: Boolean) {
        LogUtil.v("InCallFragment.enableButton", "buttonId: %s, enable: %b", InCallButtonIdsExtension.toString(buttonId), enable)
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
        (getButtonController(InCallButtonIds.BUTTON_AUDIO) as SpeakerButtonController).setAudioState(audioState)
        getButtonController(InCallButtonIds.BUTTON_MUTE).setChecked(audioState.isMuted)
    }

    override fun setCallRecordingState(isRecording: Boolean) {
        (getButtonController(InCallButtonIds.BUTTON_RECORD_CALL) as CallRecordButtonController).setRecordingState(isRecording)
    }

    override fun setCallRecordingDuration(durationMs: Long) {
        (getButtonController(InCallButtonIds.BUTTON_RECORD_CALL) as CallRecordButtonController).setRecordingDuration(durationMs)
    }

    override fun requestCallRecordingPermissions(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    override fun updateButtonStates() {
        val buttons = slots ?: return
        if (buttonControllers.isEmpty()) return
        buttons.place(buttonControllers, buttonChooser, voiceNetworkType, phoneType, buttonRows)
    }

    override fun getInCallButtonUiFragment(): Fragment = this

    override fun showAudioRouteSelector() {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        if (permissions.all { requireContext().checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } || userDeniedBluetooth) {
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
        buttonControllers.firstOrNull { it.inCallButtonId == id } ?: throw Assert.createAssertionFailException("")

    override fun isShowingLocationUi(): Boolean = locationFragment()?.isVisible == true

    override fun showLocationUi(locationUi: Fragment?) {
        val isVisible = isShowingLocationUi()
        if (locationUi != null && !isVisible) {
            childFragmentManager.beginTransaction().replace(R.id.incall_location_holder, locationUi).commitAllowingStateLoss()
        } else if (locationUi == null && isVisible) {
            childFragmentManager.beginTransaction().remove(locationFragment()!!).commitAllowingStateLoss()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        @Suppress("DEPRECATION")
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode == isShowingLocationUi()) {
            LogUtil.i("InCallFragment.onMultiWindowModeChanged", "hide = $isInMultiWindowMode")
            showLocationUi(if (isInMultiWindowMode) null else locationFragment())
        }
        contactGrid?.isInMultiWindowMode = isInMultiWindowMode
    }

    private fun locationFragment(): Fragment? = childFragmentManager.findFragmentById(R.id.incall_location_holder)

    private companion object {
        fun isSupportedButton(@InCallButtonIds id: Int) = id in setOf(
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
            InCallButtonIds.BUTTON_RECORD_CALL,
        )
    }
}

/** The red button that ends the call. */
@Composable
internal fun EndCallButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .background(MaterialTheme.colorScheme.error, CircleShape)
            .testTag(TestTags.INCALL_END_CALL),
    ) {
        Icon(painterResource(R.drawable.ic_phone_off), stringResource(R.string.incall_content_description_end_call), tint = MaterialTheme.colorScheme.onError)
    }
}

/**
 * Builds a call screen's view: Compose [top] and [bottom] parts around the emergency location
 * holder, with the dialpad's container over the bottom. The holders stay plain views because
 * fragments are placed into them by id.
 */
object InCallScreenHost {
    fun createView(context: Context, top: @Composable () -> Unit, bottom: @Composable () -> Unit): View {
        fun compose(content: @Composable () -> Unit) = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { PhoneScreenTheme(fillBackground = false) { content() } }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(compose(top), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                FrameLayout(context).apply { id = R.id.incall_location_holder },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(compose(bottom), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(context).apply {
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                FrameLayout(context).apply {
                    id = R.id.incall_dialpad_container
                    clipChildren = false
                    clipToPadding = false
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
            )
        }
    }
}
