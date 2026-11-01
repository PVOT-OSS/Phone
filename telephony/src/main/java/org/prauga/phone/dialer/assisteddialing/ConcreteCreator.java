/*
 * Copyright (C) 2017 The Android Open Source Project
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

package org.prauga.phone.dialer.assisteddialing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import org.prauga.phone.telephony.R;
import org.prauga.phone.dialer.common.LogUtil;

/**
 * A Creator for AssistedDialingMediators.
 *
 * <p>This helps keep the dependencies required by AssistedDialingMediator for assisted dialing
 * explicit.
 */
public final class ConcreteCreator {

  /**
   * Creates a new AssistedDialingMediator. Assisted dialing is on unless the user turned it off,
   * and needs the user to have unlocked the phone since it started, as its settings live in
   * credential-protected storage.
   *
   * @return An AssistedDialingMediator
   */
  public static AssistedDialingMediator createNewAssistedDialingMediator(@NonNull Context context) {
    UserManager userManager = context.getSystemService(UserManager.class);
    if (userManager == null || !userManager.isUserUnlocked()) {
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "user is locked");
      return new AssistedDialingMediatorStub();
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (!prefs.getBoolean(context.getString(R.string.assisted_dialing_setting_toggle_key), true)) {
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "disabled by local setting");
      return new AssistedDialingMediatorStub();
    }

    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    if (telephonyManager == null) {
      return new AssistedDialingMediatorStub();
    }

    String userHomeCountry =
        prefs.getString(context.getString(R.string.assisted_dialing_setting_cc_key), null);
    return new AssistedDialingMediatorImpl(
        new LocationDetector(telephonyManager, userHomeCountry),
        new NumberTransformer(new Constraints(context, getCountryCodeProvider())));
  }

  /**
   * Returns a CountryCodeProvider responsible for providing countries eligible for assisted Dialing
   */
  public static CountryCodeProvider getCountryCodeProvider() {
    return new CountryCodeProvider();
  }
}
