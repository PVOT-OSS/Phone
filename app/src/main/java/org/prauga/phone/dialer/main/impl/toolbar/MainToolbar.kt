/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.ViewUtil
import java.util.Optional

/**
 * The main screen's toolbar: a search bar that expands into a search field, with voice search and
 * the options menu. [org.prauga.phone.dialer.main.impl.MainSearchController] drives it.
 */
class MainToolbar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private var listener: SearchBarListener? = null
    private var hasGlobalLayoutListener = false

    private var queryText by mutableStateOf("")
    private var expanded by mutableStateOf(false)
    private var animateChange by mutableStateOf(true)
    private var clearFrequentsVisible by mutableStateOf(false)
    private var menuOpen by mutableStateOf(false)

    private var hintRes by mutableIntStateOf(R.string.dialer_hint_find_contact)

    /** Counts requests to focus the field and show the keyboard; each change asks once. */
    private var focusRequests by mutableIntStateOf(0)

    fun setSearchBarListener(listener: SearchBarListener) {
        this.listener = listener
    }

    /** Slides the toolbar up and off the screen, with [container] under it. */
    fun slideUp(animate: Boolean, container: View) {
        if (hasGlobalLayoutListener) return
        if (height == 0) {
            // Not laid out yet; slide once there is a height to slide by.
            hasGlobalLayoutListener = true
            ViewUtil.doOnGlobalLayout(this) {
                hasGlobalLayoutListener = false
                slideUp(animate, container)
            }
            return
        }
        if (isSlideUp()) {
            LogUtil.e("MainToolbar.slideUp", "Already slide up.")
            return
        }
        slide(-height.toFloat(), animate, container)
    }

    /** Slides the toolbar down and back onto the screen, with [container] under it. */
    fun slideDown(animate: Boolean, container: View) {
        if (translationY == 0f) {
            LogUtil.e("MainToolbar.slideDown", "Already slide down.")
            return
        }
        slide(0f, animate, container)
    }

    private fun slide(to: Float, animate: Boolean, container: View) {
        val duration = if (animate) SLIDE_DURATION else 0L
        for (view in listOf(this, container)) {
            view.animate().translationY(to).setDuration(duration).setInterpolator(SLIDE_INTERPOLATOR).start()
        }
    }

    fun isSlideUp(): Boolean = height != 0 && translationY == -height.toFloat()

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
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
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
            if (!result.isNullOrEmpty()) expand(true, Optional.of(result), true)
        }
    }

    @Composable
    override fun Content() {
        PhoneScreenTheme(fillBackground = false) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.expanded_search_bar_height))
                    .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.XS),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        if (animateChange) {
                            fadeIn(tween(PvotDurations.Short)) togetherWith fadeOut(tween(PvotDurations.Short))
                        } else {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        }
                    },
                    label = "searchBar",
                ) { isExpanded ->
                    if (isExpanded) SearchField() else SearchBar()
                }
            }
            if (menuOpen) OptionsMenu()
        }
    }

    @Composable
    private fun SearchBar() {
        PvotCard(
            modifier = Modifier.fillMaxSize().testTag(TestTags.SEARCH_BAR),
            onClick = { listener?.onSearchBarClicked() },
            shape = PvotShapes.Pill,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = PvotSpacing.XS), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PvotSpacing.M),
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::onVoiceSearch, modifier = Modifier.testTag(TestTags.SEARCH_VOICE)) {
                    Icon(painterResource(R.drawable.ic_microphone), stringResource(R.string.description_start_voice_search))
                }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(TestTags.SEARCH_MENU)) {
                    Icon(painterResource(R.drawable.ic_dots_vertical), stringResource(R.string.action_menu_overflow_description))
                }
            }
        }
    }

    @Composable
    private fun SearchField() {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(focusRequests) {
            if (focusRequests > 0) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::onBack, modifier = Modifier.testTag(TestTags.SEARCH_BACK)) {
                Icon(painterResource(R.drawable.ic_arrow_left), stringResource(R.string.action_menu_back_from_search))
            }
            PvotTextField(
                value = queryText,
                onValueChange = ::updateQuery,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag(TestTags.SEARCH_FIELD),
                placeholder = stringResource(hintRes),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                trailingIcon = if (queryText.isNotEmpty()) {
                    {
                        IconButton(onClick = { updateQuery("") }) {
                            Icon(painterResource(R.drawable.ic_x), stringResource(R.string.description_clear_search))
                        }
                    }
                } else null,
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
                    icon = { Icon(painterResource(R.drawable.ic_trash), contentDescription = null) },
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

    private companion object {
        const val SLIDE_DURATION = 300L
        val SLIDE_INTERPOLATOR = android.view.animation.AccelerateDecelerateInterpolator()
    }
}
