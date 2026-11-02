/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import android.view.View
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.ViewCompat
import com.prauga.pvot.designsystem.effects.ProvidePvotGlassIntensity
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.navigation.PvotNavBar
import com.prauga.pvot.designsystem.navigation.PvotTabItem
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneSnackbarHost
import org.prauga.phone.dialer.ui.PhoneTheme
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.WindowBackdrop

/** Puts the Compose nav bar into the main screen's view layout. */
object MainChrome {

    /**
     * Binds the nav bar, glass over [behind], the view holding the tabs it floats over, with the
     * snackbars of [snackbars] just above it.
     */
    @JvmStatic
    fun bind(
        navBarView: ComposeView,
        behind: View,
        navBar: MainNavBarState,
        snackbars: SnackbarHostState,
        actions: MainNavBarActions,
    ) {
        navBarView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        navBarView.setChromeContent {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneSnackbarHost(snackbars, Modifier.padding(bottom = PvotSpacing.S))
                WindowBackdrop(behind) { MainNavBar(navBar, actions) }
            }
        }
        val tabs = behind.findViewById<View>(R.id.fragment_container)
        (tabs.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = CompactOnScrollBehavior(navBar)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ComposeView.setChromeContent(content: @Composable () -> Unit) {
        setContent {
            MainChromeTheme {
                Box(Modifier.semantics { testTagsAsResourceId = true }) { content() }
            }
        }
    }
}

/**
 * The bar's theme: Phone's, with the palette's secondary blue as the accent like every other
 * screen, at the glass intensity chosen in Appearance.
 */
@Composable
internal fun MainChromeTheme(content: @Composable () -> Unit) {
    val appearance by AppearanceSettings.rememberAppearance()
    PhoneTheme {
        ProvidePvotGlassIntensity(appearance.preferredIntensity, content = content)
    }
}

/**
 * Shrinks the bar while the tabs scroll up and restores it when they scroll back down, as the Pvot
 * catalog does. The tabs' lists pass their scrolls to the coordinator layout holding them.
 */
internal class CompactOnScrollBehavior(private val navBar: MainNavBarState) : CoordinatorLayout.Behavior<View>() {

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean = axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (dy > ScrollThreshold) navBar.isCompact = true
        else if (dy < -ScrollThreshold) navBar.isCompact = false
    }

    private companion object {
        /** Pixels a scroll must move before it changes the bar, so a tap's jitter doesn't. */
        const val ScrollThreshold = 4
    }
}

/** What the selected tab's pill does: dial on the dialpad, search on recents, add on contacts. */
interface MainNavBarActions {

    /** Places a call with the dialpad's number. */
    fun onDial()

    /** Opens search. */
    fun onSearch()

    /** Creates a contact. */
    fun onAddContact()
}

/**
 * The main screen's tabs in the Pvot nav bar, as the Pvot catalog has it, with unread badges on
 * recents and voicemail. The selected tab's pill names its action and tapping it runs it: Dial,
 * Search or Add; voicemail has none. The bar shrinks while a list scrolls up, see
 * [MainNavBarState.isCompact].
 */
@Composable
fun MainNavBar(state: MainNavBarState, actions: MainNavBarActions, modifier: Modifier = Modifier) {
    val tabs = state.tabs
    val selected = tabs.indexOf(state.selectedTab)
    AnimatedVisibility(
        visible = state.isVisible,
        modifier = modifier,
        enter = slideInVertically(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) { it } +
            fadeIn(tween(PvotDurations.Medium)),
        exit = slideOutVertically(tween(PvotDurations.Short)) { it } +
            fadeOut(tween(PvotDurations.Short)),
    ) {
        PvotNavBar(
            selectedTab = selected,
            onTabClick = { index ->
                val tab = tabs[index]
                if (tab == state.selectedTab) runAction(tab, actions) else state.selectTab(tab)
                state.isCompact = false
            },
            tabs = tabs.map { tab -> tabItem(tab, state.notificationCount(tab)) },
            modifier = Modifier.testTag(TestTags.MAIN_NAV_BAR),
            expandedContentDescriptionRes = tabs.getOrNull(selected)?.let(::actionLabel),
            compact = state.isCompact,
        )
    }
}

private fun runAction(@TabIndex tab: Int, actions: MainNavBarActions) {
    when (tab) {
        TabIndex.DIALPAD -> actions.onDial()
        TabIndex.CALL_LOG -> actions.onSearch()
        TabIndex.CONTACTS -> actions.onAddContact()
    }
}

/** What the selected [tab]'s pill reads, its action or, for voicemail, the tab's name. */
@StringRes
private fun actionLabel(@TabIndex tab: Int): Int = when (tab) {
    TabIndex.DIALPAD -> R.string.nav_bar_action_dial
    TabIndex.CALL_LOG -> R.string.nav_bar_action_search
    TabIndex.CONTACTS -> R.string.nav_bar_action_add
    else -> R.string.tab_title_voicemail
}

private fun tabItem(@TabIndex tab: Int, badgeCount: Int): PvotTabItem = when (tab) {
    TabIndex.DIALPAD -> PvotTabItem(
        iconRes = R.drawable.ic_dialpad,
        labelRes = R.string.tab_title_dialpad,
        contentDescriptionRes = R.string.tab_title_dialpad,
        expandedIconRes = R.drawable.ic_phone,
        expandedLabelRes = R.string.nav_bar_action_dial,
    )
    TabIndex.CALL_LOG -> PvotTabItem(
        iconRes = R.drawable.ic_history,
        labelRes = R.string.tab_title_call_history,
        contentDescriptionRes = R.string.tab_title_call_history,
        expandedIconRes = R.drawable.ic_search,
        expandedLabelRes = R.string.nav_bar_action_search,
        badgeCount = badgeCount,
    )
    TabIndex.CONTACTS -> PvotTabItem(
        iconRes = R.drawable.ic_users,
        labelRes = R.string.tab_all_contacts,
        contentDescriptionRes = R.string.tab_all_contacts,
        expandedIconRes = R.drawable.ic_user_plus,
        expandedLabelRes = R.string.nav_bar_action_add,
    )
    else -> PvotTabItem(
        iconRes = R.drawable.ic_call_voicemail,
        labelRes = R.string.tab_title_voicemail,
        contentDescriptionRes = R.string.tab_title_voicemail,
        badgeCount = badgeCount,
    )
}
