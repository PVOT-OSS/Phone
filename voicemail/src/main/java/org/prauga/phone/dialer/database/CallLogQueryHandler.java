/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.prauga.phone.dialer.database;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;

import org.prauga.phone.contacts.common.database.NoNullCursorAsyncQueryHandler;
import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.phonenumbercache.CallLogQuery;
import org.prauga.phone.dialer.telecom.TelecomUtil;
import org.prauga.phone.dialer.util.PermissionsUtil;
import org.prauga.phone.dialer.voicemailstatus.VoicemailStatusQuery;
import org.prauga.phone.voicemail.VoicemailComponent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Handles asynchronous queries to the call log. */
public class CallLogQueryHandler extends NoNullCursorAsyncQueryHandler {

  /**
   * Call type similar to Calls.INCOMING_TYPE used to specify all types instead of one particular
   * type. Exception: excludes Calls.VOICEMAIL_TYPE.
   */
  public static final int CALL_TYPE_ALL = -1;

  private static final int NUM_LOGS_TO_DISPLAY = 1000;
  /** The token for the query to fetch the old entries from the call log. */
  private static final int QUERY_CALLLOG_TOKEN = 54;

  /** The filter of the newest call log query. */
  private int latestCallTypeFilter = CALL_TYPE_ALL;
  /** The token for the query to mark all missed calls as read after seeing the call log. */
  private static final int UPDATE_MARK_MISSED_CALL_AS_READ_TOKEN = 56;
  /** The token for the query to fetch voicemail status messages. */
  private static final int QUERY_VOICEMAIL_STATUS_TOKEN = 57;
  /** The token for the query to fetch the number of unread voicemails. */
  private static final int QUERY_VOICEMAIL_UNREAD_COUNT_TOKEN = 58;
  /** The token for the query to fetch the number of missed calls. */
  private static final int QUERY_MISSED_CALLS_UNREAD_COUNT_TOKEN = 59;

  private final int logLimit;
  private final WeakReference<Listener> listener;

  private final Context context;

  public CallLogQueryHandler(Context context, ContentResolver contentResolver, Listener listener) {
    this(context, contentResolver, listener, -1);
  }

  public CallLogQueryHandler(
      Context context, ContentResolver contentResolver, Listener listener, int limit) {
    super(contentResolver);
    this.context = context.getApplicationContext();
    this.listener = new WeakReference<>(listener);
    logLimit = limit;
  }

  @Override
  protected Handler createHandler(Looper looper) {
    // Provide our special handler that catches exceptions
    return new CatchingWorkerHandler(looper);
  }

  /**
   * Fetches the list of calls from the call log for a given type. This call ignores the new or old
   * state.
   *
   * <p>It will asynchronously update the content of the list view when the fetch completes.
   */
  public void fetchCalls(int callType, long newerThan) {
    cancelFetch();
    if (PermissionsUtil.hasPhonePermissions(context)) {
      fetchCalls(QUERY_CALLLOG_TOKEN, callType, false /* newOnly */, newerThan);
    } else {
      updateAdapterData(null);
    }
  }

  public void fetchVoicemailStatus() {
    StringBuilder where = new StringBuilder();
    List<String> selectionArgs = new ArrayList<>();

    VoicemailComponent.get(context)
        .getVoicemailClient()
        .appendOmtpVoicemailStatusSelectionClause(context, where, selectionArgs);

    if (TelecomUtil.hasReadWriteVoicemailPermissions(context)) {
      LogUtil.i("CallLogQueryHandler.fetchVoicemailStatus", "fetching voicemail status");
      startQuery(
          QUERY_VOICEMAIL_STATUS_TOKEN,
          null,
          Status.CONTENT_URI,
          VoicemailStatusQuery.getProjection(),
          where.toString(),
          selectionArgs.toArray(new String[0]),
          null);
    } else {
      LogUtil.i(
          "CallLogQueryHandler.fetchVoicemailStatus",
          "fetching voicemail status failed due to permissions");
    }
  }

  public void fetchVoicemailUnreadCount() {
    if (TelecomUtil.hasReadWriteVoicemailPermissions(context)) {
      // Only count voicemails that have not been read and have not been deleted.
      StringBuilder where =
          new StringBuilder(Voicemails.IS_READ + "=0" + " AND " + Voicemails.DELETED + "=0 ");
      List<String> selectionArgs = new ArrayList<>();

      VoicemailComponent.get(context)
          .getVoicemailClient()
          .appendOmtpVoicemailSelectionClause(context, where, selectionArgs);

      startQuery(
          QUERY_VOICEMAIL_UNREAD_COUNT_TOKEN,
          null,
          Voicemails.CONTENT_URI,
          new String[] {Voicemails._ID},
          where.toString(),
          selectionArgs.toArray(new String[0]),
          null);
    }
  }

  /** Fetches the list of calls in the call log. */
  private void fetchCalls(int token, int callType, boolean newOnly, long newerThan) {
    FetchQuery query = buildFetchQuery(context, callType, newOnly, newerThan, logLimit);
    // Tag the query with its filter, so a result for a filter that is no longer shown is dropped.
    latestCallTypeFilter = callType;
    startQuery(
        token,
        callType,
        query.uri,
        CallLogQuery.getProjection(),
        query.selection,
        query.selectionArgs,
        Calls.DEFAULT_SORT_ORDER);
  }

  /** The URI, selection and arguments of a call log query; sort by {@link Calls#DEFAULT_SORT_ORDER}. */
  public static final class FetchQuery {
    public final Uri uri;
    public final String selection;
    public final String[] selectionArgs;

    FetchQuery(Uri uri, String selection, String[] selectionArgs) {
      this.uri = uri;
      this.selection = selection;
      this.selectionArgs = selectionArgs;
    }
  }

  /**
   * Builds the query for calls of {@code callType} ({@link #CALL_TYPE_ALL} for every type but
   * voicemail), newer than {@code newerThan} when it is positive, at most {@code logLimit} rows
   * ({@code -1} for the default limit) and only new calls if {@code newOnly}.
   */
  public static FetchQuery buildFetchQuery(
      Context context, int callType, boolean newOnly, long newerThan, int logLimit) {
    StringBuilder where = new StringBuilder();
    List<String> selectionArgs = new ArrayList<>();

    // Ignore voicemails marked as deleted. Only an app with voicemail access may name that column;
    // any other app gets no voicemails in the call log at all.
    where.append("(1)");
    if (TelecomUtil.hasReadWriteVoicemailPermissions(context)) {
      where.append(" AND (").append(Voicemails.DELETED).append(" = 0)");
    }

    if (newOnly) {
      where.append(" AND (").append(Calls.NEW).append(" = 1)");
    }

    if (callType == Calls.MISSED_TYPE) {
      // Rejected calls count as missed: the user didn't talk to the caller either way.
      where.append(" AND (").append(Calls.TYPE).append(" IN (?, ?))");
      selectionArgs.add(Integer.toString(Calls.MISSED_TYPE));
      selectionArgs.add(Integer.toString(Calls.REJECTED_TYPE));
    } else if (callType > CALL_TYPE_ALL) {
      where.append(" AND (").append(Calls.TYPE).append(" = ?)");
      selectionArgs.add(Integer.toString(callType));
    } else {
      where.append(" AND NOT ");
      where.append("(" + Calls.TYPE + " = " + Calls.VOICEMAIL_TYPE + ")");
    }

    if (newerThan > 0) {
      where.append(" AND (").append(Calls.DATE).append(" > ?)");
      selectionArgs.add(Long.toString(newerThan));
    }

    if (callType == Calls.VOICEMAIL_TYPE) {
      VoicemailComponent.get(context)
          .getVoicemailClient()
          .appendOmtpVoicemailSelectionClause(context, where, selectionArgs);
    } else {
      // Filter out all Duo entries other than video calls
      where
          .append(" AND (")
          .append(Calls.PHONE_ACCOUNT_COMPONENT_NAME)
          .append(" IS NULL OR ")
          .append(Calls.PHONE_ACCOUNT_COMPONENT_NAME)
          .append(" NOT LIKE 'com.google.android.apps.tachyon%' OR ")
          .append(Calls.FEATURES)
          .append(" & ")
          .append(Calls.FEATURES_VIDEO)
          .append(" == ")
          .append(Calls.FEATURES_VIDEO)
          .append(")");
    }

    final int limit = (logLimit == -1) ? NUM_LOGS_TO_DISPLAY : logLimit;
    // Other apps' calls too, such as messaging apps' voice calls, where the call log keeps them.
    Uri uri =
        TelecomUtil.withOtherAppsCalls(TelecomUtil.getCallLogUri(context))
            .buildUpon()
            .appendQueryParameter(Calls.LIMIT_PARAM_KEY, Integer.toString(limit))
            .build();
    return new FetchQuery(uri, where.toString(), selectionArgs.toArray(new String[0]));
  }

  /** Cancel any pending fetch request. */
  private void cancelFetch() {
    cancelOperation(QUERY_CALLLOG_TOKEN);
  }

  /** Updates all missed calls to mark them as read. */
  public void markMissedCallsAsRead() {
    if (!PermissionsUtil.hasPhonePermissions(context)) {
      return;
    }

    ContentValues values = new ContentValues(1);
    values.put(Calls.IS_READ, "1");

    startUpdate(
        UPDATE_MARK_MISSED_CALL_AS_READ_TOKEN,
        null,
        Calls.CONTENT_URI,
        values,
        getUnreadMissedCallsQuery(),
        null);
  }

  /** Fetch all missed calls received since last time the tab was opened. */
  public void fetchMissedCallsUnreadCount() {
    if (!PermissionsUtil.hasPhonePermissions(context)) {
      return;
    }

    startQuery(
        QUERY_MISSED_CALLS_UNREAD_COUNT_TOKEN,
        null,
        Calls.CONTENT_URI,
        new String[] {Calls._ID},
        getUnreadMissedCallsQuery(),
        null,
        null);
  }

  @Override
  protected synchronized void onNotNullableQueryComplete(int token, Object cookie, Cursor cursor) {
    if (cursor == null) {
      return;
    }
    try {
      if (token == QUERY_CALLLOG_TOKEN) {
        if (cookie instanceof Integer && (Integer) cookie != latestCallTypeFilter) {
          LogUtil.i("CallLogQueryHandler.onNotNullableQueryComplete", "dropping a stale result");
          return;
        }
        if (updateAdapterData(cursor)) {
          cursor = null;
        }
      } else if (token == QUERY_VOICEMAIL_STATUS_TOKEN) {
        updateVoicemailStatus(cursor);
      } else if (token == QUERY_VOICEMAIL_UNREAD_COUNT_TOKEN) {
        updateVoicemailUnreadCount(cursor);
      } else if (token == QUERY_MISSED_CALLS_UNREAD_COUNT_TOKEN) {
        updateMissedCallsUnreadCount(cursor);
      } else {
        LogUtil.w(
            "CallLogQueryHandler.onNotNullableQueryComplete",
            "unknown query completed: ignoring: " + token);
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /**
   * Updates the adapter in the call log fragment to show the new cursor data. Returns true if the
   * listener took ownership of the cursor.
   */
  private boolean updateAdapterData(Cursor cursor) {
    final Listener listener = this.listener.get();
    return listener != null && listener.onCallsFetched(cursor);
  }

  /** @return Query string to get all unread missed calls. */
  private String getUnreadMissedCallsQuery() {
    return Calls.IS_READ
        + " = 0 OR "
        + Calls.IS_READ
        + " IS NULL"
        + " AND "
        + Calls.TYPE
        + " = "
        + Calls.MISSED_TYPE;
  }

  private void updateVoicemailStatus(Cursor statusCursor) {
    final Listener listener = this.listener.get();
    if (listener != null) {
      listener.onVoicemailStatusFetched(statusCursor);
    }
  }

  private void updateVoicemailUnreadCount(Cursor statusCursor) {
    final Listener listener = this.listener.get();
    if (listener != null) {
      listener.onVoicemailUnreadCountFetched(statusCursor);
    }
  }

  private void updateMissedCallsUnreadCount(Cursor statusCursor) {
    final Listener listener = this.listener.get();
    if (listener != null) {
      listener.onMissedCallsUnreadCountFetched(statusCursor);
    }
  }

  /** Listener to completion of various queries. */
  public interface Listener {

    /** Called when {@link CallLogQueryHandler#fetchVoicemailStatus()} completes. */
    void onVoicemailStatusFetched(Cursor statusCursor);

    /** Called when {@link CallLogQueryHandler#fetchVoicemailUnreadCount()} completes. */
    void onVoicemailUnreadCountFetched(Cursor cursor);

    /** Called when {@link CallLogQueryHandler#fetchMissedCallsUnreadCount()} completes. */
    void onMissedCallsUnreadCountFetched(Cursor cursor);

    /**
     * Called when {@link CallLogQueryHandler#fetchCalls(int, long)} complete. Returns true if takes
     * ownership of cursor.
     */
    boolean onCallsFetched(Cursor combinedCursor);
  }

  /**
   * Simple handler that wraps background calls to catch {@link SQLiteException}, such as when the
   * disk is full.
   */
  private class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {

    CatchingWorkerHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      try {
        // Perform same query while catching any exceptions
        super.handleMessage(msg);
      } catch (SQLiteDiskIOException | SQLiteFullException | SQLiteDatabaseCorruptException e) {
        LogUtil.e("CallLogQueryHandler.handleMessage", "exception on background worker thread", e);
      } catch (IllegalArgumentException e) {
        LogUtil.e("CallLogQueryHandler.handleMessage", "contactsProvider not present on device", e);
      } catch (SecurityException e) {
        // Shouldn't happen if we are protecting the entry points correctly,
        // but just in case.
        LogUtil.e(
            "CallLogQueryHandler.handleMessage", "no permission to access ContactsProvider.", e);
      }
    }
  }
}
