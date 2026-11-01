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

package org.prauga.phone.dialer.searchfragment.list;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.database.Cursor;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;
import androidx.compose.ui.platform.ComposeView;

import org.prauga.phone.contacts.common.extensions.PhoneDirectoryExtenderAccessor;
import org.prauga.phone.dialer.R;
import org.prauga.phone.dialer.callintent.CallInitiationType;
import org.prauga.phone.dialer.callintent.CallIntentBuilder;
import org.prauga.phone.dialer.callintent.CallSpecificAppData;
import org.prauga.phone.dialer.common.Assert;
import org.prauga.phone.dialer.common.FragmentUtils;
import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.common.concurrent.ThreadUtil;
import org.prauga.phone.dialer.precall.PreCall;
import org.prauga.phone.dialer.searchfragment.calllog.CallLogSearchCursorLoader;
import org.prauga.phone.dialer.searchfragment.common.RowClickListener;
import org.prauga.phone.dialer.searchfragment.common.SearchCursor;
import org.prauga.phone.dialer.searchfragment.cp2.SearchContactsCursorLoader;
import org.prauga.phone.dialer.searchfragment.directories.DirectoriesCursorLoader;
import org.prauga.phone.dialer.searchfragment.directories.DirectoriesCursorLoader.Directory;
import org.prauga.phone.dialer.searchfragment.directories.DirectoryContactsCursorLoader;
import org.prauga.phone.dialer.searchfragment.nearbyplaces.NearbyPlacesCursorLoader;
import org.prauga.phone.dialer.util.CallUtil;
import org.prauga.phone.dialer.util.PermissionsUtil;
import org.prauga.phone.dialer.util.DialerUtils;
import org.prauga.phone.dialer.util.IntentUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fragment used for searching contacts. */
public final class NewSearchFragment extends Fragment
    implements LoaderManager.LoaderCallbacks<Cursor>, RowClickListener {

  // Since some of our queries can generate network requests, we should delay them until the user
  // stops typing to prevent generating too much network traffic.
  private static final int NETWORK_SEARCH_DELAY_MILLIS = 300;

  private static final String KEY_LOCATION_PROMPT_DISMISSED = "search_location_prompt_dismissed";

  private static final int CONTACTS_LOADER_ID = 0;
  private static final int NEARBY_PLACES_LOADER_ID = 1;

  // ID for the loader that loads info about all directories (local & remote).
  private static final int DIRECTORIES_LOADER_ID = 2;

  private static final int DIRECTORY_CONTACTS_LOADER_ID = 3;

  private static final int CALL_LOG_LOADER_ID = 4;

  private static final String KEY_QUERY = "key_query";
  private static final String KEY_CALL_INITIATION_TYPE = "key_call_initiation_type";

  private SearchResults adapter;
  private String query;
  // Raw query number from dialpad, which may contain special character such as "+". This is used
  // for actions to add contact or send sms.
  private String rawNumber;
  private CallInitiationType.Type callInitiationType = CallInitiationType.Type.UNKNOWN_INITIATION;

  // Information about all local & remote directories (including ID, display name, etc, but not
  // the contacts in them).
  private final List<Directory> directories = new ArrayList<>();
  private final Runnable loaderCp2ContactsRunnable =
      () -> {
        if (getHost() != null) {
          LoaderManager.getInstance(this).restartLoader(CONTACTS_LOADER_ID, null, this);
        }
      };
  private final Runnable loadNearbyPlacesRunnable =
      () -> {
        if (getHost() != null) {
          LoaderManager.getInstance(this).restartLoader(NEARBY_PLACES_LOADER_ID, null, this);
        }
      };
  private final Runnable loadDirectoryContactsRunnable =
      () -> {
        if (getHost() != null) {
          LoaderManager.getInstance(this).restartLoader(DIRECTORY_CONTACTS_LOADER_ID, null, this);
        }
      };

  private final ActivityResultLauncher<String[]> contactPermissionLauncher =
          registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                  grantResults -> {
            if (grantResults.size() >= 1 && grantResults.values().iterator().next()) {
              // Force a refresh of the data since we were missing the permission before this.
              adapter.setNeedsPermission(false);
              initLoaders();
            }
          });

  private final ActivityResultLauncher<String[]> locationPermissionLauncher =
          registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                  grantResults -> {
            if (grantResults.size() >= 1 && grantResults.values().iterator().next()) {
              // Force a refresh of the data since we were missing the permission before this.
              loadNearbyPlacesCursor();
              adapter.showLocationPermissionRequest(false);
            }
          });

  public static NewSearchFragment newInstance() {
    return new NewSearchFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
    ComposeView view = new ComposeView(requireContext());
    adapter = new SearchResults(requireContext());
    adapter.setQuery(query, rawNumber);
    adapter.setSearchActions(getActions());
    showLocationPermission();
    SearchScreenHost.attach(view, adapter, new FragmentSearchActions());

    if (!PermissionsUtil.hasContactsReadPermissions(getContext())) {
      adapter.setNeedsPermission(true);
    } else {
      initLoaders();
    }

    if (savedInstanceState != null) {
      setQuery(
          savedInstanceState.getString(KEY_QUERY),
          CallInitiationType.Type.forNumber(savedInstanceState.getInt(KEY_CALL_INITIATION_TYPE)));
    }
    return view;
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    updateVisibility();
  }

  /**
   * On the dialpad the results show only once a digit is typed; until then the dialpad tab's quick
   * dial shows through, and takes the touches, from under the empty results.
   */
  private void updateVisibility() {
    View view = getView();
    if (view == null) {
      return;
    }
    boolean idleDialpad =
        callInitiationType == CallInitiationType.Type.DIALPAD && TextUtils.isEmpty(query);
    view.setVisibility(idleDialpad ? View.INVISIBLE : View.VISIBLE);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(KEY_CALL_INITIATION_TYPE, callInitiationType.getNumber());
    outState.putString(KEY_QUERY, query);
  }

  private void initLoaders() {
    LoaderManager.getInstance(this).initLoader(CONTACTS_LOADER_ID, null, this);
    LoaderManager.getInstance(this).initLoader(CALL_LOG_LOADER_ID, null, this);
    loadDirectoriesCursor();
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    LogUtil.i("NewSearchFragment.onCreateLoader", "loading cursor: " + id);
    if (id == CONTACTS_LOADER_ID) {
      return new SearchContactsCursorLoader(getContext(), query, isRegularSearch());
    } else if (id == NEARBY_PLACES_LOADER_ID) {
      // Directories represent contact data sources on the device, but since nearby places aren't
      // stored on the device, they don't have a directory ID. We pass the list of all existing IDs
      // so that we can find one that doesn't collide.
      List<Long> directoryIds = new ArrayList<>();
      for (Directory directory : directories) {
        directoryIds.add(directory.getId());
      }
      return new NearbyPlacesCursorLoader(getContext(), query, directoryIds);
    } else if (id == DIRECTORIES_LOADER_ID) {
      return new DirectoriesCursorLoader(getContext());
    } else if (id == DIRECTORY_CONTACTS_LOADER_ID) {
      return new DirectoryContactsCursorLoader(getContext(), query, directories);
    } else if (id == CALL_LOG_LOADER_ID) {
      return new CallLogSearchCursorLoader(getContext());
    } else {
      throw new IllegalStateException("Invalid loader id: " + id);
    }
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    LogUtil.i("NewSearchFragment.onLoadFinished", "Loader finished: " + loader);
    if (cursor != null
        && !(loader instanceof DirectoriesCursorLoader)
        && !(cursor instanceof SearchCursor)) {
      throw Assert.createIllegalStateFailException("Cursors must implement SearchCursor");
    }

    if (loader instanceof SearchContactsCursorLoader) {
      adapter.setContactsCursor((SearchCursor) cursor);

    } else if (loader instanceof CallLogSearchCursorLoader) {
      adapter.setCallLogCursor((SearchCursor) cursor);

    } else if (loader instanceof NearbyPlacesCursorLoader) {
      adapter.setNearbyPlacesCursor((SearchCursor) cursor);

    } else if (loader instanceof DirectoryContactsCursorLoader) {
      adapter.setDirectoryContactsCursor((SearchCursor) cursor);

    } else if (loader instanceof DirectoriesCursorLoader) {
      directories.clear();
      directories.addAll(DirectoriesCursorLoader.toDirectories(cursor));
      loadNearbyPlacesCursor();
      loadDirectoryContactsCursors();

    } else {
      throw new IllegalStateException("Invalid loader: " + loader);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.i("NewSearchFragment.onLoaderReset", "Loader reset: " + loader);
    if (loader instanceof SearchContactsCursorLoader) {
      adapter.setContactsCursor(null);
    } else if (loader instanceof CallLogSearchCursorLoader) {
      adapter.setCallLogCursor(null);
    } else if (loader instanceof NearbyPlacesCursorLoader) {
      adapter.setNearbyPlacesCursor(null);
    } else if (loader instanceof DirectoryContactsCursorLoader) {
      adapter.setDirectoryContactsCursor(null);
    }
  }

  public void setRawNumber(String rawNumber) {
    this.rawNumber = rawNumber;
  }

  public void setQuery(String query, CallInitiationType.Type callInitiationType) {
    this.query = query;
    this.callInitiationType = callInitiationType;
    updateVisibility();
    if (adapter != null) {
      adapter.setQuery(query, rawNumber);
      adapter.setSearchActions(getActions());
      showLocationPermission();
      loadCp2ContactsCursor();
      loadNearbyPlacesCursor();
      loadDirectoryContactsCursors();
    }
  }

  /** Returns true if the location permission was shown. */
  private boolean showLocationPermission() {
    if (adapter == null) {
      return false;
    }

    if (getContext() == null
        || PermissionsUtil.hasLocationPermissions(getContext())
        || !PhoneDirectoryExtenderAccessor.get(getContext()).isEnabled(getContext())
        || hasBeenDismissed()
        || !isRegularSearch()) {
      adapter.showLocationPermissionRequest(false);
      return false;
    }

    adapter.showLocationPermissionRequest(true);
    return true;
  }

  /** Translate the search fragment and resize it to fit on the screen. */
  @Override
  public void onDestroy() {
    super.onDestroy();
    ThreadUtil.getUiThreadHandler().removeCallbacks(loaderCp2ContactsRunnable);
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadNearbyPlacesRunnable);
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadDirectoryContactsRunnable);
  }

  private void requestContactsPermission() {
    String[] deniedPermissions =
        PermissionsUtil.getPermissionsCurrentlyDenied(
            getContext(), PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
    if (deniedPermissions.length > 0) {
      LogUtil.i(
          "NewSearchFragment.onEmptyViewActionButtonClicked",
          "Requesting permissions: " + Arrays.toString(deniedPermissions));
      FragmentUtils.getParentUnsafe(this, SearchFragmentListener.class).requestingPermission();
      contactPermissionLauncher.launch(deniedPermissions);
    }
  }

  /** Loads info about all directories (local & remote). */
  private void loadDirectoriesCursor() {
    LoaderManager.getInstance(this).initLoader(DIRECTORIES_LOADER_ID, null, this);
  }

  /**
   * Loads contacts stored in directories.
   *
   * <p>Should not be called before finishing loading info about all directories (local & remote).
   */
  private void loadDirectoryContactsCursors() {
    // Cancel existing load if one exists.
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadDirectoryContactsRunnable);
    ThreadUtil.getUiThreadHandler()
        .postDelayed(loadDirectoryContactsRunnable, NETWORK_SEARCH_DELAY_MILLIS);
  }

  private void loadCp2ContactsCursor() {
    // Cancel existing load if one exists.
    ThreadUtil.getUiThreadHandler().removeCallbacks(loaderCp2ContactsRunnable);
    ThreadUtil.getUiThreadHandler()
        .postDelayed(loaderCp2ContactsRunnable, NETWORK_SEARCH_DELAY_MILLIS);
  }

  /**
   * Loads nearby places.
   *
   * <p>Should not be called before finishing loading info about all directories (local and remote).
   */
  private void loadNearbyPlacesCursor() {
    // If we're requesting the location permission, don't load nearby places cursor.
    if (showLocationPermission()) {
      return;
    }

    // If the user dismissed the prompt without granting us the permission, don't load the cursor.
    if (getContext() == null || !PermissionsUtil.hasLocationPermissions(getContext())) {
      return;
    }

    // Cancel existing load if one exists.
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadNearbyPlacesRunnable);

    // If nearby places is not enabled, do not try to load them.
    if (!PhoneDirectoryExtenderAccessor.get(getContext()).isEnabled(getContext())) {
      return;
    }
    ThreadUtil.getUiThreadHandler()
        .postDelayed(loadNearbyPlacesRunnable, NETWORK_SEARCH_DELAY_MILLIS);
  }

  private void requestLocationPermission() {
    Assert.checkArgument(
        !PermissionsUtil.hasPermission(getContext(), ACCESS_FINE_LOCATION),
        "attempted to request already granted location permission");
    String[] deniedPermissions =
        PermissionsUtil.getPermissionsCurrentlyDenied(
            getContext(), PermissionsUtil.allLocationGroupPermissionsUsedInDialer);
    FragmentUtils.getParentUnsafe(this, SearchFragmentListener.class).requestingPermission();
    locationPermissionLauncher.launch(deniedPermissions);
  }

  public void dismissLocationPermission() {
    PreferenceManager.getDefaultSharedPreferences(getContext())
        .edit()
        .putBoolean(KEY_LOCATION_PROMPT_DISMISSED, true)
        .apply();
    adapter.showLocationPermissionRequest(false);
  }

  private boolean hasBeenDismissed() {
    return PreferenceManager.getDefaultSharedPreferences(getContext())
        .getBoolean(KEY_LOCATION_PROMPT_DISMISSED, false);
  }

  @Override
  public void onResume() {
    super.onResume();
    LoaderManager.getInstance(this).restartLoader(CONTACTS_LOADER_ID, null, this);
  }

  /**
   * Returns a list of search actions to be shown in the search results.
   *
   * <p>List will be empty if query is 1 or 0 characters or the query isn't from the Dialpad. For
   * the list of supported actions, see {@link SearchActionViewHolder.Action}.
   */
  private List<Integer> getActions() {
    boolean isDialableNumber = PhoneNumberUtils.isGlobalPhoneNumber(query);
    boolean nonDialableQueryInRegularSearch = isRegularSearch() && !isDialableNumber;
    if (TextUtils.isEmpty(query) || query.length() == 1 || nonDialableQueryInRegularSearch) {
      return Collections.emptyList();
    }

    List<Integer> actions = new ArrayList<>();
    if (!isRegularSearch()) {
      actions.add(SearchAction.CREATE_NEW_CONTACT);
      actions.add(SearchAction.ADD_TO_CONTACT);
    }

    if (isRegularSearch() && isDialableNumber) {
      actions.add(SearchAction.MAKE_VOICE_CALL);
    }

    actions.add(SearchAction.SEND_SMS);
    if (CallUtil.isVideoEnabled(getContext())) {
      actions.add(SearchAction.MAKE_VILTE_CALL);
    }

    return actions;
  }

  // Returns true if currently in Regular Search (as opposed to Dialpad Search).
  private boolean isRegularSearch() {
    return callInitiationType == CallInitiationType.Type.REGULAR_SEARCH;
  }

  @Override
  public void placeVoiceCall(String phoneNumber, int ranking) {
    placeCall(phoneNumber, ranking, false);
  }

  @Override
  public void placeVideoCall(String phoneNumber, int ranking) {
    placeCall(phoneNumber, ranking, true);
  }

  private void placeCall(String phoneNumber, int position, boolean isVideoCall) {
    CallSpecificAppData callSpecificAppData =
        CallSpecificAppData.newBuilder()
            .setCallInitiationType(callInitiationType)
            .setAllowAssistedDialing(true)
            .build();
    PreCall.start(
        getContext(),
        new CallIntentBuilder(phoneNumber, callSpecificAppData)
            .setIsVideoCall(isVideoCall)
            .setAllowAssistedDial(true));
    FragmentUtils.getParentUnsafe(this, SearchFragmentListener.class).onCallPlacedFromSearch();
  }

  /** The search results' actions. */
  private final class FragmentSearchActions implements SearchActions {

    @Override
    public void call(String number, int position) {
      placeVoiceCall(number, position);
    }

    @Override
    public void videoCall(String number, int position) {
      placeVideoCall(number, position);
    }

    @Override
    public void addToContact(String number) {
      DialerUtils.startActivityWithErrorToast(
          getContext(),
          IntentUtil.getAddToExistingContactIntent(number),
          R.string.add_contact_not_available);
    }

    @Override
    public void createContact(String number) {
      DialerUtils.startActivityWithErrorToast(getContext(), IntentUtil.getNewContactIntent(number));
    }

    @Override
    public void sendSms(String number) {
      DialerUtils.startActivityWithErrorToast(getContext(), IntentUtil.getSendSmsIntent(number));
    }

    @Override
    public void allowLocation() {
      requestLocationPermission();
    }

    @Override
    public void dismissLocation() {
      dismissLocationPermission();
    }

    @Override
    public void turnOnPermission() {
      requestContactsPermission();
    }

    @Override
    public void listTouched() {
      FragmentUtils.getParentUnsafe(NewSearchFragment.this, SearchFragmentListener.class)
          .onSearchListTouch();
    }
  }

  /** Callback to {@link NewSearchFragment}'s parent to be notified of important events. */
  public interface SearchFragmentListener {

    /** Called when the list view in {@link NewSearchFragment} is clicked. */
    void onSearchListTouch();

    /** Called when a call is placed from the search fragment. */
    void onCallPlacedFromSearch();

    /** Called when a permission is about to be requested. */
    void requestingPermission();
  }
}
