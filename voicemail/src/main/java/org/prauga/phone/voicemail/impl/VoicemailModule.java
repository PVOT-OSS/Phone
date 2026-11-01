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

package org.prauga.phone.voicemail.impl;

import android.content.Context;

import org.prauga.phone.dialer.inject.ApplicationContext;
import org.prauga.phone.voicemail.VoicemailClient;
import org.prauga.phone.voicemail.VoicemailPermissionHelper;
import org.prauga.phone.voicemail.stub.StubVoicemailClient;

import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

/** This module provides an instance of the voicemail client. */
@Module
public final class VoicemailModule {

  @Provides
  @Singleton
  static VoicemailClient provideVoicemailClient(@ApplicationContext Context context) {
    if (!VoicemailPermissionHelper.hasPermissions(context)) {
      VvmLog.i(
          "VoicemailModule.provideVoicemailClient",
          "missing permissions " + VoicemailPermissionHelper.getMissingPermissions(context));
      return new StubVoicemailClient();
    }

    VvmLog.i("VoicemailModule.provideVoicemailClient", "providing VoicemailClientImpl");
    return new VoicemailClientImpl();
  }

  private VoicemailModule() {}
}
