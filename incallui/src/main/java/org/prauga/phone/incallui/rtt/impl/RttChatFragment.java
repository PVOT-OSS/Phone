/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
 * limitations under the License
 */

package org.prauga.phone.incallui.rtt.impl;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.CallAudioState;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.prauga.phone.incallui.R;
import org.prauga.phone.incallui.speakerbuttonlogic.SpeakerButtonInfo;
import org.prauga.phone.dialer.common.Assert;
import org.prauga.phone.dialer.common.FragmentUtils;
import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.common.UiUtil;
import org.prauga.phone.dialer.lettertile.LetterTileDrawable;
import org.prauga.phone.dialer.rtt.RttTranscript;
import org.prauga.phone.dialer.rtt.RttTranscriptMessage;
import org.prauga.phone.dialer.util.DrawableConverter;
import org.prauga.phone.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import org.prauga.phone.incallui.call.state.DialerCallState;
import org.prauga.phone.incallui.incall.protocol.ContactPhotoType;
import org.prauga.phone.incallui.incall.protocol.InCallButtonIds;
import org.prauga.phone.incallui.incall.protocol.InCallButtonUi;
import org.prauga.phone.incallui.incall.protocol.InCallButtonUiDelegate;
import org.prauga.phone.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import org.prauga.phone.incallui.incall.protocol.InCallScreen;
import org.prauga.phone.incallui.incall.protocol.InCallScreenDelegate;
import org.prauga.phone.incallui.incall.protocol.InCallScreenDelegateFactory;
import org.prauga.phone.incallui.incall.protocol.PrimaryCallState;
import org.prauga.phone.incallui.incall.protocol.PrimaryInfo;
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo;
import org.prauga.phone.incallui.rtt.protocol.Constants;
import org.prauga.phone.incallui.rtt.protocol.RttCallScreen;
import org.prauga.phone.incallui.rtt.protocol.RttCallScreenDelegate;
import org.prauga.phone.incallui.rtt.protocol.RttCallScreenDelegateFactory;

import java.util.List;

/** RTT chat fragment to show chat bubbles. */
public class RttChatFragment extends Fragment
    implements OnEditorActionListener,
        TextWatcher,
        RttChatActions,
        RttCallScreen,
        InCallScreen,
        InCallButtonUi,
        AudioRouteSelectorPresenter {

  private static final String ARG_CALL_ID = "call_id";

  private EditText editText;
  private final RttChatMessages messages = new RttChatMessages();
  private final RttChatUiState uiState = new RttChatUiState();
  private boolean isClearingInput;

  private InCallScreenDelegate inCallScreenDelegate;
  private RttCallScreenDelegate rttCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private boolean isTimerStarted;
  private SecondaryInfo savedSecondaryInfo;
  private PrimaryInfo primaryInfo = PrimaryInfo.empty();
  private PrimaryCallState primaryCallState = PrimaryCallState.empty();

  /**
   * Create a new instance of RttChatFragment.
   *
   * @param callId call id of the RTT call.
   * @return new RttChatFragment
   */
  public static RttChatFragment newInstance(String callId) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, callId);
    RttChatFragment instance = new RttChatFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.i("RttChatFragment.onCreate", null);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    // Prevent updating local message until UI is ready.
    isClearingInput = true;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    LogUtil.i("RttChatFragment.onViewCreated", null);

    rttCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, RttCallScreenDelegateFactory.class)
            .newRttCallScreenDelegate(this);

    rttCallScreenDelegate.initRttCallScreenDelegate(this);

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
  }

  @Override
  public List<RttTranscriptMessage> getRttTranscriptMessageList() {
    return messages.getRttTranscriptMessageList();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    editText = RttChatHost.createInput(requireContext());
    editText.setOnEditorActionListener(this);
    editText.addTextChangedListener(this);
    editText.setOnKeyListener(
        (v, keyCode, event) -> {
          if (keyCode == KeyEvent.KEYCODE_DEL
              && event.getAction() == KeyEvent.ACTION_DOWN
              && TextUtils.isEmpty(editText.getText())) {
            String lastMessage = messages.retrieveLastLocalMessage();
            if (lastMessage != null) {
              resumeInput(lastMessage);
              rttCallScreenDelegate.onLocalMessage("\b");
              return true;
            }
            return false;
          }
          return false;
        });
    return RttChatHost.createView(requireContext(), uiState, messages, editText, this);
  }

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_SEND) {
      if (!TextUtils.isEmpty(editText.getText())) {
        onSubmit();
      }
      return true;
    }
    return false;
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    if (isClearingInput) {
      return;
    }
    String messageToAppend = messages.computeChangeOfLocalMessage(s.toString());
    if (!TextUtils.isEmpty(messageToAppend)) {
      messages.addLocalMessage(messageToAppend);
      rttCallScreenDelegate.onLocalMessage(messageToAppend);
    }
  }

  @Override
  public void onRemoteMessage(String message) {
    messages.addRemoteMessage(message);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LogUtil.enterBlock("RttChatFragment.onDestroyView");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void afterTextChanged(Editable s) {
    uiState.setSubmitEnabled(!TextUtils.isEmpty(s));
  }

  @Override
  public void onSubmit() {
    messages.submitLocalMessage();
    resumeInput("");
    rttCallScreenDelegate.onLocalMessage(Constants.BUBBLE_BREAKER);
  }

  @Override
  public void onEndCall() {
    LogUtil.i("RttChatFragment.onEndCall", "end call button clicked");
    inCallButtonUiDelegate.onEndCallClicked();
  }

  @Override
  public void onMenuOpen() {
    UiUtil.hideKeyboardFrom(getContext(), editText);
    uiState.setMenuOpen(true);
  }

  @Override
  public void onMute(boolean muted) {
    inCallButtonUiDelegate.muteClicked(muted, true);
  }

  @Override
  public void onSpeaker() {
    CallAudioState audioState = uiState.getAudioState();
    if (audioState == null || new SpeakerButtonInfo(audioState).nonBluetoothMode) {
      inCallButtonUiDelegate.toggleSpeakerphone();
    } else {
      inCallButtonUiDelegate.showAudioRouteSelector();
    }
  }

  @Override
  public void onDialpad(boolean show) {
    inCallButtonUiDelegate.showDialpadClicked(show);
  }

  @Override
  public void onAddCall() {
    inCallButtonUiDelegate.addCallClicked();
  }

  @Override
  public void onSwap() {
    if (uiState.getSwapEnabled()) {
      inCallButtonUiDelegate.swapClicked();
    }
    if (uiState.getSwitchToSecondaryEnabled()) {
      inCallScreenDelegate.onSecondaryInfoClicked();
    }
  }

  @Override
  public void onSwitchToVoice() {
    inCallButtonUiDelegate.changeToVoiceClicked();
  }

  @Override
  public void onAudioRoute(int route) {
    inCallButtonUiDelegate.setAudioRoute(route);
  }

  @Override
  public void onScrollBack() {
    UiUtil.hideKeyboardFrom(getContext(), editText);
  }

  @Override
  public void onRestoreRttChat(RttTranscript rttTranscript) {
    String unfinishedLocalMessage = messages.onRestoreRttChat(rttTranscript);
    if (unfinishedLocalMessage != null) {
      resumeInput(unfinishedLocalMessage);
    }
  }

  private void resumeInput(String input) {
    isClearingInput = true;
    editText.setText(input);
    editText.setSelection(input.length());
    isClearingInput = false;
  }

  @Override
  public void onStart() {
    LogUtil.enterBlock("RttChatFragment.onStart");
    super.onStart();
    isClearingInput = false;
    onRttScreenStart();
  }

  @Override
  public void onStop() {
    LogUtil.enterBlock("RttChatFragment.onStop");
    super.onStop();
    isClearingInput = true;
    uiState.setMenuOpen(false);
    uiState.setAudioMenuOpen(false);
    onRttScreenStop();
  }

  @Override
  public void onRttScreenStart() {
    rttCallScreenDelegate.onRttCallScreenUiReady();
  }

  @Override
  public void onRttScreenStop() {
    rttCallScreenDelegate.onRttCallScreenUiUnready();
  }

  @Override
  public Fragment getRttCallScreenFragment() {
    return this;
  }

  @Override
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("RttChatFragment.setPrimary", primaryInfo.toString());
    uiState.setName(primaryInfo.name() == null ? "" : primaryInfo.name());
    uiState.setPhotoUri(
        primaryInfo.photoType() == ContactPhotoType.CONTACT && primaryInfo.photoUri() != null
            ? primaryInfo.photoUri().toString()
            : null);
    uiState.setColorKey(
        primaryInfo.contactInfoLookupKey() != null
            ? primaryInfo.contactInfoLookupKey()
            : uiState.getName());
    this.primaryInfo = primaryInfo;
  }


  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("RttChatFragment.setSecondary", secondaryInfo.toString());
    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    uiState.setSecondary(secondaryInfo.shouldShow() ? secondaryInfo : null);
    uiState.setSwitchToSecondaryEnabled(secondaryInfo.shouldShow());
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("RttChatFragment.setCallState", primaryCallState.toString());
    this.primaryCallState = primaryCallState;
    if (!isTimerStarted && primaryCallState.state() == DialerCallState.ACTIVE) {
      LogUtil.i("RttChatFragment.setCallState", "starting timer");
      uiState.setConnectTimeMillis(primaryCallState.connectTimeMillis());
      isTimerStarted = true;
      uiState.setInputVisible(true);
      editText.setFocusableInTouchMode(true);
      if (editText.requestFocus()) {
        UiUtil.showKeyboardFrom(getContext(), editText);
      }
      messages.showAdvisory();
    }
    if (primaryCallState.state() == DialerCallState.DIALING) {
      showWaitingForJoinBanner();
    } else {
      hideWaitingForJoinBanner();
    }
    if (primaryCallState.state() == DialerCallState.DISCONNECTED) {
      rttCallScreenDelegate.onSaveRttTranscript();
    }
  }

  private void showWaitingForJoinBanner() {
    uiState.setStatusBanner(getString(R.string.rtt_status_banner_text, primaryInfo.name()));
  }

  private void hideWaitingForJoinBanner() {
    uiState.setStatusBanner(null);
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {}

  @Override
  public void showManageConferenceCallButton(boolean visible) {}

  @Override
  public boolean isManageConferenceVisible() {
    return false;
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void showNoteSentToast() {}

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    uiState.setDialpadShowing(isShowing);
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.incall_dialpad_container;
  }

  @Override
  public void showLocationUi(Fragment locationUi) {}

  @Override
  public boolean isShowingLocationUi() {
    return false;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(int buttonId, boolean show) {
    if (buttonId == InCallButtonIds.BUTTON_SWAP) {
      uiState.setSwapEnabled(show);
    }
  }

  @Override
  public void enableButton(int buttonId, boolean enable) {}

  @Override
  public void setEnabled(boolean on) {}

  @Override
  public void setHold(boolean on) {}

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("RttChatFragment.setAudioState", "audioState: " + audioState);
    uiState.setMuted(audioState.isMuted());
    uiState.setAudioState(audioState);
  }

  @Override
  public void updateButtonStates() {}

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    uiState.setAudioMenuOpen(true);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @Override
  public void requestCallRecordingPermissions(String[] permissions) {}

  @Override
  public void setCallRecordingDuration(long duration) {}

  @Override
  public void setCallRecordingState(boolean isRecording) {}
}
