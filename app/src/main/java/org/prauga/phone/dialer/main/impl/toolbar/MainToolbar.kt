/*
 * Copyright (C) 2018 The Android Open Source Project
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

package org.prauga.phone.dialer.main.impl.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.isGone
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.util.Optional
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TabHeader
import org.prauga.phone.dialer.ui.TabHeaderDefaults
import org.prauga.phone.dialer.ui.TestTags

/**
 * The main screen's toolbar: the selected tab's large title with the avatar that opens the app
 * menu, which expands into the search field, with voice search, when search opens.
 * [org.prauga.phone.dialer.main.impl.MainSearchController] drives it.
 */
class MainToolbar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private var listener: SearchBarListener? = null

    private var queryText by mutableStateOf("")
    private var expanded by mutableStateOf(false)
    private var animateChange by mutableStateOf(true)
    private var clearFrequentsVisible by mutableStateOf(false)
    private var menuOpen by mutableStateOf(false)
    private var searchButtonShown by mutableStateOf(false)

    private var hintRes by mutableIntStateOf(R.string.dialer_hint_find_contact)
    private var titleRes by mutableIntStateOf(R.string.main_activity_label)

    /** Counts requests to focus the field and show the keyboard; each change asks once. */
    private var focusRequests by mutableIntStateOf(0)

    /**
     * The requests already acted on. The field is composed again each time search opens; without
     * this an old request would focus it and bring up the keyboard, even over the dialpad.
     */
    private var handledFocusRequests = 0

    fun setSearchBarListener(listener: SearchBarListener) {
        this.listener = listener
    }

    /**
     * Shows a search button in the collapsed header, as on the contacts tab, which opens search.
     */
    fun setSearchVisible(visible: Boolean) {
        searchButtonShown = visible
    }

    /**
     * Hides the toolbar for the dialpad tab, whose page has its own header. At once and without
     * moving anything else: the tab's content stays where it is laid out.
     */
    fun hideForDialpad() {
        translationY = 0f
        visibility = View.GONE
    }

    /** Shows the toolbar again once the dialpad tab is left. */
    fun showAfterDialpad() {
        translationY = 0f
        visibility = View.VISIBLE
    }

    /** Whether the toolbar is hidden for the dialpad tab. */
    fun isHiddenForDialpad(): Boolean = isGone

    /**
     * Expands the search bar into the search field and fills it with [text], if any.
     *
     * @param requestFocus False while the dialpad shows, so a hardware keyboard types there instead
     */
    fun expand(animate: Boolean, text: Optional<String>, requestFocus: Boolean) {
        if (expanded) return
        animateChange = animate
        expanded = true
        text.ifPresent(::updateQuery)
        if (requestFocus) focusRequests++
    }

    /** Collapses the search field back into the search bar and clears it. */
    fun collapse(animate: Boolean) {
        if (!expanded) return
        animateChange = animate
        expanded = false
        hideKeyboard()
        updateQuery("")
    }

    fun isExpanded(): Boolean = expanded

    fun getQuery(): String = queryText

    /** Shows the dialpad's number in the field without searching for it again. */
    fun transferQueryFromDialpad(query: String) {
        queryText = query
    }

    fun hideKeyboard() {
        context
            .getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(windowToken, 0)
    }

    fun showKeyboard() {
        focusRequests++
    }

    fun setHint(@StringRes hint: Int) {
        hintRes = hint
    }

    fun showClearFrequents(show: Boolean) {
        clearFrequentsVisible = show
    }

    /** Shows [title], the selected tab's name, as the large title. */
    fun setTitle(@StringRes title: Int) {
        titleRes = title
    }

    /** Opens the app menu, as the avatar does; for the dialpad tab, which has its own header. */
    fun openMenu() {
        menuOpen = true
    }

    private fun updateQuery(query: String) {
        queryText = query
        listener?.onSearchQueryUpdated(query)
    }

    private fun onBack() {
        if (!expanded) return
        listener?.onSearchBackButtonClicked()
        collapse(true)
    }

    private fun onVoiceSearch() {
        listener?.onVoiceButtonClicked { result ->
            when {
                result.isNullOrEmpty() -> Unit
                expanded -> updateQuery(result)
                else -> expand(true, Optional.of(result), true)
            }
        }
    }

    @Composable
    override fun Content() {
        PhoneScreenTheme(fillBackground = false) {
            Box(
                modifier = Modifier.fillMaxWidth().height(TabHeaderDefaults.Height),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        if (animateChange) {
                            fadeIn(tween(PvotDurations.Short)) togetherWith
                                fadeOut(tween(PvotDurations.Short))
                        } else {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        }
                    },
                    label = "searchBar",
                ) { isExpanded ->
                    if (isExpanded) {
                        SearchField()
                    } else {
                        TabHeader(
                            title = stringResource(titleRes),
                            onOpenMenu = { menuOpen = true },
                            actions = {
                                if (searchButtonShown) {
                                    PvotIconButton(
                                        icon = painterResource(R.drawable.ic_search),
                                        contentDescription =
                                            stringResource(R.string.nav_bar_action_search),
                                        onClick = { listener?.onSearchBarClicked() },
                                        modifier = Modifier.testTag(TestTags.TAB_SEARCH),
                                        colors = PvotIconButtonDefaults.plainColors(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            if (menuOpen) OptionsMenu()
        }
    }

    @Composable
    private fun SearchField() {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(focusRequests) {
            if (focusRequests > handledFocusRequests) {
                handledFocusRequests = focusRequests
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        Row(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = PvotSpacing.S, vertical = PvotSpacing.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::onBack, modifier = Modifier.testTag(TestTags.SEARCH_BACK)) {
                Icon(
                    painterResource(R.drawable.ic_arrow_left),
                    stringResource(R.string.action_menu_back_from_search),
                )
            }
            PvotTextField(
                value = queryText,
                onValueChange = ::updateQuery,
                modifier =
                    Modifier.weight(1f)
                        .focusRequester(focusRequester)
                        .testTag(TestTags.SEARCH_FIELD),
                placeholder = stringResource(hintRes),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                trailingIcon = {
                    if (queryText.isNotEmpty()) {
                        IconButton(onClick = { updateQuery("") }) {
                            Icon(
                                painterResource(R.drawable.ic_x),
                                stringResource(R.string.description_clear_search),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = ::onVoiceSearch,
                            modifier = Modifier.testTag(TestTags.SEARCH_VOICE),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_microphone),
                                stringResource(R.string.description_start_voice_search),
                            )
                        }
                    }
                },
            )
        }
    }

    @Composable
    private fun OptionsMenu() {
        fun pick(item: MainMenuItem) {
            menuOpen = false
            listener?.onMenuItemClicked(item)
        }
        PvotBottomSheet(visible = true, onDismissRequest = { menuOpen = false }) {
            PvotSheetItem(
                title = stringResource(R.string.action_menu_call_history_description),
                icon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
                onClick = { pick(MainMenuItem.CALL_HISTORY) },
            )
            if (clearFrequentsVisible) {
                PvotSheetItem(
                    title = stringResource(R.string.menu_clear_frequents),
                    icon = {
                        Icon(painterResource(R.drawable.ic_trash), contentDescription = null)
                    },
                    onClick = { pick(MainMenuItem.CLEAR_FREQUENTS) },
                )
            }
            PvotSheetItem(
                title = stringResource(R.string.action_menu_helplines),
                icon = { Icon(painterResource(R.drawable.ic_help), contentDescription = null) },
                onClick = { pick(MainMenuItem.HELPLINES) },
            )
            PvotSheetItem(
                title = stringResource(R.string.dialer_settings_label),
                icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                onClick = { pick(MainMenuItem.SETTINGS) },
            )
        }
    }
}
