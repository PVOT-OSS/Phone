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
 * limitations under the License.
 */

package org.prauga.phone.dialer.calldetails;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.prauga.phone.dialer.R;
import org.prauga.phone.dialer.app.BaseActivity;
import org.prauga.phone.dialer.assisteddialing.ui.AssistedDialingSettingActivity;
import org.prauga.phone.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import org.prauga.phone.dialer.callintent.CallInitiationType;
import org.prauga.phone.dialer.callintent.CallIntentBuilder;
import org.prauga.phone.dialer.callrecord.CallRecordingDataStore;
import org.prauga.phone.dialer.common.Assert;
import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.common.concurrent.DialerExecutor.FailureListener;
import org.prauga.phone.dialer.common.concurrent.DialerExecutor.SuccessListener;
import org.prauga.phone.dialer.common.concurrent.DialerExecutor.Worker;
import org.prauga.phone.dialer.common.concurrent.DialerExecutorComponent;
import org.prauga.phone.dialer.common.concurrent.SupportUiListener;
import org.prauga.phone.dialer.common.database.Selection;
import org.prauga.phone.dialer.glidephotomanager.PhotoInfo;
import org.prauga.phone.dialer.postcall.PostCall;
import org.prauga.phone.dialer.precall.PreCall;
import org.prauga.phone.dialer.rtt.RttTranscriptActivity;
import org.prauga.phone.dialer.rtt.RttTranscriptUtil;
import org.prauga.phone.dialer.theme.base.ThemeComponent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains common logic shared between {@link OldCallDetailsActivity} and {@link
 * CallDetailsActivity}.
 */
abstract class CallDetailsActivityCommon extends BaseActivity {

  public static final String EXTRA_PHONE_NUMBER = "phone_number";
  public static final String EXTRA_CAN_REPORT_CALLER_ID = "can_report_caller_id";
  public static final String EXTRA_CAN_SUPPORT_ASSISTED_DIALING = "can_support_assisted_dialing";

  private final CallDetailsEntryViewHolder.CallDetailsEntryListener callDetailsEntryListener =
      new CallDetailsEntryListener(this);
  private final CallDetailsHeaderViewHolder.CallDetailsHeaderListener callDetailsHeaderListener =
      new CallDetailsHeaderListener(this);
  private final CallDetailsFooterViewHolder.DeleteCallDetailsListener deleteCallDetailsListener =
      new DeleteCallDetailsListener(this);
  private final CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener =
      new ReportCallIdListener(this);

  private CallDetailsAdapterCommon adapter;
  private CallDetailsEntries callDetailsEntries;
  private SupportUiListener<ImmutableSet<String>> checkRttTranscriptAvailabilityListener;
  private CallRecordingDataStore callRecordingDataStore;

  /**
   * Handles the intent that launches {@link OldCallDetailsActivity} or {@link CallDetailsActivity},
   * e.g., extract data from intent extras, start loading data, etc.
   */
  protected abstract void handleIntent(Intent intent);

  /** Creates an adapter for {@link OldCallDetailsActivity} or {@link CallDetailsActivity}. */
  protected abstract CallDetailsAdapterCommon createAdapter(
      CallDetailsEntryViewHolder.CallDetailsEntryListener callDetailsEntryListener,
      CallDetailsHeaderViewHolder.CallDetailsHeaderListener callDetailsHeaderListener,
      CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener,
      CallDetailsFooterViewHolder.DeleteCallDetailsListener deleteCallDetailsListener,
      CallRecordingDataStore callRecordingDataStore);

  /** Returns the phone number of the call details. */
  protected abstract String getNumber();

  @Override
  @CallSuper
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(ThemeComponent.get(this).theme().getApplicationThemeRes());
    setContentView(R.layout.call_details_activity);
    setupInsets(findViewById(R.id.main_layout));
    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.call_details);
    toolbar.setNavigationOnClickListener(v -> finish());
    checkRttTranscriptAvailabilityListener =
        DialerExecutorComponent.get(this)
            .createUiListener(getSupportFragmentManager(), "Query RTT transcript availability");
    callRecordingDataStore = new CallRecordingDataStore();
    handleIntent(getIntent());
    setupRecyclerViewForEntries();
  }

  @Override
  @CallSuper
  protected void onDestroy() {
    super.onDestroy();
    callRecordingDataStore.close();
  }

  @Override
  @CallSuper
  protected void onResume() {
    super.onResume();
    PostCall.promptUserForMessageIfNecessary(this, findViewById(R.id.recycler_view));
  }

  protected void loadRttTranscriptAvailability() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
      builder.add(entry.getCallMappingId());
    }
    checkRttTranscriptAvailabilityListener.listen(
        this,
        RttTranscriptUtil.getAvailableRttTranscriptIds(this, builder.build()),
        this::updateCallDetailsEntriesWithRttTranscriptAvailability,
        throwable -> {
          throw new RuntimeException(throwable);
        });
  }

  private void updateCallDetailsEntriesWithRttTranscriptAvailability(
      ImmutableSet<String> availableTranscripIds) {
    CallDetailsEntries.Builder mutableCallDetailsEntries = CallDetailsEntries.newBuilder();
    for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
      CallDetailsEntry.Builder newEntry = CallDetailsEntry.newBuilder().mergeFrom(entry);
      newEntry.setHasRttTranscript(availableTranscripIds.contains(entry.getCallMappingId()));
      mutableCallDetailsEntries.addEntries(newEntry.build());
    }
    setCallDetailsEntries(mutableCallDetailsEntries.build());
  }

  @Override
  @CallSuper
  protected void onPause() {
    super.onPause();
  }

  @Override
  @CallSuper
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    handleIntent(intent);
    setupRecyclerViewForEntries();
  }

  private void setupRecyclerViewForEntries() {
    adapter =
        createAdapter(
            callDetailsEntryListener,
            callDetailsHeaderListener,
            reportCallIdListener,
            deleteCallDetailsListener,
            callRecordingDataStore);

    RecyclerView recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setAdapter(adapter);
  }

  final CallDetailsAdapterCommon getAdapter() {
    return adapter;
  }

  @MainThread
  protected final void setCallDetailsEntries(CallDetailsEntries entries) {
    Assert.isMainThread();
    this.callDetailsEntries = entries;
    if (adapter != null) {
      adapter.updateCallDetailsEntries(entries);
    }
  }

  protected final CallDetailsEntries getCallDetailsEntries() {
    return callDetailsEntries;
  }

  /** A {@link Worker} that deletes specified entries from the call log. */
  private static final class DeleteCallsWorker implements Worker<CallDetailsEntries, Void> {
    // Use a weak reference to hold the Activity so that there is no memory leak.
    private final WeakReference<Context> contextWeakReference;

    DeleteCallsWorker(Context context) {
      this.contextWeakReference = new WeakReference<>(context);
    }

    @Override
    // Suppress the lint check here as the user will not be able to see call log entries if
    // permission.WRITE_CALL_LOG is not granted.
    @SuppressLint("MissingPermission")
    @RequiresPermission(value = permission.WRITE_CALL_LOG)
    public Void doInBackground(CallDetailsEntries callDetailsEntries) {
      Context context = contextWeakReference.get();
      if (context == null) {
        return null;
      }

      Selection selection =
          Selection.builder()
              .and(Selection.column(CallLog.Calls._ID).in(getCallLogIdList(callDetailsEntries)))
              .build();

      context
          .getContentResolver()
          .delete(Calls.CONTENT_URI, selection.getSelection(), selection.getSelectionArgs());
      context
          .getContentResolver()
          .notifyChange(Calls.CONTENT_URI, null);
      return null;
    }

    private static List<String> getCallLogIdList(CallDetailsEntries callDetailsEntries) {
      Assert.checkArgument(callDetailsEntries.getEntriesCount() > 0);

      List<String> idStrings = new ArrayList<>(callDetailsEntries.getEntriesCount());

      for (CallDetailsEntry entry : callDetailsEntries.getEntriesList()) {
        idStrings.add(String.valueOf(entry.getCallId()));
      }

      return idStrings;
    }
  }

  private static final class CallDetailsEntryListener
      implements CallDetailsEntryViewHolder.CallDetailsEntryListener {
    private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

    CallDetailsEntryListener(CallDetailsActivityCommon activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void showRttTranscript(String transcriptId, String primaryText, PhotoInfo photoInfo) {
      getActivity()
          .startActivity(
              RttTranscriptActivity.getIntent(getActivity(), transcriptId, primaryText, photoInfo));
    }

    private CallDetailsActivityCommon getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }
  }

  private static final class CallDetailsHeaderListener
      implements CallDetailsHeaderViewHolder.CallDetailsHeaderListener {
    private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

    CallDetailsHeaderListener(CallDetailsActivityCommon activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void placeImsVideoCall(String phoneNumber) {
      PreCall.start(
          getActivity(),
          new CallIntentBuilder(phoneNumber, CallInitiationType.Type.CALL_DETAILS)
              .setIsVideoCall(true));
    }

    @Override
    public void placeVoiceCall(String phoneNumber, String postDialDigits) {
      boolean canSupportedAssistedDialing =
          getActivity()
              .getIntent()
              .getExtras()
              .getBoolean(EXTRA_CAN_SUPPORT_ASSISTED_DIALING, false);
      CallIntentBuilder callIntentBuilder =
          new CallIntentBuilder(phoneNumber + postDialDigits, CallInitiationType.Type.CALL_DETAILS);
      if (canSupportedAssistedDialing) {
        callIntentBuilder.setAllowAssistedDial(true);
      }

      PreCall.start(getActivity(), callIntentBuilder);
    }

    private CallDetailsActivityCommon getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }

    @Override
    public void openAssistedDialingSettings(View unused) {
      Intent intent = new Intent(getActivity(), AssistedDialingSettingActivity.class);
      getActivity().startActivity(intent);
    }

    @Override
    public void createAssistedDialerNumberParserTask(
        AssistedDialingNumberParseWorker worker,
        SuccessListener<Integer> successListener,
        FailureListener failureListener) {
      DialerExecutorComponent.get(getActivity().getApplicationContext())
          .dialerExecutorFactory()
          .createUiTaskBuilder(
              getActivity().getSupportFragmentManager(),
              "CallDetailsActivityCommon.createAssistedDialerNumberParserTask",
              new AssistedDialingNumberParseWorker())
          .onSuccess(successListener)
          .onFailure(failureListener)
          .build()
          .executeParallel(getActivity().getNumber());
    }
  }

  static final class AssistedDialingNumberParseWorker implements Worker<String, Integer> {

    @Override
    public Integer doInBackground(@NonNull String phoneNumber) {
      PhoneNumber parsedNumber;
      try {
        parsedNumber = PhoneNumberUtil.getInstance().parse(phoneNumber, null);
      } catch (NumberParseException e) {
        LogUtil.w(
            "AssistedDialingNumberParseWorker.doInBackground",
            "couldn't parse phone number: " + LogUtil.sanitizePii(phoneNumber),
            e);
        return 0;
      }
      return parsedNumber.getCountryCode();
    }
  }

  private static final class DeleteCallDetailsListener
      implements CallDetailsFooterViewHolder.DeleteCallDetailsListener {

    private final WeakReference<CallDetailsActivityCommon> activityWeakReference;

    DeleteCallDetailsListener(CallDetailsActivityCommon activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void delete() {
      CallDetailsActivityCommon activity = getActivity();
      new AlertDialog.Builder(activity)
          .setTitle(R.string.call_details_delete_all_title)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.delete, (dialog, which) -> deleteAll(activity))
          .show();
    }

    private void deleteAll(CallDetailsActivityCommon activity) {
      DialerExecutorComponent.get(activity)
          .dialerExecutorFactory()
          .createNonUiTaskBuilder(new DeleteCallsWorker(activity))
          .onSuccess(
              unused -> {
                Intent data = new Intent();
                data.putExtra(EXTRA_PHONE_NUMBER, activity.getNumber());
                activity.setResult(RESULT_OK, data);
                activity.finish();
              })
          .build()
          .executeSerial(activity.getCallDetailsEntries());
    }

    private CallDetailsActivityCommon getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }
  }

  private static final class ReportCallIdListener
      implements CallDetailsFooterViewHolder.ReportCallIdListener {
    private final WeakReference<AppCompatActivity> activityWeakReference;

    ReportCallIdListener(AppCompatActivity activity) {
      this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    public void reportCallId(String number) {
      ReportDialogFragment.newInstance(number)
          .show(getActivity().getSupportFragmentManager(), null /* tag */);
    }

    @Override
    public boolean canReportCallerId(String number) {
      return getActivity().getIntent().getExtras().getBoolean(EXTRA_CAN_REPORT_CALLER_ID, false);
    }

    private AppCompatActivity getActivity() {
      return Preconditions.checkNotNull(activityWeakReference.get());
    }
  }
}
