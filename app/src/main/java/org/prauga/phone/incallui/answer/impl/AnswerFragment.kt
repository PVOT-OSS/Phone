/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import android.app.KeyguardManager
import android.app.KeyguardManager.KeyguardDismissCallback
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.IncomingCallSettings
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.incallui.answer.impl.CreateCustomSmsDialogFragment.CreateCustomSmsHolder
import org.prauga.phone.incallui.answer.impl.SmsBottomSheetFragment.SmsSheetHolder
import org.prauga.phone.incallui.answer.protocol.AnswerScreen
import org.prauga.phone.incallui.answer.protocol.AnswerScreenDelegate
import org.prauga.phone.incallui.answer.protocol.AnswerScreenDelegateFactory
import org.prauga.phone.incallui.call.state.DialerCallState
import org.prauga.phone.incallui.contactgrid.ContactGrid
import org.prauga.phone.incallui.contactgrid.ContactGridState
import org.prauga.phone.incallui.incall.protocol.InCallScreen
import org.prauga.phone.incallui.incall.protocol.InCallScreenDelegate
import org.prauga.phone.incallui.incall.protocol.InCallScreenDelegateFactory
import org.prauga.phone.incallui.incall.protocol.PrimaryCallState
import org.prauga.phone.incallui.incall.protocol.PrimaryInfo
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo
import org.prauga.phone.incallui.incalluilock.InCallUiLock
import org.prauga.phone.incallui.util.AccessibilityUtil
import org.prauga.phone.incallui.video.protocol.VideoCallScreen
import org.prauga.phone.incallui.videotech.utils.VideoUtils
import kotlin.math.abs
import kotlin.math.max

/** The incoming call screen: who is calling, and the controls to answer, decline or reply. */
class AnswerFragment : Fragment(), AnswerScreen, InCallScreen, SmsSheetHolder, CreateCustomSmsHolder, AnswerActions {

    private lateinit var answerScreenDelegate: AnswerScreenDelegate
    private lateinit var inCallScreenDelegate: InCallScreenDelegate
    private var contactGrid: ContactGridState? = null
    private val ui = AnswerUiState()
    private var primaryInfo: PrimaryInfo = PrimaryInfo.empty()
    private var primaryCallState: PrimaryCallState? = null
    private var textResponses: ArrayList<CharSequence>? = null
    private var textResponsesFragment: SmsBottomSheetFragment? = null
    private var createCustomSmsDialogFragment: CreateCustomSmsDialogFragment? = null
    private var answerVideoCallScreen: VideoCallScreen? = null
    private var buttonAcceptClicked = false
    private var buttonRejectClicked = false
    private var hasAnimatedEntry = false
    private val handler = Handler(Looper.getMainLooper())
    private val swipeHintRestoreTimer = Runnable(::restoreSwipeHintTexts)

    /** How far the header has faded while the answer control is pulled, from 0 to 1. */
    private var headerProgress by mutableFloatStateOf(0f)
    private var videoOff by mutableStateOf(false)
    private var showAvatar by mutableStateOf(true)

    private val secondaryBehavior: AnswerSideAction
        get() = if (isVideoCall || isVideoUpgradeRequest) ANSWER_VIDEO_AS_AUDIO else REJECT_WITH_SMS

    override fun onAttach(context: Context) {
        super.onAttach(context)
        FragmentUtils.checkParent(this, InCallScreenDelegateFactory::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val arguments = requireArguments()
        Assert.checkState(arguments.containsKey(ARG_CALL_ID))
        Assert.checkState(arguments.containsKey(ARG_IS_RTT_CALL))
        Assert.checkState(arguments.containsKey(ARG_IS_VIDEO_CALL))
        Assert.checkState(arguments.containsKey(ARG_IS_VIDEO_UPGRADE_REQUEST))
        buttonAcceptClicked = false
        buttonRejectClicked = false
        hasAnimatedEntry = savedInstanceState?.getBoolean(STATE_HAS_ANIMATED_ENTRY) == true
        val context = requireContext()
        val activity = requireActivity()
        val grid = ContactGridState(context).apply { isInMultiWindowMode = activity.isInMultiWindowMode }
        contactGrid = grid
        showAvatar = !isVideoCall && !isVideoUpgradeRequest
        ui.twoButtons = AccessibilityUtil.isTouchExplorationEnabled(context) || activity.isInMultiWindowMode ||
            IncomingCallSettings.answerWithSingleTap(context)
        ui.answerIcon = when {
            isVideoCall || isVideoUpgradeRequest -> R.drawable.ic_video
            isRttCall -> R.drawable.ic_message
            else -> R.drawable.ic_phone
        }

        answerScreenDelegate = FragmentUtils.getParentUnsafe(this, AnswerScreenDelegateFactory::class.java).newAnswerScreenDelegate(this)
        initSecondaryButtons()

        val root = FrameLayout(context)
        inflater.inflate(R.layout.answer_video_preview, root, true)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(compose(context) { Header(grid) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                FrameLayout(context).apply { id = R.id.incall_location_holder },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(
                compose(context) { AnswerControls(ui, this@AnswerFragment, Modifier.navigationBarsPadding().padding(bottom = PvotSpacing.L)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        @Suppress("DEPRECATION")
        root.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if (isVideoCall || isVideoUpgradeRequest) {
            if (VideoUtils.hasCameraPermissionAndShownPrivacyToast(context)) {
                answerVideoCallScreen = if (isSelfManagedCamera()) {
                    SelfManagedAnswerVideoCallScreen(callId, this, root)
                } else {
                    AnswerVideoCallScreen(callId, this, root)
                }
            } else {
                videoOff = true
            }
        }
        return root
    }

    private fun compose(context: Context, content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { PhoneScreenTheme(fillBackground = false) { content() } }
    }

    @androidx.compose.runtime.Composable
    private fun Header(grid: ContactGridState) {
        // The header rises and fades in on arrival, once per incoming call.
        val entry = remember { Animatable(if (hasAnimatedEntry) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (!hasAnimatedEntry) {
                entry.animateTo(1f, androidx.compose.animation.core.tween(resources.getInteger(R.integer.answer_animate_entry_millis), easing = PvotMotion.Settle))
                hasAnimatedEntry = true
            }
        }
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = PvotSpacing.L * 2), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContactGrid(
                    grid,
                    modifier = Modifier.graphicsLayer {
                        val scale = 1f - 0.25f * headerProgress
                        alpha = entry.value * (1f - headerProgress)
                        scaleX = scale
                        scaleY = scale
                        translationY = (1f - entry.value) * 120.dp.toPx()
                    },
                    avatarSize = if (showAvatar) 112.dp else 0.dp,
                )
                if (videoOff) {
                    Column(Modifier.padding(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_video_off), contentDescription = null)
                        Text(stringResource(R.string.call_incoming_video_is_off), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = PvotSpacing.S))
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inCallScreenDelegate = Assert.isNotNull(
            FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory::class.java).newInCallScreenDelegate(),
        )
        inCallScreenDelegate.onInCallScreenDelegateInit(this)
        inCallScreenDelegate.onInCallScreenReady()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        LogUtil.i("AnswerFragment.onResume", null)
        restoreSwipeHintTexts()
        inCallScreenDelegate.onInCallScreenResumed()
    }

    override fun onStart() {
        super.onStart()
        LogUtil.i("AnswerFragment.onStart", null)
        updateUI()
        answerVideoCallScreen?.onVideoScreenStart()
    }

    override fun onStop() {
        super.onStop()
        LogUtil.i("AnswerFragment.onStop", null)
        handler.removeCallbacks(swipeHintRestoreTimer)
        answerVideoCallScreen?.onVideoScreenStop()
    }

    override fun onPause() {
        super.onPause()
        LogUtil.i("AnswerFragment.onPause", null)
        inCallScreenDelegate.onInCallScreenPaused()
    }

    override fun onDestroyView() {
        LogUtil.i("AnswerFragment.onDestroyView", null)
        answerVideoCallScreen = null
        super.onDestroyView()
        inCallScreenDelegate.onInCallScreenUnready()
        answerScreenDelegate.onAnswerScreenUnready()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_HAS_ANIMATED_ENTRY, hasAnimatedEntry)
    }

    private fun initSecondaryButtons() {
        ui.secondary = secondaryBehavior
        ui.secondaryVisible = when {
            isVideoUpgradeRequest -> false
            isVideoCall -> true
            // The sheet behind it offers forwarding even when no text reply can be sent.
            canForwardCall() -> true
            else -> ui.secondaryVisible
        }
        ui.answerAndRelease = ANSWER_AND_RELEASE
        ui.answerAndReleaseVisible = allowAnswerAndRelease()
        if (allowAnswerAndRelease()) {
            answerScreenDelegate.onAnswerAndReleaseButtonEnabled()
        } else {
            answerScreenDelegate.onAnswerAndReleaseButtonDisabled()
        }
    }

    /** What answering will end, from Telecom's hint on the ringing call; null when nothing. */
    private fun incomingWillDisconnectText(): CharSequence? {
        if (!primaryInfo.answeringDisconnectsOngoingCall()) return null
        val appName = primaryInfo.answeringDisconnectsAppName()
        return if (appName.isNullOrEmpty()) {
            getText(R.string.call_incoming_will_disconnect)
        } else {
            getString(R.string.call_incoming_will_disconnect_app, appName)
        }
    }

    private fun updateUI() {
        if (view == null) return
        updatePrimaryUI()
        primaryCallState?.let { contactGrid?.setCallState(it) }
        restoreBackgroundMaskColor()
    }

    private fun updatePrimaryUI() {
        if (view == null) return
        contactGrid?.setPrimary(primaryInfo)
        ui.incomingWillDisconnectText = incomingWillDisconnectText()
    }

    // AnswerScreen

    override fun getCallId(): String = Assert.isNotNull(requireArguments().getString(ARG_CALL_ID))

    override fun isRttCall(): Boolean = requireArguments().getBoolean(ARG_IS_RTT_CALL)

    override fun isVideoCall(): Boolean = requireArguments().getBoolean(ARG_IS_VIDEO_CALL)

    override fun isVideoUpgradeRequest(): Boolean = requireArguments().getBoolean(ARG_IS_VIDEO_UPGRADE_REQUEST)

    fun isSelfManagedCamera(): Boolean = requireArguments().getBoolean(ARG_IS_SELF_MANAGED_CAMERA)

    override fun allowAnswerAndRelease(): Boolean = requireArguments().getBoolean(ARG_ALLOW_ANSWER_AND_RELEASE)

    private fun hasCallOnHold(): Boolean = requireArguments().getBoolean(ARG_HAS_CALL_ON_HOLD)

    override fun isActionTimeout(): Boolean = (buttonAcceptClicked || buttonRejectClicked) && answerScreenDelegate.isActionTimeout

    override fun setTextResponses(textResponses: List<String>?) {
        when {
            isVideoCall || isVideoUpgradeRequest -> LogUtil.i("AnswerFragment.setTextResponses", "no-op for video calls")
            textResponses == null -> {
                LogUtil.i("AnswerFragment.setTextResponses", "no text responses, hiding secondary button")
                this.textResponses = null
                // The sheet behind it still offers forwarding when no text reply can be sent.
                ui.secondaryVisible = canForwardCall()
            }
            requireActivity().isInMultiWindowMode -> {
                LogUtil.i("AnswerFragment.setTextResponses", "in multiwindow, hiding secondary button")
                this.textResponses = null
                ui.secondaryVisible = false
            }
            else -> {
                LogUtil.i("AnswerFragment.setTextResponses", "textResponses.size: " + textResponses.size)
                this.textResponses = ArrayList(textResponses)
                ui.secondaryVisible = true
            }
        }
    }

    override fun hasPendingDialogs(): Boolean {
        val pending = textResponsesFragment != null || createCustomSmsDialogFragment != null
        LogUtil.i("AnswerFragment.hasPendingDialogs", "$pending")
        return pending
    }

    override fun dismissPendingDialogs() {
        LogUtil.i("AnswerFragment.dismissPendingDialogs", null)
        textResponsesFragment?.dismiss()
        textResponsesFragment = null
        createCustomSmsDialogFragment?.dismiss()
        createCustomSmsDialogFragment = null
    }

    override fun getAnswerScreenFragment(): Fragment = this

    // InCallScreen

    override fun setPrimary(primaryInfo: PrimaryInfo) {
        LogUtil.i("AnswerFragment.setPrimary", primaryInfo.toString())
        this.primaryInfo = primaryInfo
        updatePrimaryUI()
    }

    override fun setSecondary(secondaryInfo: SecondaryInfo) = Unit

    override fun setCallState(primaryCallState: PrimaryCallState) {
        LogUtil.i("AnswerFragment.setCallState", primaryCallState.toString())
        this.primaryCallState = primaryCallState
        contactGrid?.setCallState(primaryCallState)
    }

    override fun setEndCallButtonEnabled(enabled: Boolean, animate: Boolean) = Unit

    override fun showManageConferenceCallButton(visible: Boolean) = Unit

    override fun isManageConferenceVisible(): Boolean = false

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent) {
        contactGrid?.accessibilityText()?.forEach { event.text += it }
        if (AccessibilityUtil.isTouchExplorationEnabled(context)) {
            event.text += resources.getString(R.string.a11y_incoming_call_swipe_gesture_prompt)
        }
    }

    override fun showNoteSentToast() = Unit

    override fun updateInCallScreenColors() = Unit

    override fun onInCallScreenDialpadVisibilityChange(isShowing: Boolean) = Unit

    override fun getAnswerAndDialpadContainerResourceId(): Int = throw Assert.createUnsupportedOperationFailException()

    override fun showLocationUi(locationUi: Fragment?) {
        val showing = isShowingLocationUi()
        if (!showing && locationUi != null) {
            childFragmentManager.beginTransaction().replace(R.id.incall_location_holder, locationUi).commitAllowingStateLoss()
        } else if (showing && locationUi == null) {
            childFragmentManager.findFragmentById(R.id.incall_location_holder)?.let {
                childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        }
    }

    override fun isShowingLocationUi(): Boolean =
        childFragmentManager.findFragmentById(R.id.incall_location_holder)?.isVisible == true

    override fun getInCallScreenFragment(): Fragment = this

    // AnswerActions

    override fun onAnswer() = acceptCallByUser(answerVideoAsAudio = false)

    override fun onReject() = rejectCall()

    override fun onProgress(progress: Float) {
        if (primaryCallState?.state() == DialerCallState.INCOMING && !isVideoCall) {
            answerScreenDelegate.updateWindowBackgroundColor(progress)
        }
        val startDelay = .25f
        headerProgress = max(0f, (abs(progress) - 1) / (1 - startDelay) + 1)
        if (abs(progress) >= .0001f) {
            handler.removeCallbacks(swipeHintRestoreTimer)
            restoreSwipeHintTexts()
        }
        if (progress == 0f) restoreBackgroundMaskColor()
    }

    override fun onSecondary() {
        if (secondaryBehavior == ANSWER_VIDEO_AS_AUDIO) acceptCallByUser(answerVideoAsAudio = true) else showMessageMenu()
    }

    override fun onAnswerAndRelease() {
        answerScreenDelegate.onAnswerAndReleaseCall()
        buttonAcceptClicked = true
    }

    override fun onSideHint(action: AnswerSideAction) {
        ui.hint = getText(action.hint)
        handler.removeCallbacks(swipeHintRestoreTimer)
        handler.postDelayed(swipeHintRestoreTimer, HINT_SECONDARY_SHOW_DURATION_MILLIS)
    }

    private fun acceptCallByUser(answerVideoAsAudio: Boolean) {
        LogUtil.i("AnswerFragment.acceptCallByUser", if (answerVideoAsAudio) " answerVideoAsAudio" else "")
        if (!buttonAcceptClicked) {
            answerScreenDelegate.onAnswer(answerVideoAsAudio)
            buttonAcceptClicked = true
        }
    }

    private fun rejectCall() {
        LogUtil.i("AnswerFragment.rejectCall", null)
        if (!buttonRejectClicked) {
            if (context == null) LogUtil.w("AnswerFragment.rejectCall", "Null context when rejecting call")
            buttonRejectClicked = true
            answerScreenDelegate.onReject()
        }
    }

    private fun restoreBackgroundMaskColor() {
        answerScreenDelegate.updateWindowBackgroundColor(0f)
    }

    private fun restoreSwipeHintTexts() {
        val state = primaryCallState
        ui.hint = when {
            !allowAnswerAndRelease() -> null
            hasCallOnHold() -> getText(R.string.call_incoming_default_label_answer_and_release_third)
            state != null && state.supportsCallOnHold() -> getText(R.string.call_incoming_default_label_answer_and_release_second)
            else -> ui.hint
        }
    }

    private fun showMessageMenu() {
        LogUtil.i("AnswerFragment.showMessageMenu", "Show sms menu.")
        if (context == null || isDetached || childFragmentManager.isDestroyed) return
        textResponsesFragment = SmsBottomSheetFragment.newInstance(textResponses, canForwardCall()).also { it.show(childFragmentManager, null) }
        TelecomUtil.silenceRinger(context)
    }

    // SmsSheetHolder and CreateCustomSmsHolder

    override fun acquireInCallUiLock(tag: String): InCallUiLock = answerScreenDelegate.acquireInCallUiLock(tag)

    override fun smsSelected(text: CharSequence?) {
        LogUtil.i("AnswerFragment.smsSelected", null)
        textResponsesFragment = null
        if (text == null) {
            val keyguard = requireContext().getSystemService(KeyguardManager::class.java)
            if (!keyguard.isKeyguardLocked) {
                LogUtil.i("AnswerFragment.smsSelected", "not locked, showing dialog directly")
                showCustomSmsDialog()
                return
            }
            LogUtil.i("AnswerFragment.smsSelected", "dismissing keyguard")
            keyguard.requestDismissKeyguard(
                requireActivity(),
                object : KeyguardDismissCallback() {
                    override fun onDismissCancelled() = LogUtil.i("AnswerFragment.smsSelected", "onDismissCancelled")

                    override fun onDismissError() = LogUtil.i("AnswerFragment.smsSelected", "onDismissError")

                    override fun onDismissSucceeded() {
                        LogUtil.i("AnswerFragment.smsSelected", "onDismissSucceeded")
                        showCustomSmsDialog()
                    }
                },
            )
            return
        }
        if (primaryCallState != null && canRejectCallWithSms()) {
            rejectCall()
            answerScreenDelegate.onRejectCallWithMessage(text.toString())
        }
    }

    private fun showCustomSmsDialog() {
        createCustomSmsDialogFragment = CreateCustomSmsDialogFragment.newInstance().also { it.showNow(childFragmentManager, null) }
    }

    private fun canForwardCall(): Boolean = ::answerScreenDelegate.isInitialized && answerScreenDelegate.canForwardCall()

    override fun forwardSelected() {
        LogUtil.i("AnswerFragment.forwardSelected", null)
        textResponsesFragment = null
        answerScreenDelegate.onForwardCall()
    }

    override fun smsDismissed() {
        LogUtil.i("AnswerFragment.smsDismissed", null)
        textResponsesFragment = null
    }

    override fun customSmsCreated(text: CharSequence) {
        LogUtil.i("AnswerFragment.customSmsCreated", null)
        createCustomSmsDialogFragment = null
        if (primaryCallState != null && canRejectCallWithSms()) {
            rejectCall()
            answerScreenDelegate.onRejectCallWithMessage(text.toString())
        }
    }

    override fun customSmsDismissed() {
        LogUtil.i("AnswerFragment.customSmsDismissed", null)
        createCustomSmsDialogFragment = null
    }

    private fun canRejectCallWithSms(): Boolean {
        val state = primaryCallState?.state() ?: return false
        return state != DialerCallState.DISCONNECTED && state != DialerCallState.DISCONNECTING && state != DialerCallState.IDLE
    }

    companion object {
        private const val ARG_CALL_ID = "call_id"
        private const val ARG_IS_RTT_CALL = "is_rtt_call"
        private const val ARG_IS_VIDEO_CALL = "is_video_call"
        private const val ARG_ALLOW_ANSWER_AND_RELEASE = "allow_answer_and_release"
        private const val ARG_HAS_CALL_ON_HOLD = "has_call_on_hold"
        private const val ARG_IS_VIDEO_UPGRADE_REQUEST = "is_video_upgrade_request"
        private const val ARG_IS_SELF_MANAGED_CAMERA = "is_self_managed_camera"
        private const val STATE_HAS_ANIMATED_ENTRY = "hasAnimated"
        private const val HINT_SECONDARY_SHOW_DURATION_MILLIS = 5000L

        val REJECT_WITH_SMS = AnswerSideAction(
            R.drawable.ic_message,
            R.string.a11y_description_incoming_call_reject_with_sms,
            R.string.a11y_incoming_call_reject_with_sms,
            R.string.call_incoming_swipe_to_decline_with_message,
        )
        val ANSWER_VIDEO_AS_AUDIO = AnswerSideAction(
            R.drawable.ic_video_off,
            R.string.a11y_description_incoming_call_answer_video_as_audio,
            R.string.a11y_incoming_call_answer_video_as_audio,
            R.string.call_incoming_swipe_to_answer_video_as_audio,
        )
        val ANSWER_AND_RELEASE = AnswerSideAction(
            R.drawable.ic_phone_x,
            R.string.a11y_description_incoming_call_answer_and_release,
            R.string.a11y_incoming_call_answer_and_release,
            R.string.call_incoming_swipe_to_answer_and_release,
        )

        @JvmStatic
        fun newInstance(
            callId: String,
            isRttCall: Boolean,
            isVideoCall: Boolean,
            isVideoUpgradeRequest: Boolean,
            isSelfManagedCamera: Boolean,
            allowAnswerAndRelease: Boolean,
            hasCallOnHold: Boolean,
        ): AnswerFragment = AnswerFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CALL_ID, Assert.isNotNull(callId))
                putBoolean(ARG_IS_RTT_CALL, isRttCall)
                putBoolean(ARG_IS_VIDEO_CALL, isVideoCall)
                putBoolean(ARG_IS_VIDEO_UPGRADE_REQUEST, isVideoUpgradeRequest)
                putBoolean(ARG_IS_SELF_MANAGED_CAMERA, isSelfManagedCamera)
                putBoolean(ARG_ALLOW_ANSWER_AND_RELEASE, allowAnswerAndRelease)
                putBoolean(ARG_HAS_CALL_ON_HOLD, hasCallOnHold)
            }
        }
    }
}
