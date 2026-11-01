/*
 * Copyright (C) 2015 The Android Open Source Project
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

package org.prauga.phone.incallui;

import android.annotation.SuppressLint;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;

import androidx.annotation.NonNull;

import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.common.concurrent.DialerExecutorComponent;
import org.prauga.phone.incallui.call.CallList;
import org.prauga.phone.incallui.call.DialerCall;
import org.prauga.phone.incallui.call.TelecomAdapter;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 * Accepts broadcast Intents which will be prepared by {@link StatusBarNotifier} and thus sent from
 * the notification manager. This should be visible from outside, but shouldn't be exported.
 */
public class NotificationBroadcastReceiver extends BroadcastReceiver {

  /**
   * Intent Action used for hanging up the current call from Notification bar. This will choose
   * first ringing call, first active call, or first background call (typically in STATE_HOLDING
   * state).
   */
  public static final String ACTION_DECLINE_INCOMING_CALL =
      "org.prauga.phone.incallui.ACTION_DECLINE_INCOMING_CALL";

  public static final String ACTION_HANG_UP_ONGOING_CALL =
      "org.prauga.phone.incallui.ACTION_HANG_UP_ONGOING_CALL";
  public static final String ACTION_ANSWER_VIDEO_INCOMING_CALL =
      "org.prauga.phone.incallui.ACTION_ANSWER_VIDEO_INCOMING_CALL";
  public static final String ACTION_ANSWER_VOICE_INCOMING_CALL =
      "org.prauga.phone.incallui.ACTION_ANSWER_VOICE_INCOMING_CALL";
  public static final String ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST =
      "org.prauga.phone.incallui.ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST";
  public static final String ACTION_DECLINE_VIDEO_UPGRADE_REQUEST =
      "org.prauga.phone.incallui.ACTION_DECLINE_VIDEO_UPGRADE_REQUEST";
  public static final String ACTION_TURN_ON_SPEAKER = "org.prauga.phone.incallui.ACTION_TURN_ON_SPEAKER";
  public static final String ACTION_TURN_OFF_SPEAKER =
      "org.prauga.phone.incallui.ACTION_TURN_OFF_SPEAKER";

  public static final String ACTION_MUTE = "org.prauga.phone.incallui.ACTION_MUTE";
  public static final String ACTION_UNMUTE = "org.prauga.phone.incallui.ACTION_UNMUTE";
  public static final String ACTION_UNHOLD = "org.prauga.phone.incallui.ACTION_UNHOLD";

  public static final String ACTION_PULL_EXTERNAL_CALL =
      "org.prauga.phone.incallui.ACTION_PULL_EXTERNAL_CALL";

  public static final String EXTRA_NOTIFICATION_ID =
      "org.prauga.phone.incallui.extra.EXTRA_NOTIFICATION_ID";

  @SuppressLint("MissingPermission")
  @Override
  public void onReceive(Context context, Intent intent) {
    final String action = intent.getAction();
    LogUtil.i("NotificationBroadcastReceiver.onReceive", "Broadcast from Notification: " + action);

    // TODO: Commands of this nature should exist in the CallList.
    if (action.equals(ACTION_ANSWER_VIDEO_INCOMING_CALL)) {
      answerIncomingCall(VideoProfile.STATE_BIDIRECTIONAL, context);
    } else if (action.equals(ACTION_ANSWER_VOICE_INCOMING_CALL)) {
      answerIncomingCall(VideoProfile.STATE_AUDIO_ONLY, context);
    } else if (action.equals(ACTION_DECLINE_INCOMING_CALL)) {
      declineIncomingCall();
    } else if (action.equals(ACTION_HANG_UP_ONGOING_CALL)) {
      hangUpOngoingCall();
    } else if (action.equals(ACTION_ACCEPT_VIDEO_UPGRADE_REQUEST)) {
      acceptUpgradeRequest(context);
    } else if (action.equals(ACTION_DECLINE_VIDEO_UPGRADE_REQUEST)) {
      declineUpgradeRequest();
    } else if (action.equals(ACTION_PULL_EXTERNAL_CALL)) {
      closeSystemDialogs(context);
      int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
      InCallPresenter.getInstance().getExternalCallNotifier().pullExternalCall(notificationId);
    } else if (action.equals(ACTION_TURN_ON_SPEAKER)) {
      TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_SPEAKER);
    } else if (action.equals(ACTION_TURN_OFF_SPEAKER)) {
      TelecomAdapter.getInstance().setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
    } else if (action.equals(ACTION_MUTE)) {
      TelecomAdapter.getInstance().mute(true);
    } else if (action.equals(ACTION_UNMUTE)) {
      TelecomAdapter.getInstance().mute(false);
    } else if (action.equals(ACTION_UNHOLD)) {
      DialerCall heldCall = CallList.getInstance().getBackgroundCall();
      if (heldCall != null) {
        heldCall.unhold();
      }
    }
  }

  private void acceptUpgradeRequest(Context context) {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.acceptUpgradeRequest", "call list is empty");
    } else {
      DialerCall call = callList.getVideoUpgradeRequestCall();
      if (call != null) {
        call.getVideoTech().acceptVideoRequest(context);
      }
    }
  }

  private void declineUpgradeRequest() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.declineUpgradeRequest", "call list is empty");
    } else {
      DialerCall call = callList.getVideoUpgradeRequestCall();
      if (call != null) {
        call.getVideoTech().declineVideoRequest();
      }
    }
  }

  private void hangUpOngoingCall() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.hangUpOngoingCall", "call list is empty");
    } else {
      DialerCall call = callList.getOutgoingCall();
      if (call == null) {
        call = callList.getActiveOrBackgroundCall();
      }
      LogUtil.i(
          "NotificationBroadcastReceiver.hangUpOngoingCall", "disconnecting call, call: " + call);
      if (call != null) {
        call.disconnect();
      }
    }
  }

  private void answerIncomingCall(int videoState, @NonNull Context context) {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.answerIncomingCall", "call list is empty");
    } else {
      DialerCall call = callList.getIncomingCall();
      if (call != null) {

        Futures.addCallback(
            Futures.immediateFuture(null),
            new FutureCallback<Void>() {
              @Override
              public void onSuccess(Void result) {
                answerIncomingCallCallback(call, videoState);
              }

              @Override
              public void onFailure(Throwable t) {
                answerIncomingCallCallback(call, videoState);
                // TODO(erfanian): Enumerate all error states and specify recovery strategies.
                throw new RuntimeException("Failed to successfully complete pre call tasks.", t);
              }
            },
            DialerExecutorComponent.get(context).uiExecutor());
      }
    }
  }

  private void answerIncomingCallCallback(@NonNull DialerCall call, int videoState) {
    call.answer(videoState);
    InCallPresenter.getInstance().showInCall(false /* showDialpad */, false /* newOutgoingCall */);
  }

  private void declineIncomingCall() {
    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      StatusBarNotifier.clearAllCallNotifications();
      LogUtil.e("NotificationBroadcastReceiver.declineIncomingCall", "call list is empty");
    } else {
      DialerCall call = callList.getIncomingCall();
      if (call != null) {
        call.reject(false /* rejectWithMessage */, null);
      }
    }
  }

  /** Closes open system dialogs and the notification shade. */
  private void closeSystemDialogs(Context context) {
    final Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
    final Bundle options = BroadcastOptions.makeBasic()
            .setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT)
            .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE)
            .toBundle();
    context.sendBroadcast(intent, null /* receiverPermission */, options);
  }
}
