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

package org.prauga.phone.dialer.binary.aosp;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.prauga.phone.contacts.common.extensions.PhoneDirectoryExtender;
import org.prauga.phone.contacts.common.extensions.PhoneDirectoryExtenderFactory;
import org.prauga.phone.dialer.binary.common.DialerApplication;
import org.prauga.phone.dialer.inject.ContextModule;
import org.prauga.phone.dialer.lookup.LookupCacheService;
import org.prauga.phone.dialer.lookup.LookupProvider;
import org.prauga.phone.dialer.lookup.LookupSettings;
import org.prauga.phone.dialer.lookup.ReverseLookupService;
import org.prauga.phone.dialer.phonenumbercache.PhoneNumberCacheBindings;
import org.prauga.phone.dialer.phonenumbercache.PhoneNumberCacheBindingsFactory;
import org.prauga.phone.incallui.bindings.InCallUiBindings;
import org.prauga.phone.incallui.bindings.InCallUiBindingsFactory;
import org.prauga.phone.incallui.bindings.InCallUiBindingsStub;
import org.prauga.phone.incallui.bindings.PhoneNumberService;

/**
 * The application class for the AOSP Dialer. This is a version of the Dialer app that has no
 * dependency on Google Play Services.
 */
public class AospDialerApplication extends DialerApplication implements
    PhoneNumberCacheBindingsFactory, PhoneDirectoryExtenderFactory, InCallUiBindingsFactory {

  /** Returns a new instance of the root component for the AOSP Dialer. */
  @Override
  @NonNull
  protected Object buildRootComponent() {
    return DaggerAospDialerRootComponent.builder().contextModule(new ContextModule(this)).build();
  }

  @Override
  public PhoneDirectoryExtender newPhoneDirectoryExtender() {
    return new PhoneDirectoryExtender() {
      @Override
      public boolean isEnabled(Context context) {
        return LookupSettings.isForwardLookupEnabled(AospDialerApplication.this);
      }

      @Override
      @Nullable
      public Uri getContentUri() {
        return LookupProvider.NEARBY_AND_PEOPLE_LOOKUP_URI;
      }
    };
  }

  @Override
  public InCallUiBindings newInCallUiBindings() {
    return new InCallUiBindingsStub() {
      @Override
      @Nullable
      public PhoneNumberService newPhoneNumberService(Context context) {
        return new ReverseLookupService(context);
      }
    };
  }

  @Override
  public PhoneNumberCacheBindings newPhoneNumberCacheBindings() {
    return LookupCacheService::new;
  }
}
