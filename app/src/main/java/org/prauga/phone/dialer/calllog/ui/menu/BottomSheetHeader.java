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

package org.prauga.phone.dialer.calllog.ui.menu;

import android.content.Context;

import org.prauga.phone.dialer.calllog.model.CoalescedRow;
import org.prauga.phone.dialer.calllogutils.CallLogEntryText;
import org.prauga.phone.dialer.calllogutils.PhotoInfoBuilder;
import org.prauga.phone.dialer.historyitemactions.HistoryItemBottomSheetHeaderInfo;

/** Configures the top row in the bottom sheet. */
final class BottomSheetHeader {

  static HistoryItemBottomSheetHeaderInfo fromRow(Context context, CoalescedRow row) {
    return HistoryItemBottomSheetHeaderInfo.newBuilder()
        .setNumber(row.getNumber())
        .setPhotoInfo(PhotoInfoBuilder.fromCoalescedRow(context, row))
        .setPrimaryText(CallLogEntryText.buildPrimaryText(context, row).toString())
        .setSecondaryText(
            CallLogEntryText.buildSecondaryTextForBottomSheet(context, row).toString())
        .build();
  }
}
