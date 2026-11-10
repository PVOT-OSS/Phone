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

package org.prauga.phone.dialer.main.impl;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.prauga.phone.contacts.common.dialog.ClearFrequentsDialog;
import org.prauga.phone.dialer.R;
import org.prauga.phone.dialer.app.calllog.CallLogActivity;
import org.prauga.phone.dialer.app.settings.DialerSettingsActivity;
import org.prauga.phone.dialer.callintent.CallInitiationType;
import org.prauga.phone.dialer.common.LogUtil;
import org.prauga.phone.dialer.dialpadview.DialpadFragment;
import org.prauga.phone.dialer.dialpadview.DialpadFragment.DialpadListener;
import org.prauga.phone.dialer.dialpadview.DialpadFragment.OnDialpadQueryChangedListener;
import org.prauga.phone.dialer.helplines.HelplineActivity;
import org.prauga.phone.dialer.main.impl.bottomnav.MainNavBarState;
import org.prauga.phone.dialer.main.impl.bottomnav.TabIndex;
import org.prauga.phone.dialer.main.impl.toolbar.MainMenuItem;
import org.prauga.phone.dialer.main.impl.toolbar.MainToolbar;
import org.prauga.phone.dialer.main.impl.toolbar.SearchBarListener;
import org.prauga.phone.dialer.searchfragment.list.NewSearchFragment;
import org.prauga.phone.dialer.searchfragment.list.NewSearchFragment.SearchFragmentListener;
import org.prauga.phone.dialer.smartdial.util.SmartDialNameMatcher;
import org.prauga.phone.dialer.util.DialerUtils;
import org.prauga.phone.dialer.util.TransactionSafeActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Search controller for handling all the logic related to entering and exiting the search UI.
 *
 * <p>Components modified are:
 *
 * <ul>
 *   <li>Nav bar, hidden in search from the search bar; it stays under the dialpad.
 *   <li>Toolbar, expanded and visible when dialpad is hidden. Otherwise, hidden off screen.
 *   <li>Dialpad, shown by the nav bar's dialpad tab and closed by moving to another tab.
 * </ul>
 *
 * @see #onBackPressed()
 */
public class MainSearchController implements SearchBarListener {

  private static final String KEY_IS_TOOLBAR_EXPANDED = "is_toolbar_expanded";
  private static final String KEY_IS_TOOLBAR_SLIDE_UP = "is_toolbar_slide_up";

  private static final String DIALPAD_FRAGMENT_TAG = "dialpad_fragment_tag";
  private static final String SEARCH_FRAGMENT_TAG = "search_fragment_tag";

  private final TransactionSafeActivity activity;
  private final MainNavBarState bottomNav;
  private final MainToolbar toolbar;

  /** View located underneath the toolbar that needs to animate with it. */

  private final List<OnSearchShowListener> onSearchShowListenerList = new ArrayList<>();

  /**
   * True when an action happens that closes search (like leaving the app or placing a call). We
   * want to wait until onPause is called otherwise the transition will look extremely janky.
   */
  private boolean closeSearchOnPause;

  private boolean callPlacedFromSearch;
  private boolean requestingPermission;

  private DialpadFragment dialpadFragment;
  /** Whether the dialpad was asked to show and hasn't been put away since. */
  private boolean dialpadShowRequested;
  private NewSearchFragment searchFragment;

  private final ActivityResultLauncher<Intent> mVoiceSearchLauncher;

  public MainSearchController(
      TransactionSafeActivity activity,
      MainNavBarState bottomNav,
      MainToolbar toolbar) {
    this.activity = activity;
    this.bottomNav = bottomNav;
    this.toolbar = toolbar;

    dialpadFragment = (DialpadFragment) activity.getSupportFragmentManager()
            .findFragmentByTag(DIALPAD_FRAGMENT_TAG);
    searchFragment = (NewSearchFragment) activity.getSupportFragmentManager()
            .findFragmentByTag(SEARCH_FRAGMENT_TAG);

    mVoiceSearchLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
              onVoiceResults(result.getResultCode(), result.getData());
            });

    // The results end at the keypad's top edge while it shows, instead of running behind it.
    View searchContainer = activity.findViewById(R.id.search_fragment_container);
    View dialpadContainer = activity.findViewById(R.id.dialpad_fragment_container);
    if (searchContainer != null && dialpadContainer != null) {
      View.OnLayoutChangeListener inset =
          (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
              insetSearchAboveKeypad(searchContainer, dialpadContainer);
      searchContainer.addOnLayoutChangeListener(inset);
      dialpadContainer.addOnLayoutChangeListener(inset);
    }
  }

  /**
   * Pads the bottom of [searchContainer] by as much as the open keypad in [dialpadContainer]
   * overlaps it, so the dialpad's matches fit in the space above the keypad.
   */
  private static void insetSearchAboveKeypad(View searchContainer, View dialpadContainer) {
    int inset = 0;
    View keypad =
        dialpadContainer instanceof ViewGroup && ((ViewGroup) dialpadContainer).getChildCount() > 0
            ? ((ViewGroup) dialpadContainer).getChildAt(0)
            : null;
    if (keypad != null && keypad.getVisibility() == View.VISIBLE) {
      int[] search = new int[2];
      int[] dialpad = new int[2];
      searchContainer.getLocationInWindow(search);
      dialpadContainer.getLocationInWindow(dialpad);
      inset = Math.max(0, search[1] + searchContainer.getHeight() - dialpad[1]);
    }
    if (searchContainer.getPaddingBottom() != inset) {
      // Posted: padding requests a layout, which can't happen inside this layout pass.
      int finalInset = inset;
      searchContainer.post(() -> searchContainer.setPadding(0, 0, 0, finalInset));
    }
  }

  /** Should be called if we're showing the dialpad because of a new ACTION_DIAL intent. */
  public void showDialpadFromNewIntent() {
    LogUtil.enterBlock("MainSearchController.showDialpadFromNewIntent");
    if (isDialpadVisible()) {
      // One scenario where this can happen is if the user has the dialpad open when the receive a
      // call and press add call in the in call ui which calls this method.
      LogUtil.i("MainSearchController.showDialpadFromNewIntent", "Dialpad is already visible.");

      // Mark started from new intent in case there is a phone number in the intent
      dialpadFragment.setStartedFromNewIntent(true);
      return;
    }
    showDialpad(/* animate=*/ false, /* fromNewIntent=*/ true);
  }

  /** Shows the dialpad and slides the toolbar off screen. */
  public void showDialpad(boolean animate) {
    LogUtil.enterBlock("MainSearchController.showDialpad");
    showDialpad(animate, false);
  }

  private void showDialpad(boolean animate, boolean fromNewIntent) {
    if (isDialpadVisible()) {
      LogUtil.e("MainSearchController.showDialpad", "Dialpad is already visible.");
      return;
    }

    dialpadShowRequested = true;
    // The toolbar stays collapsed under the dialpad: expanded, it would flash its search field
    // when the dialpad tab is left and the toolbar shows again.
    toolbar.hideForDialpad();

    activity.setTitle(R.string.dialpad_activity_title);

    FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();

    // Show Search
    if (searchFragment == null) {
      searchFragment = NewSearchFragment.newInstance();
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    } else if (!isSearchVisible()) {
      transaction.show(searchFragment);
    }

    // Show Dialpad
    if (dialpadFragment == null) {
      dialpadFragment = new DialpadFragment();
      dialpadFragment.setStartedFromNewIntent(fromNewIntent);
      transaction.add(R.id.dialpad_fragment_container, dialpadFragment, DIALPAD_FRAGMENT_TAG);
      searchFragment.setQuery("", CallInitiationType.Type.DIALPAD);
    } else {
      dialpadFragment.setStartedFromNewIntent(fromNewIntent);
      transaction.show(dialpadFragment);
      // Closing search cleared the search bar, which left the results in its mode, where they
      // cover the quick dial even with no query. On the dialpad they follow its digits.
      onDialpadQueryChanged(dialpadFragment.getQuery());
    }
    // At once, in the same frame as the toolbar hiding above, or the last tab shows without it.
    transaction.commitNow();

    notifyListenersOnSearchOpen();
  }

  /**
   * Hides the dialpad and slides the toolbar back onto the screen.
   *
   * <p>This method intentionally "hides" and does not "remove" the dialpad in order to preserve its
   * state (i.e. we call {@link FragmentTransaction#hide(Fragment)} instead of {@link
   * FragmentTransaction#remove(Fragment)}.
   *
   * @see {@link #closeSearch(boolean)} to "remove" the dialpad.
   */
  private void hideDialpad(boolean animate) {
    LogUtil.enterBlock("MainSearchController.hideDialpad");
    if (dialpadFragment == null) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is null.");
      return;
    }

    if (!dialpadFragment.isAdded()) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is not added.");
      return;
    }

    if (dialpadFragment.isHidden()) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is already hidden.");
      return;
    }

    if (!dialpadFragment.isDialpadSlideUp()) {
      LogUtil.e("MainSearchController.hideDialpad", "Dialpad fragment is already slide down.");
      return;
    }

    dialpadShowRequested = false;
    toolbar.showAfterDialpad();
    toolbar.transferQueryFromDialpad(dialpadFragment.getQuery());
    activity.setTitle(R.string.main_activity_label);

    dialpadFragment.setAnimate(animate);
    if (!animate) {
      // Put away at once, in the same frame as the tab that replaces it.
      dialpadFragment.slideDown(false, null);
      if (activity.isSafeToCommitTransactions()) {
        activity.getSupportFragmentManager().beginTransaction().hide(dialpadFragment).commitNow();
      }
      return;
    }
    dialpadFragment.slideDown(
        animate,
        new AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            if (activity.isSafeToCommitTransactions()
                && !(activity.isFinishing() || activity.isDestroyed())) {
              activity.getSupportFragmentManager()
                      .beginTransaction()
                      .hide(dialpadFragment)
                      .commit();
            }
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
  }

  private void hideBottomNav() {
    bottomNav.setVisible(false);
  }

  private void showBottomNav() {
    bottomNav.setVisible(true);
  }

  /** Should be called when {@link DialpadListener#onDialpadShown()} is called. */
  public void onDialpadShown() {
    LogUtil.enterBlock("MainSearchController.onDialpadShown");
    // The dialpad is part of its tab's page: it is there at once, not slid in.
    dialpadFragment.slideUp(false);
  }

  /** Opens the dialpad for the dialpad tab, unless it is already open or opening. */
  public void showDialpadForTab(boolean animate) {
    // A dial intent may have just asked for the dialpad, its show still pending; opening it again
    // would lose the intent's number.
    if (dialpadFragment != null && (dialpadShowRequested || !dialpadFragment.isHidden())) {
      return;
    }
    showDialpad(animate, false);
  }

  /** Closes the dialpad and its search when the nav bar moves to another tab. */
  public void closeDialpadForTab() {
    if (isSearchVisible()) {
      closeSearch(false);
    }
  }

  /**
   * The nav bar's Dial action: places a call with the dialpad's number, or fills in the last number
   * when there is none; opens the dialpad first if it was put away.
   */
  public void onDialAction() {
    if (isDialpadVisible()) {
      dialpadFragment.onCall();
    } else {
      showDialpad(true);
    }
  }

  /**
   * @see SearchFragmentListener#onSearchListTouch()
   *     <p>There are 4 scenarios we support to provide a nice UX experience:
   *     <ol>
   *       <li>When the dialpad is visible with an empty query, close the search UI.
   *       <li>When the dialpad is visible with a non-empty query, hide the dialpad.
   *       <li>When the regular search UI is visible with an empty query, close the search UI.
   *       <li>When the regular search UI is visible with a non-empty query, hide the keyboard.
   *     </ol>
   */
  public void onSearchListTouch() {
    LogUtil.enterBlock("MainSearchController.onSearchListTouched");
    if (isDialpadVisible()) {
      if (TextUtils.isEmpty(dialpadFragment.getQuery())) {
        closeSearch(true);
      } else {
        // The keypad goes away and the search goes on, in the toolbar's search field.
        toolbar.expand(/* animate=*/ false, Optional.empty(), /* requestFocus */ false);
        hideDialpad(/* animate=*/ true);
        hideBottomNav();
      }
    } else if (isSearchVisible()) {
      if (TextUtils.isEmpty(toolbar.getQuery())) {
        closeSearch(true);
        reopenDialpadOnItsTab();
      } else {
        closeKeyboard();
      }
    }
  }

  /**
   * Should be called when the user presses the back button.
   *
   * @return true if #onBackPressed() handled to action.
   */
  public boolean onBackPressed() {
    if (isDialpadVisible()) {
      // The dialpad is a tab: back clears the number first, then leaves like any other tab.
      if (TextUtils.isEmpty(dialpadFragment.getQuery())) {
        return false;
      }
      LogUtil.i("MainSearchController.onBackPressed", "Dialpad visible with query");
      dialpadFragment.clearDialpad();
      return true;
    } else if (isSearchVisible()) {
      LogUtil.i("MainSearchController.onBackPressed", "Search is visible");
      closeSearch(true);
      reopenDialpadOnItsTab();
      return true;
    } else {
      return false;
    }
  }

  /** Calls {@link #hideDialpad(boolean)}, removes the search fragment and clears the dialpad. */
  private void closeSearch(boolean animate) {
    LogUtil.enterBlock("MainSearchController.closeSearch");
    if (searchFragment == null) {
      LogUtil.e("MainSearchController.closeSearch", "Search fragment is null.");
      return;
    }

    if (!searchFragment.isAdded()) {
      LogUtil.e("MainSearchController.closeSearch", "Search fragment isn't added.");
      return;
    }

    if (searchFragment.isHidden()) {
      LogUtil.e("MainSearchController.closeSearch", "Search fragment is already hidden.");
      return;
    }

    if (isDialpadVisible()) {
      hideDialpad(animate);
    }
    showBottomNav();
    updateStatusBarColor(android.R.attr.statusBarColor);
    toolbar.collapse(animate);
    // At once, in the same frame as the next tab and the toolbar.
    activity.getSupportFragmentManager().beginTransaction().hide(searchFragment).commitNow();

    // Clear the dialpad so the phone number isn't persisted between search sessions.
    if (dialpadFragment != null) {
      // Temporarily disable accessibility when we clear the dialpad, since it should be
      // invisible and should not announce anything.
      dialpadFragment
          .getDigitsWidget()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      dialpadFragment.clearDialpad();
      dialpadFragment
          .getDigitsWidget()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    notifyListenersOnSearchClose();
  }

  @Nullable
  protected DialpadFragment getDialpadFragment() {
    return dialpadFragment;
  }

  private boolean isDialpadVisible() {
    return dialpadFragment != null
        && dialpadFragment.isAdded()
        && !dialpadFragment.isHidden()
        && dialpadFragment.isDialpadSlideUp();
  }

  private boolean isSearchVisible() {
    return searchFragment != null && searchFragment.isAdded() && !searchFragment.isHidden();
  }

  /** Returns true if the search UI is visible. */
  public boolean isInSearch() {
    return isSearchVisible();
  }

  /**
   * Whether the nav bar should hide: in search from the search bar, or in the dialpad's results with
   * the keypad put away. The open dialpad keeps the nav bar under it.
   */
  public boolean hidesNavBar() {
    return isSearchVisible() && (dialpadFragment == null || dialpadFragment.isHidden());
  }

  /** Closes the keyboard if necessary. */
  private void closeKeyboard() {
    if (searchFragment != null && searchFragment.isAdded()) {
      toolbar.hideKeyboard();
    }
  }

  /**
   * Opens search in regular/search bar search mode.
   *
   * <p>Hides the nav bar, expands toolbar and starts the search fragment.
   */
  @Override
  public void onSearchBarClicked() {
    LogUtil.enterBlock("MainSearchController.onSearchBarClicked");
    openSearch(Optional.empty());
  }

  private void openSearch(Optional<String> query) {
    LogUtil.enterBlock("MainSearchController.openSearch");

    toolbar.expand(/* animate=*/ true, query, /* requestFocus */ true);
    toolbar.showKeyboard();
    hideBottomNav();
    updateStatusBarColor(android.R.attr.colorBackgroundFloating);

    FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
    // Show Search
    if (searchFragment == null) {
      searchFragment = NewSearchFragment.newInstance();
      transaction.add(R.id.search_fragment_container, searchFragment, SEARCH_FRAGMENT_TAG);
      transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    } else if (!isSearchVisible()) {
      transaction.show(searchFragment);
    }

    searchFragment.setQuery(query.orElse(""), CallInitiationType.Type.REGULAR_SEARCH);

    if (activity.isSafeToCommitTransactions()) {
      transaction.commit();
    }

    notifyListenersOnSearchOpen();
  }

  @Override
  public void onSearchBackButtonClicked() {
    LogUtil.enterBlock("MainSearchController.onSearchBackButtonClicked");
    closeSearch(true);
    reopenDialpadOnItsTab();
  }

  /**
   * Opens the dialpad again when search closes on the dialpad tab, as after the keypad was put
   * away to scroll the results; otherwise the tab would show without its keypad.
   */
  private void reopenDialpadOnItsTab() {
    if (bottomNav.getSelectedTab() == TabIndex.DIALPAD) {
      showDialpad(/* animate=*/ false, /* fromNewIntent=*/ false);
    }
  }

  @Override
  public void onSearchQueryUpdated(String query) {
    if (searchFragment != null) {
      searchFragment.setQuery(query, CallInitiationType.Type.REGULAR_SEARCH);
    }
  }

  /** @see OnDialpadQueryChangedListener#onDialpadQueryChanged(java.lang.String) */
  public void onDialpadQueryChanged(String query) {
    String normalizedQuery = SmartDialNameMatcher.normalizeNumber(/* context = */ activity, query);
    if (searchFragment != null) {
      searchFragment.setRawNumber(query);
      searchFragment.setQuery(normalizedQuery, CallInitiationType.Type.DIALPAD);
    }
  }

  @Override
  public void onVoiceButtonClicked(VoiceSearchResultCallback voiceSearchResultCallback) {
    try {
      Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      mVoiceSearchLauncher.launch(voiceIntent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(activity, R.string.voice_search_not_available, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onMenuItemClicked(MainMenuItem item) {
    switch (item) {
      case SETTINGS:
        activity.startActivity(new Intent(activity, DialerSettingsActivity.class));
        break;
      case CLEAR_FREQUENTS:
        ClearFrequentsDialog.show(activity.getSupportFragmentManager());
        break;
      case CALL_STATS:
        activity.startActivity(new Intent(activity, CallLogActivity.class));
        break;
      case HELPLINES:
        activity.startActivity(new Intent(activity, HelplineActivity.class));
        break;
    }
  }


  @Override
  public void onActivityPause() {
    LogUtil.enterBlock("MainSearchController.onActivityPause");
    closeKeyboard();

    if (closeSearchOnPause) {
      if (isInSearch() && (callPlacedFromSearch || !isDialpadVisible())) {
        closeSearch(false);
      }
      closeSearchOnPause = false;
      callPlacedFromSearch = false;
    }
  }

  @Override
  public void onUserLeaveHint() {
    if (isInSearch()) {
      // Requesting a permission causes this to be called and we want search to remain open when
      // that happens. Otherwise, close search.
      closeSearchOnPause = !requestingPermission;

      // Always hide the keyboard when the user leaves dialer (including permission requests)
      closeKeyboard();
    }
  }

  @Override
  public void onCallPlacedFromSearch() {
    closeSearchOnPause = true;
    callPlacedFromSearch = true;
  }

  @Override
  public void requestingPermission() {
    LogUtil.enterBlock("MainSearchController.requestingPermission");
    requestingPermission = true;
  }

  public void onVoiceResults(int resultCode, Intent data) {
    if (resultCode == AppCompatActivity.RESULT_OK) {
      ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (matches.size() > 0) {
        LogUtil.i("MainSearchController.onVoiceResults", "voice search - match found");
        openSearch(Optional.of(matches.get(0)));
      } else {
        LogUtil.i("MainSearchController.onVoiceResults", "voice search - nothing heard");
      }
    } else {
      LogUtil.e("MainSearchController.onVoiceResults", "voice search failed");
    }
  }

  public void onSaveInstanceState(Bundle bundle) {
    bundle.putBoolean(KEY_IS_TOOLBAR_EXPANDED, toolbar.isExpanded());
    bundle.putBoolean(KEY_IS_TOOLBAR_SLIDE_UP, toolbar.isHiddenForDialpad());
  }

  public void onRestoreInstanceState(Bundle savedInstanceState) {
    boolean isSlideUp = savedInstanceState.getBoolean(KEY_IS_TOOLBAR_SLIDE_UP, false);
    if (isSlideUp) {
      toolbar.hideForDialpad();
    }
    if (savedInstanceState.getBoolean(KEY_IS_TOOLBAR_EXPANDED, false)) {
      // If the toolbar is slide up, that means the dialpad is showing. Thus we don't want to
      // request focus or we'll break physical/bluetooth keyboards typing.
      toolbar.expand(/* animate */ false, Optional.empty(), /* requestFocus */ !isSlideUp);
    }
  }

  private void updateStatusBarColor(@AttrRes int colorAttribute) {
      int color = DialerUtils.resolveColor(activity, colorAttribute);
      activity.getWindow().setStatusBarColor(color);
  }

  private void notifyListenersOnSearchOpen() {
    for (OnSearchShowListener listener : onSearchShowListenerList) {
      listener.onSearchOpen();
    }
  }

  private void notifyListenersOnSearchClose() {
    for (OnSearchShowListener listener : onSearchShowListenerList) {
      listener.onSearchClose();
    }
  }

  /** Listener for search fragment show states change */
  public interface OnSearchShowListener {
    void onSearchOpen();

    void onSearchClose();
  }
}
