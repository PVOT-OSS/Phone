/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */

package org.prauga.phone.incallui;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import org.prauga.phone.incallui.R;
import org.prauga.phone.dialer.app.BaseActivity;

/** Shows the {@link ConferenceManagerFragment} */
public class ManageConferenceActivity extends BaseActivity {

  private boolean isVisible;

  public boolean isVisible() {
    return isVisible;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    InCallPresenter.getInstance().setManageConferenceActivity(this);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                InCallPresenter.getInstance().bringToForeground(false);
                finish();
              }
            });

    setContentView(R.layout.activity_manage_conference);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.manageConferencePanel);
    if (fragment == null) {
      fragment = new ConferenceManagerFragment();
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.manageConferencePanel, fragment)
          .commit();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (isFinishing()) {
      InCallPresenter.getInstance().setManageConferenceActivity(null);
    }
  }


  @Override
  protected void onStart() {
    super.onStart();
    isVisible = true;
  }

  @Override
  protected void onStop() {
    super.onStop();
    isVisible = false;
  }
}
