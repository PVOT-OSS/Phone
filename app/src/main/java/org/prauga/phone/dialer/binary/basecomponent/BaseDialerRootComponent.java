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
 * limitations under the License
 */

package org.prauga.phone.dialer.binary.basecomponent;

import org.prauga.phone.dialer.activecalls.ActiveCallsComponent;
import org.prauga.phone.dialer.calllog.CallLogComponent;
import org.prauga.phone.dialer.calllog.database.CallLogDatabaseComponent;
import org.prauga.phone.dialer.calllog.ui.CallLogUiComponent;
import org.prauga.phone.dialer.common.concurrent.DialerExecutorComponent;
import org.prauga.phone.dialer.contacts.ContactsComponent;
import org.prauga.phone.dialer.glidephotomanager.GlidePhotoManagerComponent;
import org.prauga.phone.dialer.phonelookup.PhoneLookupComponent;
import org.prauga.phone.dialer.phonelookup.database.PhoneLookupDatabaseComponent;
import org.prauga.phone.dialer.phonenumbergeoutil.PhoneNumberGeoUtilComponent;
import org.prauga.phone.dialer.precall.PreCallComponent;
import org.prauga.phone.dialer.preferredsim.PreferredSimComponent;
import org.prauga.phone.dialer.preferredsim.suggestion.SimSuggestionComponent;
import org.prauga.phone.dialer.promotion.PromotionComponent;
import org.prauga.phone.dialer.speeddial.loader.UiItemLoaderComponent;
import org.prauga.phone.dialer.storage.StorageComponent;
import org.prauga.phone.dialer.theme.base.ThemeComponent;
import org.prauga.phone.voicemail.VoicemailComponent;

/**
 * Base class for the core application-wide component. All variants of the Dialer app should extend
 * from this component.
 */
public interface BaseDialerRootComponent
    extends ActiveCallsComponent.HasComponent,
        CallLogComponent.HasComponent,
        CallLogDatabaseComponent.HasComponent,
        CallLogUiComponent.HasComponent,
        ContactsComponent.HasComponent,
        DialerExecutorComponent.HasComponent,
        GlidePhotoManagerComponent.HasComponent,
        PhoneLookupComponent.HasComponent,
        PhoneLookupDatabaseComponent.HasComponent,
        PhoneNumberGeoUtilComponent.HasComponent,
        PreCallComponent.HasComponent,
        PreferredSimComponent.HasComponent,
        PromotionComponent.HasComponent,
        UiItemLoaderComponent.HasComponent,
        SimSuggestionComponent.HasComponent,
        StorageComponent.HasComponent,
        ThemeComponent.HasComponent,
        VoicemailComponent.HasComponent {}
