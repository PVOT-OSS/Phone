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

package org.prauga.phone.dialer.calllog;

import org.prauga.phone.dialer.calllog.database.CallLogDatabaseModule;
import org.prauga.phone.dialer.calllog.datasources.CallLogDataSource;
import org.prauga.phone.dialer.calllog.datasources.DataSources;
import org.prauga.phone.dialer.calllog.datasources.phonelookup.PhoneLookupDataSource;
import org.prauga.phone.dialer.calllog.datasources.systemcalllog.SystemCallLogDataSource;
import org.prauga.phone.dialer.calllog.datasources.voicemail.VoicemailDataSource;

import com.google.common.collect.ImmutableList;

import dagger.Module;
import dagger.Provides;

/** Dagger module which satisfies call log dependencies. */
@Module(includes = CallLogDatabaseModule.class)
public abstract class CallLogModule {

  @Provides
  static DataSources provideCallLogDataSources(
      SystemCallLogDataSource systemCallLogDataSource,
      PhoneLookupDataSource phoneLookupDataSource,
      VoicemailDataSource voicemailDataSource) {
    // System call log must be first, see getDataSourcesExcludingSystemCallLog below.
    ImmutableList<CallLogDataSource> allDataSources =
        ImmutableList.of(systemCallLogDataSource, phoneLookupDataSource, voicemailDataSource);
    return new DataSources() {
      @Override
      public SystemCallLogDataSource getSystemCallLogDataSource() {
        return systemCallLogDataSource;
      }

      @Override
      public ImmutableList<CallLogDataSource> getDataSourcesIncludingSystemCallLog() {
        return allDataSources;
      }

      @Override
      public ImmutableList<CallLogDataSource> getDataSourcesExcludingSystemCallLog() {
        return allDataSources.subList(1, allDataSources.size());
      }
    };
  }
}
