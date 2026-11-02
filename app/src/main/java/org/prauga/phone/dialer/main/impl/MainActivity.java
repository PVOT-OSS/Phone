/*
 * Copyright (C) 2017 The Android Open Source Project
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

package org.prauga.phone.dialer.main.impl;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.prauga.phone.dialer.R;
import org.prauga.phone.dialer.blockreportspam.ShowBlockReportSpamDialogReceiver;
import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.main.MainActivityPeer;
import org.prauga.phone.dialer.onboarding.OnboardingRouter;
import org.prauga.phone.dialer.util.PermissionsUtil;
import org.prauga.phone.dialer.util.TransactionSafeActivity;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
// TODO(calderwoodra): Do not extend TransactionSafeActivity after new SpeedDial is launched
public class MainActivity extends TransactionSafeActivity
    implements MainActivityPeer.PeerSupplier {

  private MainActivityPeer activePeer;

  /**
   * {@link android.content.BroadcastReceiver} that shows a dialog to block a number and/or report
   * it as spam when notified.
   */
  private ShowBlockReportSpamDialogReceiver showBlockReportSpamDialogReceiver;

  /** Whether READ_PHONE_NUMBERS was asked for already, so a refusal isn't asked again on resume. */
  private boolean askedForPhoneNumbers;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // The splash hands over to Theme.Pvot; the peer's AppCompat theme is set over it until the
    // main screen is Compose through and through.
    SplashScreen.installSplashScreen(this);
    setTheme(R.style.MainActivityTheme);
    super.onCreate(savedInstanceState);
    LogUtil.enterBlock("MainActivity.onCreate");

    activePeer = new OldMainActivityPeer(this);
    activePeer.onActivityCreate(savedInstanceState);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (activePeer.onBackPressed()) {
                  return;
                }
                // Nothing of ours to close: let the system handle it.
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
              }
            });

    showBlockReportSpamDialogReceiver =
        new ShowBlockReportSpamDialogReceiver(getSupportFragmentManager());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    activePeer.onNewIntent(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // The user build asks to become the default phone app over this screen, which keeps its intent.
    Intent onboarding = OnboardingRouter.intentIfNeeded(this);
    if (onboarding != null) {
      startActivity(onboarding);
      return;
    }

    activePeer.onActivityResume();
    requestPhoneNumbersPermissionIfImplied();

    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            showBlockReportSpamDialogReceiver, ShowBlockReportSpamDialogReceiver.getIntentFilter());
  }

  /**
   * Asks for READ_PHONE_NUMBERS once the phone permission is granted. Telecom wants it to read
   * calling accounts until the app is the default phone app, and Android grants it without asking
   * because it shares the phone permission's group. Older installs were granted the group without it.
   */
  private void requestPhoneNumbersPermissionIfImplied() {
    if (!askedForPhoneNumbers
        && PermissionsUtil.hasReadPhoneStatePermissions(this)
        && !PermissionsUtil.hasPermission(this, Manifest.permission.READ_PHONE_NUMBERS)) {
      askedForPhoneNumbers = true;
      requestPermissions(new String[] {Manifest.permission.READ_PHONE_NUMBERS}, 0);
    }
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    activePeer.onUserLeaveHint();
  }

  @Override
  protected void onPause() {
    super.onPause();
    activePeer.onActivityPause();

    LocalBroadcastManager.getInstance(this).unregisterReceiver(showBlockReportSpamDialogReceiver);
  }

  @Override
  protected void onStop() {
    super.onStop();
    activePeer.onActivityStop();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    activePeer.onSaveInstanceState(bundle);
  }

  @Override
  public MainActivityPeer getPeer() {
    return activePeer;
  }
}
